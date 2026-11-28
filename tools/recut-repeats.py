#!/usr/bin/env python3
"""Drop the frames that repeat the frame before them in a film, and the audio of the same slots.

usage: recut-repeats.py FILM.mp4 STAGES.json OUT.mp4 OUT.stages.json [--report-json REPORT.json]

check-film.py's 160x90 luma score only flags candidates. A candidate is deleted only when a
full-resolution, full-colour comparison of the decoded frame with the previous frame that is kept
shows no change beyond the encoder's noise: few pixels differ by much, and no block of the picture
differs by more than a little, in luma and in both chroma planes. A candidate that fails the
comparison is ambiguous and stays. The tool measures the film it has cut, deletes again, and stops
when no candidate passes. It exits 1 when ambiguous candidates remain, because the cut then still
fails the film check. The input must be a constant 30 fps: the audio is cut by source time and the
video is renumbered at 30 fps, so any other cadence would drift apart.

The stage list is renumbered for the shorter film. The report gives each stage's frames before and
after, the speed its world plays at, the frames dropped with their full-resolution differences, and
the ambiguous frames.
"""

import argparse
import importlib.util
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

CHECK_FILM = Path(__file__).with_name("check-film.py")
MAX_ROUNDS = 12
FRAME_SPACING_TOLERANCE_SECONDS = 0.001
# The full-resolution comparison below was measured on take 12 (its withheld film, 1248 pairs)
# through the same filters. Of the 544 pairs whose 160x90 score is 0.005 or less, the 99th
# percentile is 39 luma pixels more than 32 levels apart (maximum 93), 4 U and 14 V pixels more
# than 16 apart (maxima 6 and 28), and a largest block mean of 4.0 in luma, 1.0 in U and 1.25 in V
# (maxima 16.0, 1.25 and 2.0). The bounds are those 99th percentiles. A change that stays inside
# them, under about 40 pixels or a block mean under 4 levels, cannot be told from encoder noise
# and is not seen.
LUMA_JUMP = 32
CHROMA_JUMP = 16
MAX_LUMA_PIXELS = 40
MAX_CHROMA_PIXELS = 16
MAX_LUMA_BLOCK = 4.0
MAX_CHROMA_BLOCK = 1.25
# Block means come from an area-scaled difference picture of 120x68 blocks, amplified so that the
# 8-bit result resolves a quarter of a level.
BLOCK_GRID_WIDTH = 120
BLOCK_GRID_HEIGHT = 68
BLOCK_GAIN = 4


def load_check_film():
    spec = importlib.util.spec_from_file_location("check_film", CHECK_FILM)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def probe(video, entries):
    output = subprocess.run([
        "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
        f"stream={entries}", "-of", "csv=p=0", str(video),
    ], check=True, capture_output=True, text=True).stdout.strip()
    return output.split(",")


def require_constant_fps(check_film, video, times):
    rate = probe(video, "r_frame_rate")[0]
    fps = round(check_film.TARGET_FPS)
    if rate != f"{fps}/1":
        raise ValueError(f"The film is {rate}, not {fps} fps: the recut handles only {fps} fps")
    spacing = 1 / check_film.TARGET_FPS
    if any(abs(later - earlier - spacing) > FRAME_SPACING_TOLERANCE_SECONDS
           for earlier, later in zip(times, times[1:])):
        raise ValueError(f"The frame spacing is not a constant {fps} fps")


def encode(check_film, video, kept, times, out_path):
    runs = check_film.frame_runs(kept)
    frame_seconds = 1 / check_film.TARGET_FPS
    select = check_film.select_expression(kept)
    fps = round(check_film.TARGET_FPS)
    graph = [f"[0:v]select='{select}',settb=1/{fps},setpts=N[vout]"]
    pieces = []
    for index, (first, last) in enumerate(runs):
        graph.append(
            f"[0:a]atrim=start={times[first]:.6f}:end={times[last] + frame_seconds:.6f},"
            f"asetpts=PTS-STARTPTS[a{index}]"
        )
        pieces.append(f"[a{index}]")
    graph.append("".join(pieces) + f"concat=n={len(pieces)}:v=0:a=1[aout]")
    subprocess.run([
        "ffmpeg", "-v", "error", "-y", "-i", str(video), "-filter_complex", ";".join(graph),
        "-map", "[vout]", "-map", "[aout]", "-fps_mode", "passthrough",
        "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k", str(out_path),
    ], check=True)


def read_signalstats(path):
    frames = path.read_text().split("frame:")[1:]
    return [
        {m.group(1): float(m.group(2))
         for m in re.finditer(r"lavfi\.signalstats\.(\w+)=([\d.]+)", frame)}
        for frame in frames
    ]


def pair_statistics(check_film, video, kept, width, height):
    """Full-resolution changes between each kept frame and the kept frame before it.

    Entry p describes the pair (kept[p - 1], kept[p]); entry 0 is empty.
    """
    luma_area = width * height
    chroma_area = (width // 2) * (height // 2)
    amplified = f"clip(val*{BLOCK_GAIN},0,255)"
    with tempfile.TemporaryDirectory() as folder:
        counts_path = Path(folder) / "counts.txt"
        blocks_path = Path(folder) / "blocks.txt"
        graph = (
            f"[0:v]select='{check_film.select_expression(kept)}',tblend=all_mode=difference,"
            "split=2[counted][blocked];"
            f"[counted]lutyuv=y='if(gt(val,{LUMA_JUMP}),255,0)':u='if(gt(val,{CHROMA_JUMP}),255,0)'"
            f":v='if(gt(val,{CHROMA_JUMP}),255,0)',signalstats,"
            f"metadata=mode=print:file={counts_path}[counts];"
            f"[blocked]lutyuv=y='{amplified}':u='{amplified}':v='{amplified}',"
            f"scale={BLOCK_GRID_WIDTH}:{BLOCK_GRID_HEIGHT}:flags=area,signalstats,"
            f"metadata=mode=print:file={blocks_path}[blocks]"
        )
        subprocess.run([
            "ffmpeg", "-v", "error", "-i", str(video), "-an", "-filter_complex", graph,
            "-map", "[counts]", "-fps_mode", "passthrough", "-f", "null", "-",
            "-map", "[blocks]", "-fps_mode", "passthrough", "-f", "null", "-",
        ], check=True)
        counts = read_signalstats(counts_path)
        blocks = read_signalstats(blocks_path)
    if len(counts) != len(kept) - 1 or len(blocks) != len(kept) - 1:
        raise ValueError(f"Measured {len(counts)} pairs for {len(kept)} frames")
    statistics = [{}]
    for count, block in zip(counts, blocks):
        statistics.append({
            "lumaPixels": round(count["YAVG"] / 255 * luma_area),
            "uPixels": round(count["UAVG"] / 255 * chroma_area),
            "vPixels": round(count["VAVG"] / 255 * chroma_area),
            "lumaBlock": block["YMAX"] / BLOCK_GAIN,
            "uBlock": block["UMAX"] / BLOCK_GAIN,
            "vBlock": block["VMAX"] / BLOCK_GAIN,
        })
    return statistics


def unchanged(pair):
    return (
        pair["lumaPixels"] <= MAX_LUMA_PIXELS
        and max(pair["uPixels"], pair["vPixels"]) <= MAX_CHROMA_PIXELS
        and pair["lumaBlock"] <= MAX_LUMA_BLOCK
        and max(pair["uBlock"], pair["vBlock"]) <= MAX_CHROMA_BLOCK
    )


def cut_repeats(check_film, video, times, width, height, out_path):
    """Kept frame indexes, the full-resolution record of each drop, the rounds and the ambiguous."""
    kept = list(range(len(times)))
    current = video
    drops = []
    rounds = []
    for _ in range(MAX_ROUNDS):
        means, blocks = check_film.decoded_differences(current, len(kept))
        flagged, _ = check_film.repeat_flags(means, blocks)
        candidates = [position for position, flag in enumerate(flagged) if flag]
        statistics = pair_statistics(check_film, video, kept, width, height) if candidates else []
        # Delete a candidate only if the frame before it survives this round, so that the pair
        # measured is the pair that remains.
        deleted = set()
        for position in candidates:
            if unchanged(statistics[position]) and position - 1 not in deleted:
                deleted.add(position)
        if not deleted:
            return kept, drops, rounds, [kept[position] for position in candidates]
        rounds.append(len(deleted))
        drops.extend(
            {"frame": kept[position], **statistics[position]} for position in sorted(deleted)
        )
        kept = [frame for position, frame in enumerate(kept) if position not in deleted]
        encode(check_film, video, kept, times, out_path)
        current = out_path
    raise ValueError(f"The cut still loses frames after {MAX_ROUNDS} rounds")


def recut(video, stages, out_path):
    check_film = load_check_film()
    times = check_film.frame_times(video)
    require_constant_fps(check_film, video, times)
    if stages[0]["startFrame"] != 0 or stages[-1]["endFrameExclusive"] != len(times):
        raise ValueError("The stage list does not cover every video frame")
    width, height = (int(value) for value in probe(video, "width,height"))
    kept, drops, rounds, ambiguous = cut_repeats(check_film, video, times, width, height, out_path)
    if not drops:
        encode(check_film, video, kept, times, out_path)
    dropped = [drop["frame"] for drop in drops]

    new_stages = []
    rows = []
    start = 0
    for stage in stages:
        first, end = stage["startFrame"], stage["endFrameExclusive"]
        frames = sum(1 for frame in kept if first <= frame < end)
        if frames == 0:
            raise ValueError(f"Stage {stage['stage']} has no frame left")
        new_stages.append({"stage": stage["stage"], "startFrame": start,
                           "endFrameExclusive": start + frames})
        rows.append({
            "stage": stage["stage"],
            "framesBefore": end - first,
            "framesAfter": frames,
            "framesDropped": end - first - frames,
            "worldSpeed": (end - first) / frames,
            "droppedFrames": [frame for frame in dropped if first <= frame < end],
        })
        start += frames
    full_resolution = [{
        "frame": drop["frame"],
        "lumaBlock": drop["lumaBlock"],
        "chromaBlock": max(drop["uBlock"], drop["vBlock"]),
        "lumaPixels": drop["lumaPixels"],
        "chromaPixels": max(drop["uPixels"], drop["vPixels"]),
    } for drop in drops]
    return new_stages, {
        "framesBefore": len(times), "framesAfter": len(kept), "framesDropped": len(dropped),
        "droppedPerRound": rounds, "droppedFullResolution": full_resolution,
        "ambiguousFrames": ambiguous, "stages": rows,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("stages", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("out_stages", type=Path)
    parser.add_argument("--report-json", type=Path)
    args = parser.parse_args()
    try:
        new_stages, report = recut(args.video, json.loads(args.stages.read_text()), args.out)
    except (ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"recut-repeats: {error}\n")
    args.out_stages.write_text(json.dumps(new_stages, indent=2) + "\n")
    if args.report_json is not None:
        args.report_json.write_text(json.dumps(report, indent=2) + "\n")
    print(f"wrote {args.out}: {report['framesBefore']} -> {report['framesAfter']} frames, "
          f"{report['framesDropped']} dropped (per round {report['droppedPerRound']})")
    for row in report["stages"]:
        print(f"{row['stage']}: {row['framesBefore']} -> {row['framesAfter']} "
              f"(-{row['framesDropped']}), world speed {row['worldSpeed']:.3f}x")
    if report["ambiguousFrames"]:
        print(f"recut-repeats: {len(report['ambiguousFrames'])} candidates are ambiguous and stay: "
              f"{report['ambiguousFrames']}; the cut still fails the film check", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
