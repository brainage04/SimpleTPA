"""Square chat-autocomplete crops with equal sky margins left and right of the entries.

Round-3 feedback item: the three ``*-autocomplete-square.png`` renders from
``captures-ui`` were bottom-left anchored squares with far more sky on the right
than on the left of the autocomplete entry block.  This script re-derives the
square from the original 3840x2160 frames (no re-encoding of the old crops):

  * the entry block is found by ink detection: all pixels of the autocomplete
    band (the rows above the chat input bar, x < 1000) that are not sky and not
    the translucent chat-bar colour.  That is the suggestion/usage panel: its
    solid background plus the glyphs on top of it.
  * the crop keeps the frame's left and bottom edge fixed (only the top and the
    right are trimmed), so the sky left of the block is ``block.x0`` pixels.
  * a square of side ``2 * block.x0 + block.width`` puts exactly ``block.x0``
    pixels of sky on the right as well.
  * the resulting box must still contain the block and the chat input line text
    (the "context"); the script asserts that instead of trusting it.

Colours of a ``UI Void`` frame (3840x2160, GUI scale 4): sky ~ (133,172,255),
chat bar ~ (66,85,127), suggestion panel ~ (25,32,47), entry glyphs (170,170,170)
/ (42,42,42), input line glyphs (128,128,128) / (170,170,170) / (32,32,32).
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROUND3 = HERE.parent
FRAMES = ROUND3 / "captures-ui" / "renders" / "frames"

SKY = (133, 172, 255)
BAR = (66, 85, 127)
PANEL = (25, 32, 47)
TOL = 6

# project -> (source frame, file stem, old square side used by captures-ui)
SHOTS = {
    "simpletpa": ("simpletpa-tp-full.png", "simpletpa-autocomplete-square.png", 392),
    "simplehomes": ("simplehomes-home-full.png", "simplehomes-autocomplete-square.png", 456),
    "spawncommands": ("spawncommands-spawn-full.png", "spawncommands-autocomplete-square.png", 368),
}
BAND_X1 = 1000          # entry panel lives in the left 1000 px of the frame
LABEL = {
    "simpletpa": "SimpleTPA",
    "simplehomes": "SimpleHomes",
    "spawncommands": "SpawnCommands",
}


def near(p, ref, tol=TOL) -> bool:
    return abs(p[0] - ref[0]) <= tol and abs(p[1] - ref[1]) <= tol and abs(p[2] - ref[2]) <= tol


def analyse(frame: Path) -> dict:
    im = Image.open(frame).convert("RGB")
    px = im.load()
    w, h = im.size

    # chat input bar: first row from the bottom that is mostly bar-coloured
    bar_top = None
    for y in range(h - 1, h // 2, -1):
        n = sum(1 for x in range(0, BAND_X1, 4) if near(px[x, y], BAR))
        if n > (BAND_X1 // 4) * 0.8:
            bar_top = y
        elif bar_top is not None:
            break
    assert bar_top is not None and bar_top < h - 1, "no chat bar found"

    # entry block: every non-sky pixel above the chat bar
    xs, ys = [], []
    for y in range(bar_top - 1, bar_top - 600, -1):
        row = [x for x in range(0, BAND_X1) if not near(px[x, y], SKY)]
        if not row:
            if ys:
                break
            continue
        xs += [row[0], row[-1]]
        ys.append(y)
    assert xs, "no autocomplete block found above the chat bar"
    block = {"x0": min(xs), "x1": max(xs), "y0": min(ys), "y1": max(ys)}
    block["width"] = block["x1"] - block["x0"] + 1
    block["height"] = block["y1"] - block["y0"] + 1

    # glyph rows inside the block: pixels that differ from the panel background
    # in any channel (entries are yellow 255,255,0 / gray 170,170,170 over it),
    # grouped into text lines - proves the single entries are all inside the block
    lines = []
    for y in range(block["y0"], block["y1"] + 1):
        gx = [x for x in range(block["x0"], block["x1"] + 1)
              if max(abs(px[x, y][i] - PANEL[i]) for i in range(3)) > 10]
        if gx:
            if lines and y == lines[-1]["y1"] + 1:
                lines[-1]["y1"] = y
                lines[-1]["x0"] = min(lines[-1]["x0"], gx[0])
                lines[-1]["x1"] = max(lines[-1]["x1"], gx[-1])
            else:
                lines.append({"y0": y, "y1": y, "x0": gx[0], "x1": gx[-1]})

    # chat input line text ("context"): non-sky, non-bar pixels below the block
    cx, cy = [], []
    for y in range(block["y1"] + 1, h):
        row = [x for x in range(0, w // 2) if not near(px[x, y], SKY) and not near(px[x, y], BAR)]
        if row:
            cx += [row[0], row[-1]]
            cy.append(y)
    context = None
    if cx:
        context = {"x0": min(cx), "x1": max(cx), "y0": min(cy), "y1": max(cy)}

    return im, {"bar_top": bar_top, "block": block, "lines": lines, "context": context}


def crop_one(key: str) -> dict:
    frame_name, out_name, old_side = SHOTS[key]
    frame = FRAMES / frame_name
    im, m = analyse(frame)
    w, h = im.size
    block = m["block"]

    sky_left = block["x0"]              # crop keeps the frame's left edge
    side = block["width"] + 2 * sky_left
    box = (0, h - side, side, h)

    # the box must contain the whole entry block and the chat input line text
    assert box[2] > block["x1"] and box[1] <= block["y0"], "crop would cut the entries"
    ctx = m["context"]
    assert ctx is None or (box[2] > ctx["x1"] and box[1] <= ctx["y0"]), "crop would cut the input line"

    out = HERE / out_name
    im.crop(box).save(out, optimize=True)

    # verify the deliverable by re-measuring the written file: the block columns
    # inside the crop, and how many columns beside it are pure sky over the
    # block's own rows
    chk = Image.open(out).convert("RGB")
    cpx = chk.load()
    rows = range(block["y0"] - box[1], block["y1"] - box[1] + 1)
    cols = [x for x in range(box[2]) if any(not near(cpx[x, y], SKY) for y in rows)]
    sky_cols = [x for x in range(box[2]) if all(near(cpx[x, y], SKY) for y in rows)]
    left_sky = min(cols)
    right_sky = box[2] - 1 - max(cols)

    return {
        "project": LABEL[key],
        "path": out_name,
        "sourceFrame": str(frame.relative_to(ROUND3)),
        "sourceSize": [w, h],
        "oldSquare": {"box": [0, h - old_side, old_side, h], "side": old_side,
                      "skyLeftOfEntries": block["x0"] - 0,
                      "skyRightOfEntries": old_side - 1 - block["x1"],
                      "blockMargins": {"top": block["y0"] - (h - old_side),
                                       "left": block["x0"],
                                       "right": old_side - 1 - block["x1"],
                                       "bottom": h - 1 - block["y1"]}},
        # what a crop centred on the glyph columns alone (excluding the entry
        # panel's 4 px / 1 GUI px left padding) would measure
        "glyphBlockAlternative": {
            "x0": min(g["x0"] for g in m["lines"]), "x1": max(g["x1"] for g in m["lines"]),
            "side": 2 * min(g["x0"] for g in m["lines"])
                    + (max(g["x1"] for g in m["lines"]) - min(g["x0"] for g in m["lines"]) + 1)},
        # [top, left, right, bottom] pixels around the entry block inside the crop
        "margins": {"top": block["y0"] - box[1], "left": block["x0"] - box[0],
                    "right": box[2] - 1 - block["x1"], "bottom": box[3] - 1 - block["y1"]},
        "method": "",
        "entryBlock": block,
        "entryLines": m["lines"],
        "chatInputText": m["context"],
        "chatBarTop": m["bar_top"],
        "newSquare": {"box": list(box), "side": side,
                      "skyLeftOfEntries": block["x0"] - box[0],
                      "skyRightOfEntries": box[2] - 1 - block["x1"]},
        "trim": {"top": box[1] - (h - old_side), "right": old_side - box[2],
                 "left": 0, "bottom": 0},
        "verifiedInFile": {"size": list(chk.size), "skyLeft": left_sky, "skyRight": right_sky,
                           "pureSkyColumnsLeft": len([c for c in sky_cols if c < min(cols)]),
                           "pureSkyColumnsRight": len([c for c in sky_cols if c > max(cols)])},
    }


def main() -> None:
    report = {"tool": "captures-crops/crop.py", "frames": str(FRAMES.relative_to(ROUND3)),
              "images": {}}
    for key in SHOTS:
        r = crop_one(key)
        r["method"] = ("ink detection on the original 3840x2160 frame: entry block = non-sky pixels "
                       "above the chat input bar; square side = 2*sky_left + block width, anchored on "
                       "the frame's left/bottom edge")
        report["images"][key] = r
        m = r["margins"]
        print(f"{key}: block {r['entryBlock']['width']}x{r['entryBlock']['height']} at "
              f"x{r['entryBlock']['x0']}..{r['entryBlock']['x1']} -> {r['newSquare']['side']}x"
              f"{r['newSquare']['side']} (was {r['oldSquare']['side']}), sky L/R "
              f"{r['newSquare']['skyLeftOfEntries']}/{r['newSquare']['skyRightOfEntries']}, "
              f"trim top {r['trim']['top']} right {r['trim']['right']}")
    (HERE / "crop-report.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    sys.exit(main())
