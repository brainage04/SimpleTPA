#!/usr/bin/env python3
"""Test for recut-repeats.py on encoded 1280x720 films.

A repeated slot goes with its audio. A frame that carries any change survives: a chroma-only
change, a small digit, a change across a block corner and a ramp whose steps are each small but add
up. Input that is not 30 fps is refused.
"""

import json
import subprocess
import tempfile
from pathlib import Path

RECUT = Path(__file__).with_name("recut-repeats.py")
CHECK = Path(__file__).with_name("check-film.py")
WIDTH = 1280
HEIGHT = 720
SECONDS = 3
FPS = 30
REPEATED_SLOTS = (20, 60)
CHANGED_SLOT = REPEATED_SLOTS[0]
PLAIN_SLOT = REPEATED_SLOTS[1]
AUDIO_TOLERANCE_SECONDS = 0.05
FIRST_STAGE_FRAMES = 45
# Gray ground and a 160-pixel white box moving 10 pixels a frame: 0.4 mean pair difference, above
# the checker's still bar of 0.3.
GROUND = f"color=c=0x808080:s={WIDTH}x{HEIGHT}"
BOX = "color=c=white:s=160x160"
BOX_PLACEMENT = "overlay=x=40+300*t:y=300"
# On the slot the loop repeats, luma matches the ground to within 2 and V moves by 21.
CHROMA_ONLY = f"drawbox=x=100:y=100:w=24:h=24:color=0xA07380:t=fill:enable='gte(n,{CHANGED_SLOT})'"
# A 5x7 white-ish digit, 60 levels over the ground.
DIGIT = f"drawbox=x=100:y=100:w=5:h=7:color=0xBCBCBC:t=fill:enable='gte(n,{CHANGED_SLOT})'"
# 6x6 pixels, 96 levels over the ground, centred on the corner of four blocks of the 120x68 grid
# the recut measures on: nine pixels in each block.
BLOCK_CORNER = (
    f"drawbox=x={WIDTH // 2 - 3}:y={HEIGHT // 2 - 3}:w=6:h=6:color=0xE0E0E0:t=fill"
    f":enable='gte(n,{CHANGED_SLOT})'"
)
# A 24x24 patch that rises 3 levels a frame for 10 frames: each step is small, together they are 30.
RAMP_START = 40
RAMP_FRAMES = 10
RAMP_STEP = 3
RAMP_X = 200
RAMP_SIZE = 24
RAMP_AREA = f"between(X,{RAMP_X},{RAMP_X + RAMP_SIZE})*between(Y,{RAMP_X},{RAMP_X + RAMP_SIZE})"
RAMP = (
    f"geq=lum='if({RAMP_AREA},128+{RAMP_STEP}*clip(N-{RAMP_START},0,{RAMP_FRAMES}),lum(X,Y))'"
    ":cb='cb(X,Y)':cr='cr(X,Y)'"
)
# Largest step between two kept frames of the ramp. Frames are kept while a deletion would leave a
# step over the bound of 4, so steps stay near 6, and 11 was measured after two encodes. Deleting
# a frame against a neighbour that is itself deleted leaves a single step of 30.
RAMP_MAX_STEP = 15


def run(command):
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def probe(video, stream, entry, count_frames=False):
    command = ["ffprobe", "-v", "error"]
    if count_frames:
        command.append("-count_frames")
    command += ["-select_streams", stream, "-show_entries", f"stream={entry}", "-of", "csv=p=0",
                str(video)]
    return run(command).strip()


def make_film(video, extra=(), loops=REPEATED_SLOTS, fps=FPS, loop_count=1):
    loop_filters = [f"loop=loop={loop_count}:size=1:start={slot}" for slot in loops]
    chain = ",".join([BOX_PLACEMENT, *loop_filters, *extra])
    run([
        "ffmpeg", "-v", "error", "-y",
        "-f", "lavfi", "-t", str(SECONDS), "-i", f"{GROUND}:r={fps}",
        "-f", "lavfi", "-t", str(SECONDS), "-i", f"{BOX}:r={fps}",
        "-f", "lavfi", "-t", str(SECONDS), "-i", "sine=frequency=440:sample_rate=48000",
        "-filter_complex", f"[0:v][1:v]{chain}[v]", "-map", "[v]", "-map", "2:a",
        "-c:v", "libx264", "-preset", "fast", "-crf", "18", "-pix_fmt", "yuv420p",
        "-c:a", "aac", str(video),
    ])


def recut(work, name, film, stages_frames=None):
    frames = int(probe(film, "v:0", "nb_read_frames", count_frames=True))
    split = FIRST_STAGE_FRAMES if stages_frames is None else stages_frames
    stages = work / f"{name}.stages.json"
    stages.write_text(json.dumps([
        {"stage": "first", "startFrame": 0, "endFrameExclusive": split},
        {"stage": "second", "startFrame": split, "endFrameExclusive": frames},
    ]))
    out = work / f"{name}-out.mp4"
    out_stages = work / f"{name}-out.stages.json"
    report_path = work / f"{name}-report.json"
    result = subprocess.run(
        ["python3", str(RECUT), str(film), str(stages), str(out), str(out_stages),
         "--report-json", str(report_path)],
        capture_output=True, text=True,
    )
    print(result.stdout, end="")
    report = json.loads(report_path.read_text()) if report_path.exists() else None
    return result, report, out, out_stages, frames


def patch_levels(video):
    pixel = RAMP_X + RAMP_SIZE // 2
    raw = subprocess.run([
        "ffmpeg", "-v", "error", "-i", str(video), "-vf", f"crop=2:2:{pixel}:{pixel},format=gray",
        "-f", "rawvideo", "-",
    ], check=True, capture_output=True).stdout
    return [sum(raw[start:start + 4]) / 4 for start in range(0, len(raw), 4)]


with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)

    film = work / "plain.mp4"
    make_film(film)
    result, report, out, out_stages, frames = recut(work, "plain", film)
    assert result.returncode == 0, result.stderr
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
    for row in report["droppedFullResolution"]:
        assert set(row) >= {"frame", "lumaBlock", "chromaBlock", "lumaPixels", "chromaPixels"}

    for name, change in (("chroma", CHROMA_ONLY), ("digit", DIGIT), ("corner", BLOCK_CORNER)):
        film = work / f"{name}.mp4"
        make_film(film, extra=(change,))
        result, report, out, out_stages, frames = recut(work, name, film)
        dropped_frames = [frame for row in report["stages"] for frame in row["droppedFrames"]]
        print(f"{name}: dropped {dropped_frames}, exit {result.returncode}")
        assert CHANGED_SLOT not in dropped_frames, f"the {name} change was deleted with its frame"
        assert PLAIN_SLOT in dropped_frames, "the plain repeated slot was kept"
        assert result.returncode == 1, f"the {name} frame is an ambiguous candidate: exit 1"
        assert "ambiguous" in result.stderr

    film = work / "ramp.mp4"
    make_film(film, extra=(RAMP,), loops=(RAMP_START,), loop_count=RAMP_FRAMES - 1)
    result, report, out, out_stages, frames = recut(work, "ramp", film)
    levels = patch_levels(out)
    steps = [abs(later - earlier) for earlier, later in zip(levels, levels[1:])]
    print(f"ramp: {report['framesDropped']} dropped, largest step {max(steps)}")
    assert levels[-1] - levels[0] >= RAMP_STEP * RAMP_FRAMES - 2, "the end of the ramp is gone"
    assert max(steps) <= RAMP_MAX_STEP, f"the ramp jumps {max(steps)} between kept frames"

    film = work / "slow.mp4"
    make_film(film, fps=25)
    result, report, out, out_stages, frames = recut(work, "slow", film)
    assert result.returncode != 0, "a 25 fps film was recut"
    assert "30 fps" in result.stderr
    print("recut: ok")
