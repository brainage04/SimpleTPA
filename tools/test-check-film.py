#!/usr/bin/env python3
"""Mutation test for check-film.py: short repeats, long holds and PTS gaps must fail."""

import json
import subprocess
import tempfile
from pathlib import Path

CHECK = Path(__file__).with_name("check-film.py")
LOOP_START = 30
SHORT_LOOPS = (1, 10, 12)
HOLD_LOOPS = (30, 40)
GAP_AT_FRAME = 40
GAP_SECONDS = 0.0667
MAX_STILL_SECONDS = 1.2


def run(command):
    subprocess.run(command, check=True, capture_output=True)


def frame_count(video):
    return int(subprocess.run([
        "ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
        "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", str(video),
    ], check=True, capture_output=True, text=True).stdout.strip())


def mutant(video, work, name, loop_frames, gap=False):
    filters = f"loop=loop={loop_frames}:size=1:start={LOOP_START}" if loop_frames else ""
    if gap:
        filters += ("," if filters else "")
        filters += f"setpts=PTS+({GAP_SECONDS}/TB)*gte(N\\,{GAP_AT_FRAME})"
    run([
        "ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-t", "2",
        "-i", "testsrc2=size=160x90:rate=30", "-vf", filters,
        "-c:v", "libx264", "-crf", "18", "-pix_fmt", "yuv420p", str(video),
    ])
    stages = work / f"{name}.stages.json"
    stages.write_text(json.dumps([
        {"stage": name, "startFrame": 0, "endFrameExclusive": frame_count(video)}
    ]))
    report = work / f"{name}.report.json"
    result = subprocess.run(
        ["python3", str(CHECK), str(video), str(stages), "--report-json", str(report)],
        capture_output=True, text=True,
    )
    return result.returncode, json.loads(report.read_text())


with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    for loop_frames in SHORT_LOOPS:
        code, report = mutant(work / f"repeat{loop_frames}.mp4", work,
                              f"repeat{loop_frames}", loop_frames)
        stage = report["stages"][0]
        print(f"loop {loop_frames:2d}: exit {code}, repeats {stage['repeatedFrames']}, "
              f"still {stage['longestStillSeconds']:.3f}")
        assert code != 0, f"the {loop_frames}-frame repeat passed"
        assert stage["repeatedFrames"] > 0, f"the {loop_frames}-frame repeat was not detected"

    for loop_frames in HOLD_LOOPS:
        code, report = mutant(work / f"hold{loop_frames}.mp4", work,
                              f"hold{loop_frames}", loop_frames)
        stage = report["stages"][0]
        print(f"hold {loop_frames:2d}: exit {code}, decoded repeats "
              f"{report['repeatedPixelRunFrames']}, exact+decoded {stage['repeatedFrames']}, "
              f"still {stage['longestStillSeconds']:.3f}")
        assert report["repeatedPixelRunFrames"] == 0, (
            "a scripted hold was counted as a short decoded repeat"
        )
        if loop_frames == HOLD_LOOPS[0]:
            assert stage["longestStillSeconds"] <= MAX_STILL_SECONDS
        else:
            assert stage["longestStillSeconds"] > MAX_STILL_SECONDS
            assert code != 0, "a hold over the still bar passed"

    code, report = mutant(work / "gap.mp4", work, "gap", 0, gap=True)
    print(f"gap: exit {code}, failures {report['ptsGapFailures']}")
    assert code != 0, "the PTS gap passed"
    assert GAP_AT_FRAME in report["ptsGapFailures"], "the PTS gap was not detected"
