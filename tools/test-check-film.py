#!/usr/bin/env python3
"""Mutation test for check-film.py.

Repeats, long holds and PTS gaps must fail the film. Repeats in a still scene and a repeated slot
that carries a new caption must not.
"""

import json
import subprocess
import tempfile
from pathlib import Path

CHECK = Path(__file__).with_name("check-film.py")
LOOP_START = 30
SHORT_LOOPS = (1, 10, 12)
HOLD_LOOPS = (30, 40)
SLOW_CONTRAST = 0.15
GAP_AT_FRAME = 40
GAP_SECONDS = 0.0667
MAX_STILL_SECONDS = 1.2
MOVING_SOURCE = "testsrc2=size=160x90:rate=30"
STILL_SOURCE = "color=c=gray:size=160x90:rate=30"
# A box that jumps 8 pixels every 10 frames: nine identical pairs between jumps in a still scene.
STILL_JUMPING_BOX = ",".join(
    f"drawbox=x={10 + 8 * jump}:y=40:w=8:h=8:color=white:t=fill"
    f":enable='between(n,{10 * jump},{10 * jump + 9})'"
    for jump in range(6)
)
# Three by three pixels 16 levels off the slow scene: 0.01 over the frame, 1.0 in one block.
FAINT_CAPTION = f"drawbox=x=40:y=40:w=3:h=3:color=0x909090:t=fill:enable='gte(n,{LOOP_START})'"
# Lossless, so a repeated slot is bit-identical. At crf 12 to 18 the encoder re-codes a repeated
# slot with a different error, up to 0.05 over the frame and 1.2 in a block of a busy scene; the
# bars' allowance for that comes from take 12, not from these clips.
TEST_ENCODE = ("-c:v", "libx264", "-crf", "0", "-pix_fmt", "yuv420p")


def run(command):
    subprocess.run(command, check=True, capture_output=True)


def frame_count(video):
    return int(subprocess.run([
        "ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
        "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", str(video),
    ], check=True, capture_output=True, text=True).stdout.strip())


def mutant(video, work, name, loop_frames, gap=False, contrast=None, source=MOVING_SOURCE,
           after=()):
    filters = [f"loop=loop={loop_frames}:size=1:start={LOOP_START}"] if loop_frames else []
    if contrast is not None:
        filters.append(f"eq=contrast={contrast}")
    filters.extend(after)
    if gap:
        filters.append(f"setpts=PTS+({GAP_SECONDS}/TB)*gte(N\\,{GAP_AT_FRAME})")
    run([
        "ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-t", "2",
        "-i", source, "-vf", ",".join(filters) or "null",
        *TEST_ENCODE, str(video),
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

    for loop_frames in SHORT_LOOPS:
        name = f"slow-repeat{loop_frames}"
        code, report = mutant(work / f"{name}.mp4", work, name, loop_frames, contrast=SLOW_CONTRAST)
        stage = report["stages"][0]
        print(f"slow scene loop {loop_frames:2d}: exit {code}, repeats {stage['repeatedFrames']}")
        assert code != 0, f"the {loop_frames}-frame repeat in a slow scene passed"
        assert stage["repeatedFrames"] > 0, (
            f"the {loop_frames}-frame repeat in a slow scene was not detected"
        )

    for loop_frames in HOLD_LOOPS:
        code, report = mutant(work / f"hold{loop_frames}.mp4", work,
                              f"hold{loop_frames}", loop_frames)
        stage = report["stages"][0]
        print(f"hold {loop_frames:2d}: exit {code}, decoded repeats "
              f"{report['repeatedPixelRunFrames']}, stage repeats {stage['repeatedFrames']}, "
              f"still {stage['longestStillSeconds']:.3f}")
        assert report["repeatedPixelRunFrames"] == 0, (
            "a scripted hold was counted as a short decoded repeat"
        )
        if loop_frames == HOLD_LOOPS[0]:
            assert stage["longestStillSeconds"] <= MAX_STILL_SECONDS
        else:
            assert stage["longestStillSeconds"] > MAX_STILL_SECONDS
            assert code != 0, "a hold over the still bar passed"

    code, report = mutant(work / "still.mp4", work, "still", 0, source=STILL_SOURCE,
                          after=(STILL_JUMPING_BOX,))
    stage = report["stages"][0]
    print(f"still scene with a jumping box: exit {code}, repeats {stage['repeatedFrames']}, "
          f"still-scene near-identical {stage['stillSceneNearIdenticalFrames']}")
    assert code == 0, "repeated slots between the jumps of a still scene failed the film"
    assert stage["stillSceneNearIdenticalFrames"] > 0, "the still scene's repeats were not reported"

    code, report = mutant(work / "plain.mp4", work, "plain", 1, contrast=SLOW_CONTRAST)
    plain = report["stages"][0]["repeatedFrames"]
    code, report = mutant(work / "caption.mp4", work, "caption", 1, contrast=SLOW_CONTRAST,
                          after=(FAINT_CAPTION,))
    caption = report["stages"][0]["repeatedFrames"]
    print(f"repeated slot in a slow scene: {plain} repeats; carrying a new caption: {caption}")
    assert plain == 1, "the repeated slot without a caption was not found"
    assert caption == 0, "a repeated slot that carries a new caption was dropped as a repeat"

    code, report = mutant(work / "gap.mp4", work, "gap", 0, gap=True)
    print(f"gap: exit {code}, failures {report['ptsGapFailures']}")
    assert code != 0, "the PTS gap passed"
    assert GAP_AT_FRAME in report["ptsGapFailures"], "the PTS gap was not detected"
