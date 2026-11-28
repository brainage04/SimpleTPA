#!/usr/bin/env python3
"""Drop the repeated frames check-film.py finds in the moving scenes of a film.

usage: recut-repeats.py FILM.mp4 STAGES.json OUT.mp4 OUT.stages.json [--report-json REPORT.json]

A repeated slot is a frame check-film.py flags in a moving scene: the recorder wrote the previous
game frame again because the game had not presented a new one. The recut removes those frames,
and the audio of the same slots so picture and sound stay in step, and leaves every other
frame alone, including those that gain a chat line, a key highlight or a counter digit and every
frame of a still scene. The stage list is renumbered for the shorter film. The report gives each
stage's frames before and after, the speed its world plays at, and the frames dropped.
"""

import argparse
import importlib.util
import json
import subprocess
import sys
from pathlib import Path

CHECK_FILM = Path(__file__).with_name("check-film.py")
MAX_ENCODES = 5


def load_check_film():
    spec = importlib.util.spec_from_file_location("check_film", CHECK_FILM)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def kept_runs(kept):
    runs = []
    for frame in kept:
        if runs and frame == runs[-1][1] + 1:
            runs[-1][1] = frame
        else:
            runs.append([frame, frame])
    return runs


def encode(video, runs, times, frame_seconds, out_path):
    select = "+".join(f"between(n,{first},{last})" for first, last in runs)
    graph = [f"[0:v]select='{select}',settb=1/{round(1 / frame_seconds)},setpts=N[vout]"]
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


def drop_repeats(check_film, means, blocks):
    """Frames to keep, found by running the checker's flags on the film as it will be cut.

    Dropping a repeated slot changes the scene around its neighbours, so a pair the checker left
    alone can become a repeat in the shorter film. Each round drops what the checker flags in the
    frames still alive, until it flags none. A dropped frame's successor keeps its own pair
    difference, which is the difference from the frame before the dropped one: the two were equal.
    """
    alive = list(range(len(means)))
    rounds = []
    while True:
        repeats, _ = check_film.repeat_flags(
            [means[frame] for frame in alive], [blocks[frame] for frame in alive]
        )
        gone = sum(repeats)
        if not gone:
            return alive, rounds
        rounds.append(gone)
        alive = [frame for frame, repeated in zip(alive, repeats) if not repeated]


def recut(video, stages, out_path):
    check_film = load_check_film()
    times = check_film.frame_times(video)
    if stages[0]["startFrame"] != 0 or stages[-1]["endFrameExclusive"] != len(times):
        raise ValueError("The stage list does not cover every video frame")
    means, blocks = check_film.decoded_differences(video, len(times))
    kept, rounds = drop_repeats(check_film, means, blocks)
    frame_seconds = 1 / check_film.TARGET_FPS
    for _ in range(MAX_ENCODES):
        encode(video, kept_runs(kept), times, frame_seconds, out_path)
        # The encoder re-codes every frame: measure the cut itself and drop what it still repeats.
        cut_means, cut_blocks = check_film.decoded_differences(out_path, len(kept))
        repeats, _ = check_film.repeat_flags(cut_means, cut_blocks)
        if not any(repeats):
            break
        rounds.append(sum(repeats))
        kept = [frame for frame, repeated in zip(kept, repeats) if not repeated]
    else:
        raise ValueError(f"The cut still has repeated frames after {MAX_ENCODES} encodes")
    dropped = sorted(set(range(len(times))) - set(kept))

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
    return new_stages, {"framesBefore": len(times), "framesAfter": len(kept),
                        "framesDropped": len(dropped), "droppedPerRound": rounds, "stages": rows}


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
    return 0


if __name__ == "__main__":
    sys.exit(main())
