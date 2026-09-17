"""Measure the native GUI-scale-4 panel; all boxes use exclusive right/bottom."""
from pathlib import Path
from PIL import Image, ImageChops
import json

HERE = Path(__file__).resolve().parent
ROUND3 = HERE.parent

def measure(path):
    image = Image.open(path).convert("RGB")
    width, height = image.size
    bar_top = height - 56
    # Only the native autocomplete band: exclude the full-width input bar below.
    band_box = (0, max(0, height - 600), min(width, 1000), bar_top)
    band = image.crop(band_box)
    # Native panel background is below 65 in all three channels; sky and input
    # bar are brighter. Glyph shadows are inside this same rectangular panel.
    mask = band.convert("L").point(lambda p: 255 if p < 65 else 0)
    found = mask.getbbox()
    assert found, path
    box = [found[0], found[1] + band_box[1], found[2], found[3] + band_box[1]]
    panel = image.crop(box)
    background = panel.getpixel((0, 0))
    diff = ImageChops.difference(panel, Image.new("RGB", panel.size, background))
    r, g, b = diff.split()
    glyph_mask = ImageChops.lighter(ImageChops.lighter(r, g), b).point(lambda p: 255 if p > 10 else 0)
    lines = []
    assert panel.height % 48 == 0, "expected native 12-GUI-pixel rows at scale 4"
    for y in range(0, panel.height, 48):
        row = glyph_mask.crop((0, y, panel.width, y + 48)).getbbox()
        assert row, "empty autocomplete entry"
        lines.append([row[0] + box[0], row[1] + y + box[1], row[2] + box[0], row[3] + y + box[1]])
    return image, {"sourceFrame": str(path.relative_to(ROUND3)), "sourceSize": list(image.size), "inkBBox": box, "inkWidth": panel.width, "inkHeight": panel.height, "glyphLineBBoxes": lines, "panelColor": background, "barTop": bar_top}

if __name__ == "__main__":
    paths = [ROUND3 / "captures-crops" / f"{name}-autocomplete-square.png" for name in ("simpletpa", "simplehomes", "spawncommands")]
    paths.extend(HERE / "frames" / f"{name}-wide-full.png" for name in ("spawncommands-spawn", "simplehomes-home"))
    result = [measure(path)[1] for path in paths]
    (HERE / "evidence/geometry-probe.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
