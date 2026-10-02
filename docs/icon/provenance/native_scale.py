#!/usr/bin/env python3
"""Turn the native capture crop into the shipped square icon (Pillow 12.3.0).

The crop is Minecraft GUI pixel art: every GUI pixel is a BLOCK x BLOCK square of one
colour. This script recovers the native GUI-pixel image by taking each block's majority
colour (ties, which only occur on the faint sky-gradient seams, go to the candidate
colour that is most common in the surrounding 3x3 blocks, then to the smallest RGB
value), enlarges it FACTOR times with NEAREST, centres it on a transparent CANVAS x CANVAS
square and writes ../icon.png. The mod's shipped copies are that icon resized to 128x128
with LANCZOS.

Run from docs/icon/provenance: python3 native_scale.py
"""
from collections import Counter
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
SOURCE = HERE / "capture-crop.png"  # the delivered 340x340 capture crop; its source frame is lost
BOX = None
BLOCK, FACTOR, CANVAS = 4, 6, 512  # GUI scale 4 -> 85x85 native, x6 = 510 on 512
SHIPPED = (
    "common/src/main/resources/assets/simpletpa/icon.png",
    "fabric/src/gametest/resources/assets/simpletpa/icon.png",
)


def native(crop):
    w, h = crop.size
    assert w % BLOCK == 0 and h % BLOCK == 0, (w, h, BLOCK)
    px = crop.load()
    nw, nh = w // BLOCK, h // BLOCK
    counts = [
        [Counter(px[bx * BLOCK + i, by * BLOCK + j] for i in range(BLOCK) for j in range(BLOCK)) for bx in range(nw)]
        for by in range(nh)
    ]
    out = Image.new("RGB", (nw, nh))
    op = out.load()
    ties = 0
    for by in range(nh):
        for bx in range(nw):
            c = counts[by][bx]
            top = max(c.values())
            cands = [col for col, n in c.items() if n == top]
            if len(cands) > 1:
                ties += 1
                around = Counter()
                for y in range(max(0, by - 1), min(nh, by + 2)):
                    for x in range(max(0, bx - 1), min(nw, bx + 2)):
                        around.update(counts[y][x])
                cands.sort(key=lambda col: (-around[col], col))
            op[bx, by] = cands[0]
    return out, ties


crop = Image.open(SOURCE).convert("RGB")
if BOX is not None:
    crop = crop.crop(BOX)
small, ties = native(crop)
big = small.resize((small.width * FACTOR, small.height * FACTOR), Image.Resampling.NEAREST)
icon = Image.new("RGBA", (CANVAS, CANVAS))
icon.paste(big, ((CANVAS - big.width) // 2, (CANVAS - big.height) // 2))
icon.save(HERE.parent / "icon.png")
for rel in SHIPPED:
    icon.resize((128, 128), Image.Resampling.LANCZOS).save(REPO / rel)
print(f"native {small.size}, {ties} tied blocks, x{FACTOR} -> {big.size} centred on {CANVAS}x{CANVAS}")
