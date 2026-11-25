#!/usr/bin/env python3
"""Check a filmed clip's frame rate, repeated frames and longest near-still per stage."""

import argparse
import csv
import json
import re
import subprocess
import sys
from pathlib import Path

MIN_FPS = 29.7
MAX_STILL_SECONDS = 1.2
FREEZE_NOISE = 0.001
FREEZE_MIN_DURATION_SECONDS = 0.5


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
        "ffmpeg",
        "-v",
        "error",
        "-i",
        str(video),
        "-map",
        "0:v:0",
        "-f",
        "framemd5",
        "-",
    ])
    return [
        line.rsplit(",", 1)[-1].strip()
        for line in output.splitlines()
        if line and not line.startswith("#")
    ]


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
    repeated = [False] + [current == previous for previous, current in zip(hashes, hashes[1:])]
    near_still = [any(start <= time < end for start, end in freezes) for time in times]
    stage_names = [stage_for_frame(stages, frame) for frame in range(len(times))]

    summaries = []

    for stage in stages:
        start = stage["startFrame"]
        end = stage["endFrameExclusive"]
        stage_times = times[start:end]
        span = stage_times[-1] - stage_times[0]
        fps = (end - start - 1) / span if span > 0 else 0.0
        longest_still = max(
            (
                min(freeze_end, stage_times[-1]) - max(freeze_start, stage_times[0])
                for freeze_start, freeze_end in freezes
            ),
            default=0.0,
        )
        repeated_frames = sum(repeated[start:end])
        passed = fps >= MIN_FPS and repeated_frames == 0 and longest_still <= MAX_STILL_SECONDS
        summaries.append({
            "stage": stage["stage"],
            "startFrame": start,
            "endFrameExclusive": end,
            "frames": end - start,
            "durationSeconds": span,
            "fps": fps,
            "repeatedFrames": repeated_frames,
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
        "stages": summaries,
        "passed": all(stage["passed"] for stage in summaries),
    }
    if csv_path is not None:
        with csv_path.open("w", newline="") as output:
            writer = csv.writer(output)
            writer.writerow(["frame", "pts_seconds", "stage", "repeated", "near_still"])
            for frame, time in enumerate(times):
                writer.writerow([
                    frame,
                    f"{time:.6f}",
                    stage_names[frame],
                    str(repeated[frame]).lower(),
                    str(near_still[frame]).lower(),
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
