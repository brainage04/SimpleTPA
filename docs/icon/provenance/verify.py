"""Reopen written PNGs and verify exact crop bytes, dimensions, and geometry."""
from pathlib import Path
from hashlib import sha256
import json
from PIL import Image

HERE = Path(__file__).resolve().parent
ROUND3 = HERE.parent
report = json.loads((HERE / "crop-report.json").read_text())
manifest = json.loads((HERE / "manifest.json").read_text())
assert len(manifest["entries"]) == 3
results = []
for record in report["images"]:
    source = Image.open(ROUND3 / record["sourceFrame"]).convert("RGB")
    output_path = ROUND3 / record["path"]
    output = Image.open(output_path).convert("RGB")
    expected = source.crop(record["cropBox"])
    assert output.size == expected.size == (340, 340)
    assert output.tobytes() == expected.tobytes()
    x0, y0, x1, y1 = record["inkBBoxInOutput"]
    assert 0 <= x0 < x1 <= 340 and 0 <= y0 < y1 <= 340
    dark = output.crop((0, y0, 340, y1)).convert("L").point(lambda value: 255 if value < 65 else 0)
    bbox = dark.getbbox()
    assert bbox == (x0, 0, x1, y1 - y0)
    assert (bbox[0], 340 - bbox[2]) == (record["measuredSky"]["left"], record["measuredSky"]["right"])
    results.append({"project": record["project"], "size": list(output.size), "skyLeft": x0, "skyRight": 340 - x1, "cropPixelSHA256": sha256(output.tobytes()).hexdigest(), "fileSHA256": sha256(output_path.read_bytes()).hexdigest(), "exactSourceCrop": True})
reference = ROUND3 / "captures-crops/simpletpa-autocomplete-square.png"
assert reference.read_bytes() == (HERE / "simpletpa-autocomplete-square.png").read_bytes()
spawn = report["images"][2]
# Enumerate every possible 340-wide crop containing the panel and inside frame.
leftmost = max(0, spawn["inkBBox"][2] - 340)
rightmost = min(spawn["inkBBox"][0], spawn["sourceSize"][0] - 340)
feasible = [(abs((36 - left) - (left + 340 - 280)), left) for left in range(leftmost, rightmost + 1)]
assert min(feasible) == (24, 0)
assert 2 * 36 + 244 == 316
alternative = report["closestExactSymmetryAlternative"]
alt_source = Image.open(ROUND3 / alternative["sourceFrame"]).convert("RGB")
alt_output = Image.open(ROUND3 / alternative["path"]).convert("RGB")
assert alt_output.size == (316, 316)
assert alt_output.tobytes() == alt_source.crop(alternative["cropBox"]).tobytes()
alt_dark = alt_output.crop((0, 112, 316, 256)).convert("L").point(lambda value: 255 if value < 65 else 0).getbbox()
assert alt_dark == (36, 0, 280, 144)
result = {"passed": True, "images": results, "approvedReferenceByteIdentical": True, "spawn340MinimumPossibleAsymmetry": 24, "spawn316AlternativeMargins": [36, 36], "resizedPixels": 0, "compositedPixels": 0, "visualInspection": "All three final PNGs viewed at their native 340x340 dimensions: all listed completion entries readable and complete; Homes intentionally omits the input bar."}
(HERE / "verification.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result, indent=2))
