"""Independent check of the three delivered crops.

Re-opens the written PNGs (not the in-memory images), re-measures them and
compares them byte-for-byte with the source frame region, so a wrong crop box,
a resample or a shifted crop fails here.

  square          width == height
  exact pixels    crop == source.crop(box) pixel by pixel
  subset          new box inside the box of the old captures-ui square
  entries inside  every glyph row of the entry block is inside the crop
  input inside    the chat input line text is inside the crop
  sky symmetry    pure-sky columns left of the block == right of the block
                  (measured over the block's own rows, where the background
                  really is sky and not the chat bar)
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROUND3 = HERE.parent
SKY = (133, 172, 255)
BAR = (66, 85, 127)
TOL = 6


def near(p, ref, tol=TOL) -> bool:
    return all(abs(p[i] - ref[i]) <= tol for i in range(3))


def main() -> int:
    report = json.loads((HERE / "crop-report.json").read_text())
    results = {}
    failures = []
    for key, r in report["images"].items():
        out = HERE / r["path"]
        frame = ROUND3 / r["sourceFrame"]
        img = Image.open(out).convert("RGB")
        src = Image.open(frame).convert("RGB")
        box = tuple(r["newSquare"]["box"])
        w, h = img.size
        ipx, spx = img.load(), src.load()

        checks = {"square": w == h}

        # pixel identity with the source region
        same = all(ipx[x, y] == spx[box[0] + x, box[1] + y] for y in range(h) for x in range(w))
        checks["exactPixelsFromFrame"] = same

        checks["newBoxInsideOldSquare"] = (
            box[0] >= r["oldSquare"]["box"][0] and box[1] >= r["oldSquare"]["box"][1]
            and box[2] <= r["oldSquare"]["box"][2] and box[3] <= r["oldSquare"]["box"][3])

        blk = r["entryBlock"]
        rows = range(blk["y0"] - box[1], blk["y1"] - box[1] + 1)
        cols = [x for x in range(w) if any(not near(ipx[x, y], SKY) for y in rows)]
        sky = [x for x in range(w) if all(near(ipx[x, y], SKY) for y in rows)]
        left, right = min(cols), w - 1 - max(cols)
        checks["entriesFullyInside"] = (min(cols) > 0 and max(cols) < w - 1
                                        and rows.start >= 0 and rows.stop - 1 < h)
        checks["skyLeftEqualsSkyRight"] = left == right
        checks["pureSkyColumnsLeft"] = len([c for c in sky if c < min(cols)])
        checks["pureSkyColumnsRight"] = len([c for c in sky if c > max(cols)])
        ctx = r["chatInputText"]
        checks["inputLineInside"] = (ctx is None or
                                     (ctx["x1"] - box[0] < w and ctx["y1"] - box[1] < h
                                      and ctx["x0"] - box[0] >= 0 and ctx["y0"] - box[1] >= 0))
        # no entry glyph row touches the crop border horizontally
        checks["glyphMarginMin"] = min(min(g["x0"] for g in r["entryLines"]) - box[0],
                                       w - 1 - max(g["x1"] for g in r["entryLines"]))

        results[key] = {"file": r["path"], "size": [w, h],
                        "blockInCrop": {"x0": min(cols), "x1": max(cols),
                                        "y0": blk["y0"] - box[1], "y1": blk["y1"] - box[1]},
                        "skyLeft": left, "skyRight": right,
                        "skyAboveBlock": blk["y0"] - box[1],
                        "skyBelowBlock": box[3] - 1 - blk["y1"], **checks}
        for name, ok in checks.items():
            if isinstance(ok, bool) and not ok:
                failures.append(f"{key}: {name}")

    (HERE / "verification.json").write_text(json.dumps(
        {"source": "captures-crops/verify.py", "results": results, "failures": failures}, indent=2) + "\n")
    for k, v in results.items():
        print(k, v["size"], "sky L/R", v["skyLeft"], v["skyRight"], "failures:", failures or "none")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
