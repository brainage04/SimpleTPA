#!/usr/bin/env python3
"""Test for recut-repeats.py: repeated slots go with their audio, everything else stays."""

import json
import subprocess
import tempfile
from pathlib import Path

RECUT = Path(__file__).with_name("recut-repeats.py")
CHECK = Path(__file__).with_name("check-film.py")
SLOW_SCENE = "testsrc2=size=160x90:rate=30"
SLOW_CONTRAST = 0.15
REPEATED_SLOTS = (20, 60)
SECONDS = 3
FPS = 30
AUDIO_TOLERANCE_SECONDS = 0.05
FIRST_STAGE_FRAMES = 45
# Lossless, as in test-check-film.py, so a repeated slot is bit-identical.
TEST_ENCODE = ("-c:v", "libx264", "-crf", "0", "-pix_fmt", "yuv420p")


def run(command):
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def probe(video, stream, entry, count_frames=False):
    command = ["ffprobe", "-v", "error"]
    if count_frames:
        command.append("-count_frames")
    command += ["-select_streams", stream, "-show_entries", f"stream={entry}", "-of", "csv=p=0",
                str(video)]
    return run(command).strip()


def make_film(video):
    loops = ",".join(f"loop=loop=1:size=1:start={slot}" for slot in REPEATED_SLOTS)
    run([
        "ffmpeg", "-v", "error", "-y",
        "-f", "lavfi", "-t", str(SECONDS), "-i", SLOW_SCENE,
        "-f", "lavfi", "-t", str(SECONDS), "-i", "sine=frequency=440:sample_rate=48000",
        "-vf", f"{loops},eq=contrast={SLOW_CONTRAST}",
        *TEST_ENCODE, "-c:a", "aac", str(video),
    ])


with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    film = work / "film.mp4"
    make_film(film)
    frames = int(probe(film, "v:0", "nb_read_frames", count_frames=True))
    stages = work / "film.stages.json"
    stages.write_text(json.dumps([
        {"stage": "first", "startFrame": 0, "endFrameExclusive": FIRST_STAGE_FRAMES},
        {"stage": "second", "startFrame": FIRST_STAGE_FRAMES, "endFrameExclusive": frames},
    ]))
    out = work / "out.mp4"
    out_stages = work / "out.stages.json"
    report_path = work / "report.json"
    output = subprocess.run(
        ["python3", str(RECUT), str(film), str(stages), str(out), str(out_stages),
         "--report-json", str(report_path)],
        capture_output=True, text=True,
    )
    print(output.stdout, end="")
    assert output.returncode == 0, output.stderr
    report = json.loads(report_path.read_text())

    dropped = len(REPEATED_SLOTS)
    assert report["framesDropped"] == dropped, f"dropped {report['framesDropped']}, not {dropped}"
    assert report["stages"][0]["framesDropped"] == 1
    assert report["stages"][1]["framesDropped"] == 1
    assert int(probe(out, "v:0", "nb_read_frames", count_frames=True)) == frames - dropped

    audio_before = float(probe(film, "a:0", "duration"))
    audio_after = float(probe(out, "a:0", "duration"))
    expected = audio_before - dropped / FPS
    assert abs(audio_after - expected) <= AUDIO_TOLERANCE_SECONDS, (
        f"audio {audio_after:.3f}s, expected {expected:.3f}s after dropping {dropped} slots"
    )

    new_stages = json.loads(out_stages.read_text())
    assert new_stages[0]["startFrame"] == 0
    assert new_stages[0]["endFrameExclusive"] == new_stages[1]["startFrame"]
    assert new_stages[1]["endFrameExclusive"] == frames - dropped

    check = subprocess.run(["python3", str(CHECK), str(out), str(out_stages)],
                           capture_output=True, text=True)
    assert check.returncode == 0, "the recut film still fails the film check:\n" + check.stdout
    print("recut: ok")
