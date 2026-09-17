"""Row/column profile of the chat + autocomplete region of a capture frame.

Background classes in these frames (void world, camera straight up):
  sky      ~ (133,172,255)
  chatbar  ~ ( 66, 85,127)  (translucent chat background rectangle)
Everything else counts as ink (text + its drop shadow).
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

SKY = (133, 172, 255)
BAR = (66, 85, 127)


def classify(p) -> str:
    r, g, b = p
    if abs(r - SKY[0]) <= 3 and abs(g - SKY[1]) <= 3 and abs(b - SKY[2]) <= 3:
        return "sky"
    if abs(r - BAR[0]) <= 4 and abs(g - BAR[1]) <= 4 and abs(b - BAR[2]) <= 4:
        return "bar"
    return "ink"


def main() -> None:
    path = Path(sys.argv[1])
    y0 = int(sys.argv[2]) if len(sys.argv) > 2 else 1950
    y1 = int(sys.argv[3]) if len(sys.argv) > 3 else 2160
    x0 = int(sys.argv[4]) if len(sys.argv) > 4 else 0
    x1 = int(sys.argv[5]) if len(sys.argv) > 5 else 1200
    im = Image.open(path).convert("RGB")
    px = im.load()
    w, h = im.size
    y1 = min(y1, h)
    x1 = min(x1, w)
    for y in range(y0, y1):
        xs = [x for x in range(x0, x1) if classify(px[x, y]) == "ink"]
        barcount = sum(1 for x in range(x0, x1) if classify(px[x, y]) == "bar")
        if xs:
            print(f"y={y:4d} nink={len(xs):4d} x[{min(xs)}..{max(xs)}] bar={barcount}")
        else:
            print(f"y={y:4d} nink=   0 x[   ..   ] bar={barcount}")


if __name__ == "__main__":
    main()
