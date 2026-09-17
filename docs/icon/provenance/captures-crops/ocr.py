"""Read the entry text of the crops back out of the pixels.

No OCR engine and no vision model is available here, so the client's own bitmap
font (assets/minecraft/textures/font/ascii.png, 8x8 cells) is used as an exact
template: a line's glyph pixels are isolated by colour (glyph body = the entry
colour, shadow = colour//4), then a depth-first search over the glyph table finds
the characters whose bitmaps, placed at GUI scale 4, reproduce that pixel set
*exactly*.  A decode is only reported when the re-render is pixel-identical, so
the strings below are measured, not guessed.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "captures-ui" / "tools"))
from mcfont import Font  # noqa: E402

HERE = Path(__file__).resolve().parent
ROUND3 = HERE.parent
SCALE = 4
PANEL = (25, 32, 47)


def line_mask(px, x0, x1, y0, y1) -> dict:
    """{(x, y): colour} of the glyph bodies of one entry line, plus its colour."""
    counts = {}
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            p = px[x, y]
            if max(abs(p[i] - PANEL[i]) for i in range(3)) > 10:
                counts[p] = counts.get(p, 0) + 1
    # glyph body colour = most frequent colour that is brighter than its shadow
    for colour, _ in sorted(counts.items(), key=lambda kv: -kv[1]):
        if colour != tuple(c // 4 for c in colour) and sum(colour) > sum(PANEL):
            probe = colour
            break
    mask = {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1) if px[x, y] == probe}
    return mask, probe


def render(font: Font, text: str, pen_x: int, top_y: int) -> set:
    out = set()
    pen = pen_x
    for ch in text:
        g = font.glyphs.get(ch)
        if g is None:
            pen += font.advance(ch) * SCALE
            continue
        left, gtop, bitmap = g
        for gy, row in enumerate(bitmap):
            for gx, on in enumerate(row):
                if on:
                    for dy in range(SCALE):
                        for dx in range(SCALE):
                            out.add((pen + (left + gx) * SCALE + dx, top_y + (gtop + gy) * SCALE + dy))
        pen += (len(bitmap[0]) + 1) * SCALE
    return out


def decode(font: Font, mask: set) -> list[str]:
    xs = sorted({x for x, _ in mask})
    cands = [(ch, g) for ch, g in font.glyphs.items()]
    sols: list[str] = []

    def step(pen: int, top: int, text: str) -> None:
        if len(sols) > 4:
            return
        rest = {p for p in mask if p[0] >= pen}
        if not rest:
            sols.append(text)
            return
        for ch, (left, gtop, bitmap) in cands:
            adv = (len(bitmap[0]) + 1) * SCALE
            win = {p for p in rest if p[0] < pen + adv}
            if not win:
                continue
            exp = render(font, ch, pen, top)
            if exp == win and all(exp):
                step(pen + adv, top, text + ch)

    for top in range(min(y for _, y in mask) - 8, min(y for _, y in mask) + 1):
        for ch, (left, _, _) in cands:
            if left * SCALE > min(xs):
                continue
            step(min(xs) - left * SCALE, top, "")
    return sorted(set(sols))


def main() -> int:
    font = Font()
    report = json.loads((HERE / "crop-report.json").read_text())
    out = {}
    for key, r in report["images"].items():
        img = Image.open(HERE / r["path"]).convert("RGB")
        px = img.load()
        box = r["newSquare"]["box"]
        entries = []
        for line in r["entryLines"]:
            x0, x1 = line["x0"] - box[0], line["x1"] - box[0]
            y0, y1 = line["y0"] - box[1], line["y1"] - box[1]
            mask, colour = line_mask(px, x0, x1, y0, y1)
            sols = decode(font, mask)
            exact = [s for s in sols if render(font, s, min(x for x, _ in mask) - font.glyphs[s[0]][0] * SCALE,
                                               min(y for _, y in mask)) == mask]
            entries.append({"line": [x0, y0, x1, y1], "colour": list(colour),
                            "pixels": len(mask), "solutions": sols, "exact": exact})
        out[key] = {"size": list(img.size), "entries": entries}
        print(key, json.dumps(entries, indent=1))
    (HERE / "ocr.json").write_text(json.dumps(
        {"source": "captures-crops/ocr.py", "font": "client ascii.png at GUI scale 4", "images": out},
        indent=2) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
