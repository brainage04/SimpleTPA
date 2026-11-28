#!/usr/bin/env python3
"""Check a filmed clip's frame rate, repeated frames and longest near-still per stage.

All three checks read the encoded film. A frame whose decoded picture, chroma included, is
bit-identical to the one before is an exact repeat and fails its stage wherever it is: in motion,
in a still scene or in a hold. A pair of neighbouring frames whose 160x90 luma differs by no more
than REPEAT_MAD (or REPEAT_LEVEL_FRACTION of the scene's median pair difference, if more)
overall and by no more than REPEAT_BLOCK_MAD in every 10x10-pixel block is a candidate repeat, so
a frame that gains a chat line, a key highlight or a counter digit is not one. The scene is the
pairs within MOTION_WINDOW_PAIRS on either side. Where its median pair difference is at least
MOTION_LEVEL_MAD the scene moves and the candidate fails the stage; below it the scene is still
and the pair is only counted in stillSceneNearIdenticalFrames. A run of more than
SHORT_REPEAT_RUN_FRAMES such pairs is a scripted hold, and the still bar judges it. The still bar
combines ffmpeg's freezedetect with decoded low-difference runs. The 160x90 score only flags
candidates: recut-repeats.py deletes a frame only when a full-resolution comparison agrees.
"""

import argparse
import csv
import json
import re
import statistics
import subprocess
import sys
from pathlib import Path

MIN_FPS = 29.7
MAX_STILL_SECONDS = 1.2
TARGET_FPS = 30.0
MAX_PTS_GAP_FRAMES = 1.5
FREEZE_NOISE = 0.001
FREEZE_MIN_DURATION_SECONDS = 0.5
REPEAT_PIXEL_WIDTH = 160
REPEAT_PIXEL_HEIGHT = 90
REPEAT_BLOCK_PIXELS = 10
# Most pairs between identical game frames in take 12 differ by 0.000-0.020 over the frame (codec
# noise; about one pair in five reaches 0.08-0.1), and its slowest moving scene steps by 0.02+.
REPEAT_MAD = 0.02
# Take 12's resetfalloff stage has one pair at 0.042 in a stage whose median pair is 2.0, and no
# pair between 0.042 and 0.22: a repeated slot that the encoder coded with extra noise.
REPEAT_LEVEL_FRACTION = 0.1
# The quietest repeated slots of take 12's moving stages move their busiest block by 0.01-0.19; a
# 3x3-pixel change of 16 levels moves it by 1.0.
REPEAT_BLOCK_MAD = 0.5
# Half a second of pairs on either side judges whether the scene around a repeat moves. Take 12's
# still stages have a median pair of 0.010 or less and its moving stages 0.04 or more.
MOTION_WINDOW_PAIRS = 15
MOTION_LEVEL_MAD = 0.03
SHORT_REPEAT_RUN_FRAMES = 12
STILL_MAD = 0.3


def run(command):
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def frame_times(video):
    output = run([
        "ffprobe",
        "-v",
        "error",
        "-select_streams",
        "v:0",
        "-show_frames",
        "-show_entries",
        "frame=best_effort_timestamp_time",
        "-of",
        "json",
        str(video),
    ])
    return [float(frame["best_effort_timestamp_time"]) for frame in json.loads(output)["frames"]]


def frame_hashes(video):
    output = run([
        "ffmpeg", "-v", "error", "-i", str(video), "-map", "0:v:0", "-f", "framemd5", "-",
    ])
    return [
        line.rsplit(",", 1)[-1].strip()
        for line in output.splitlines()
        if line and not line.startswith("#")
    ]


def read_exact(stream, size):
    chunk = b""
    while len(chunk) < size:
        part = stream.read(size - len(chunk))
        if not part:
            break
        chunk += part
    return chunk


def frame_runs(frames):
    """The [first, last] runs of consecutive frame indexes in an ascending list."""
    runs = []
    for frame in frames:
        if runs and frame == runs[-1][1] + 1:
            runs[-1][1] = frame
        else:
            runs.append([frame, frame])
    return runs


def select_expression(frames):
    """An ffmpeg select expression that keeps the frames with these indexes, in order."""
    return "+".join(f"between(n,{first},{last})" for first, last in frame_runs(frames))


def decoded_differences(video, count, frames=None):
    """Mean and largest block mean of each frame's absolute luma difference from the one before.

    With frames, the film is first cut to those frame indexes and count is how many that leaves.
    """
    size = REPEAT_PIXEL_WIDTH * REPEAT_PIXEL_HEIGHT
    cut = f"select='{select_expression(frames)}'," if frames is not None else ""
    proc = subprocess.Popen([
        "ffmpeg", "-v", "error", "-i", str(video), "-map", "0:v:0",
        "-vf", f"{cut}scale={REPEAT_PIXEL_WIDTH}:{REPEAT_PIXEL_HEIGHT},format=gray",
        "-fps_mode", "passthrough", "-an", "-f", "rawvideo", "-pix_fmt", "gray", "-",
    ], stdout=subprocess.PIPE)
    means = [float("inf")] * count
    blocks = [float("inf")] * count
    previous = None
    index = 0
    while index < count:
        frame = read_exact(proc.stdout, size)
        if len(frame) < size:
            break
        if previous is not None:
            difference = [abs(a - b) for a, b in zip(frame, previous)]
            means[index] = sum(difference) / size
            blocks[index] = largest_block_mean(difference)
        previous = frame
        index += 1
    proc.stdout.close()
    proc.wait()
    if index != count:
        raise ValueError(f"Decoded {index} pixel frames but {count} timestamps")
    return means, blocks


def largest_block_mean(difference):
    block_area = REPEAT_BLOCK_PIXELS * REPEAT_BLOCK_PIXELS
    largest = 0.0
    for top in range(0, REPEAT_PIXEL_HEIGHT, REPEAT_BLOCK_PIXELS):
        for left in range(0, REPEAT_PIXEL_WIDTH, REPEAT_BLOCK_PIXELS):
            total = 0
            for row in range(top, top + REPEAT_BLOCK_PIXELS):
                start = row * REPEAT_PIXEL_WIDTH + left
                total += sum(difference[start:start + REPEAT_BLOCK_PIXELS])
            largest = max(largest, total / block_area)
    return largest


def flag_runs(flags):
    runs = []
    index = 1
    while index < len(flags):
        if flags[index]:
            first = index
            while index + 1 < len(flags) and flags[index + 1]:
                index += 1
            runs.append((first, index))
        index += 1
    return runs


def repeat_flags(means, blocks):
    """Flags for repeats in moving scenes and for near-identical pairs in still scenes."""
    count = len(means)
    levels = [
        statistics.median(means[max(1, pair - MOTION_WINDOW_PAIRS):pair + MOTION_WINDOW_PAIRS + 1])
        if pair else 0.0
        for pair in range(count)
    ]
    same_slot = [
        mean <= max(REPEAT_MAD, REPEAT_LEVEL_FRACTION * level) and block <= REPEAT_BLOCK_MAD
        for mean, block, level in zip(means, blocks, levels)
    ]
    repeats = [False] * count
    still = [False] * count
    for first, last in flag_runs(same_slot):
        if last - first + 1 > SHORT_REPEAT_RUN_FRAMES:
            continue
        for frame in range(first, last + 1):
            if levels[frame] >= MOTION_LEVEL_MAD:
                repeats[frame] = True
            else:
                still[frame] = True
    return repeats, still


def freeze_intervals(video, duration):
    result = subprocess.run([
        "ffmpeg",
        "-hide_banner",
        "-i",
        str(video),
        "-vf",
        f"freezedetect=n={FREEZE_NOISE}:d={FREEZE_MIN_DURATION_SECONDS}",
        "-an",
        "-f",
        "null",
        "-",
    ], check=True, capture_output=True, text=True)
    intervals = []
    start = None
    for line in result.stderr.splitlines():
        match = re.search(r"freeze_start: ([\d.]+)", line)
        if match:
            start = float(match.group(1))
        match = re.search(r"freeze_end: ([\d.]+)", line)
        if match and start is not None:
            intervals.append((start, float(match.group(1))))
            start = None
    if start is not None:
        intervals.append((start, duration))
    return intervals


def stage_for_frame(stages, frame):
    for stage in stages:
        if stage["startFrame"] <= frame < stage["endFrameExclusive"]:
            return stage["stage"]
    raise ValueError(f"Frame {frame} is outside the stage list")


def check(video, stages_path, csv_path=None, json_path=None):
    stages = json.loads(stages_path.read_text())
    times = frame_times(video)
    hashes = frame_hashes(video)
    if len(times) != len(hashes):
        raise ValueError(f"Decoded {len(times)} timestamps but {len(hashes)} frame hashes")
    if not times:
        raise ValueError("The video has no frames")
    if stages[0]["startFrame"] != 0 or stages[-1]["endFrameExclusive"] != len(times):
        raise ValueError("The stage list does not cover every video frame")
    for previous, current in zip(stages, stages[1:]):
        if previous["endFrameExclusive"] != current["startFrame"]:
            raise ValueError("The stage list has a gap or overlap")
    duration = times[-1] - times[0]
    freezes = freeze_intervals(video, duration)
    means, blocks = decoded_differences(video, len(times))
    candidate_repeats, still_scene = repeat_flags(means, blocks)
    exact_repeats = [False] + [current == previous for previous, current in zip(hashes, hashes[1:])]
    repeated = [exact or candidate for exact, candidate in zip(exact_repeats, candidate_repeats)]
    still_runs = flag_runs([mean <= STILL_MAD for mean in means])
    gaps = [current - previous for previous, current in zip(times, times[1:])]
    bad_gaps = [
        frame for frame, gap in enumerate(gaps, start=1)
        if gap > MAX_PTS_GAP_FRAMES / TARGET_FPS
    ]
    near_still = [any(start <= time < end for start, end in freezes) for time in times]
    stage_names = [stage_for_frame(stages, frame) for frame in range(len(times))]

    summaries = []
    for stage in stages:
        start = stage["startFrame"]
        end = stage["endFrameExclusive"]
        stage_times = times[start:end]
        span = stage_times[-1] - stage_times[0]
        fps = (end - start - 1) / span if span > 0 else 0.0
        freeze_still = max(
            (
                min(freeze_end, stage_times[-1]) - max(freeze_start, stage_times[0])
                for freeze_start, freeze_end in freezes
            ),
            default=0.0,
        )
        pixel_still = max(
            (
                times[min(last, end - 1)] - times[max(first - 1, start)]
                for first, last in still_runs
                if min(last, end - 1) > max(first - 1, start)
            ),
            default=0.0,
        )
        longest_still = max(0.0, freeze_still, pixel_still)
        repeated_frames = sum(repeated[start:end])
        still_scene_frames = sum(still_scene[start:end])
        passed = fps >= MIN_FPS and repeated_frames == 0 and longest_still <= MAX_STILL_SECONDS
        summaries.append({
            "stage": stage["stage"],
            "startFrame": start,
            "endFrameExclusive": end,
            "frames": end - start,
            "durationSeconds": span,
            "fps": fps,
            "repeatedFrames": repeated_frames,
            "exactRepeatedFrames": sum(exact_repeats[start:end]),
            "stillSceneNearIdenticalFrames": still_scene_frames,
            "longestStillSeconds": longest_still,
            "passed": passed,
        })

    report = {
        "video": str(video.resolve()),
        "frames": len(times),
        "durationSeconds": duration,
        "minimumFps": MIN_FPS,
        "maximumStillSeconds": MAX_STILL_SECONDS,
        "freezeNoise": FREEZE_NOISE,
        "maximumPtsGapSeconds": max(gaps, default=0.0),
        "ptsGapFailures": bad_gaps,
        "repeatedPixelRunFrames": sum(candidate_repeats),
        "exactRepeatedFrames": sum(exact_repeats),
        "stillSceneNearIdenticalFrames": sum(still_scene),
        "shortRepeatRunLimitFrames": SHORT_REPEAT_RUN_FRAMES,
        "stages": summaries,
        "passed": all(stage["passed"] for stage in summaries) and not bad_gaps,
    }
    if csv_path is not None:
        with csv_path.open("w", newline="") as output:
            writer = csv.writer(output)
            writer.writerow(["frame", "pts_seconds", "stage", "repeated", "near_still",
                             "pts_gap_seconds"])
            for frame, time in enumerate(times):
                writer.writerow([
                    frame,
                    f"{time:.6f}",
                    stage_names[frame],
                    str(repeated[frame]).lower(),
                    str(near_still[frame]).lower(),
                    f"{gaps[frame - 1]:.6f}" if frame else "0.000000",
                ])
    if json_path is not None:
        json_path.write_text(json.dumps(report, indent=2) + "\n")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("stages", type=Path)
    parser.add_argument("--frames-csv", type=Path)
    parser.add_argument("--report-json", type=Path)
    args = parser.parse_args()
    try:
        report = check(args.video, args.stages, args.frames_csv, args.report_json)
    except (ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"check-film: {error}\n")
    print(json.dumps(report, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
