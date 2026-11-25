#!/usr/bin/env python3
"""Mutation test: check-film.py must fail a looped frame run and a long PTS gap."""

import json
import subprocess
import tempfile
from pathlib import Path

CHECK = Path(__file__).with_name("check-film.py")
LOOP_FRAMES = 39
TOTAL_FRAMES = 99
GAP_AT_FRAME = 82
GAP_SECONDS = 0.0667


def run(command):
    subprocess.run(command, check=True, capture_output=True)


with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    video = work / "mutated.mp4"
    stages = work / "stages.json"
    report = work / "report.json"
    stages.write_text(json.dumps([
        {"stage": "mutated", "startFrame": 0, "endFrameExclusive": TOTAL_FRAMES}
    ]))
    filter_chain = (
        f"loop=loop={LOOP_FRAMES}:size=1:start=30,"
        f"setpts=PTS+({GAP_SECONDS}/TB)*gte(N\\,{GAP_AT_FRAME})"
    )
    run([
        "ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-t", "2",
        "-i", "testsrc2=size=160x90:rate=30", "-vf", filter_chain,
        "-c:v", "libx264", "-crf", "18", "-pix_fmt", "yuv420p", str(video),
    ])
    result = subprocess.run(
        ["python3", str(CHECK), str(video), str(stages), "--report-json", str(report)],
        capture_output=True, text=True,
    )
    payload = json.loads(report.read_text())
    print(f"checker exit {result.returncode}")
    print(f"repeated frames {payload['stages'][0]['repeatedFrames']}")
    print(f"PTS gap failures {payload['ptsGapFailures']}")
    assert result.returncode != 0, "the mutated film passed"
    assert payload["stages"][0]["repeatedFrames"] > 0, "the loop was not detected"
    assert GAP_AT_FRAME in payload["ptsGapFailures"], "the PTS gap was not detected"
