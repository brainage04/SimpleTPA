"""Three 340x340 native-pixel crops, with measured geometric constraints."""
from pathlib import Path
from hashlib import sha256
import json
import shutil
from PIL import Image
from measure import measure

HERE = Path(__file__).resolve().parent
ROUND3 = HERE.parent
SIZE = 340

def digest(path):
    return sha256(path.read_bytes()).hexdigest()

def write_json(name, data):
    (HERE / name).write_text(json.dumps(data, indent=2) + "\n")

def make_crop(key, project, source, bottom, entries, expected_box):
    image, info = measure(source)
    ink = info["inkBBox"]
    assert ink == expected_box, (key, ink)
    assert len(info["glyphLineBBoxes"]) == len(entries)
    ideal_left = (ink[0] + ink[2] - SIZE) / 2
    left = max(0, int(ideal_left))
    box = [left, bottom - SIZE, left + SIZE, bottom]
    assert 0 <= box[0] <= ink[0] < ink[2] <= box[2] <= image.width
    assert 0 <= box[1] <= ink[1] < ink[3] <= box[3] <= image.height
    output = HERE / f"{key}-autocomplete-square.png"
    if key == "simpletpa":
        assert box == [0, 0, SIZE, SIZE]
        shutil.copyfile(source, output)
    else:
        image.crop(box).save(output)
    saved = Image.open(output).convert("RGB")
    assert saved.size == (SIZE, SIZE)
    assert saved.tobytes() == image.crop(box).tobytes(), "output not an exact source crop"
    local_ink = [ink[0] - box[0], ink[1] - box[1], ink[2] - box[0], ink[3] - box[1]]
    band = saved.crop((0, local_ink[1], SIZE, local_ink[3]))
    dark_bbox = band.convert("L").point(lambda p: 255 if p < 65 else 0).getbbox()
    assert dark_bbox == (local_ink[0], 0, local_ink[2], info["inkHeight"])
    sky_left, sky_right = dark_bbox[0], SIZE - dark_bbox[2]
    sky_bands = [band.crop((0, 0, sky_left, band.height)), band.crop((SIZE - sky_right, 0, SIZE, band.height))]
    for sky in sky_bands:
        assert all(b > r + 30 and b > g + 20 for r, g, b in sky.get_flattened_data()), "non-sky pixel in measured margin"
    info.update({
        "project": project, "path": str(output.relative_to(ROUND3)),
        "sourceSHA256": digest(source), "outputSHA256": digest(output),
        "cropBox": box, "outputSize": [SIZE, SIZE], "nativeGuiScale": 4,
        "idealCropLeft": ideal_left, "inkBBoxInOutput": local_ink,
        "trimFromSource": {"left": box[0], "top": box[1], "right": image.width - box[2], "bottom": image.height - box[3]},
        "measuredSky": {"left": sky_left, "right": sky_right, "absoluteDifference": abs(sky_left - sky_right), "pureSkyColumnsLeft": sky_left, "pureSkyColumnsRight": sky_right, "measurementBand": [local_ink[1], local_ink[3]], "skyChannelExtrema": [sky.getextrema() for sky in sky_bands]},
        "entries": entries, "entryBBoxesContained": True, "pixelIdenticalToSourceCrop": True,
        "scaling": "none", "interpolation": "none", "padding": "none",
        "inputBar": "omitted entirely by vertical crop; no truncated command text" if key == "simplehomes" else "included; native full-width bar is cropped at right as in approved reference",
    })
    return info


def main():
    records = [
        make_crop("simpletpa", "SimpleTPA", ROUND3 / "captures-crops/simpletpa-autocomplete-square.png", 340, ["tpaccept", "tpautoaccept", "tpdeny", "tprequest"], [36, 88, 304, 280]),
        make_crop("simplehomes", "SimpleHomes", HERE / "frames/simplehomes-home-wide-full.png", 2104, ["base", "mine", "village"], [148, 1956, 280, 2100]),
        make_crop("spawncommands", "SpawnCommands", HERE / "frames/spawncommands-spawn-wide-full.png", 2160, ["spawnof", "spawnpoint", "spawnshare"], [36, 1956, 280, 2100]),
    ]
    alternative = HERE / "evidence/closest-equal-sky/spawncommands-autocomplete-316.png"
    alternative.parent.mkdir(parents=True, exist_ok=True)
    source = HERE / "frames/spawncommands-spawn-wide-full.png"
    with Image.open(source) as image:
        image.crop((0, 1844, 316, 2160)).save(alternative)
    closest = {"project": "SpawnCommands", "path": str(alternative.relative_to(ROUND3)), "sourceFrame": str(source.relative_to(ROUND3)), "size": [316, 316], "cropBox": [0, 1844, 316, 2160], "trimFromSource": {"left": 0, "top": 1844, "right": 4804, "bottom": 0}, "inkBBox": [36, 1956, 280, 2100], "inkBBoxInOutput": [36, 112, 280, 256], "measuredSky": {"left": 36, "right": 36}, "outputSHA256": digest(alternative), "reason": "largest feasible exactly symmetric crop at GUI scale 4: side <= 2*36+244 = 316; therefore also closest such integer side to 340"}
    write_json("crop-report.json", {"coordinateConvention": "[left,top,right,bottom), exclusive right/bottom; ink bbox includes the complete native autocomplete panel and its glyphs", "requestedSize": [340, 340], "primaryOutputCount": 3, "rootCause": "Previous algorithm anchored every crop at the frame left and set side = 2*panelLeft + panelWidth, yielding 340/428/316. Fixing side to 340 requires an x44 crop for Homes, but x-12 for Spawn; a 5120px-wide recapture confirms native GUI anchoring is unchanged.", "images": records, "closestExactSymmetryAlternative": closest})
    manifest = []
    for record in records:
        sky = record["measuredSky"]
        manifest.append({"project": record["project"], "label": f'{record["project"]} chat autocomplete — 340x340, native GUI scale 4', "path": record["path"], "method": "Byte-identical copy of approved reference" if record["project"] == "SimpleTPA" else "Integer crop of new native F2 screenshot; no resize, resampling, interpolation, compositing or padding", "source": record["sourceFrame"], "notes": f'340x340; sky {sky["left"]} left / {sky["right"]} right; ink bbox {record["inkBBox"]}; crop {record["cropBox"]}. {record["inputBar"]}. See captures-crops2/crop-report.json for pixel/hash evidence.'})
    write_json("manifest.json", {"entries": manifest, "alternatives": [{"project": "SpawnCommands", "label": "Closest exact-symmetry fallback — 316x316, NOT part of the three 340x340 primaries", "path": closest["path"], "method": "Integer crop only of wider native scale-4 screenshot", "source": closest["sourceFrame"], "notes": "36/36 sky, all three entries and input text retained; same native typography, different canvas size."}]})
    write_json("blockers.json", {"blockers": [
        {"project": "SpawnCommands", "constraint": "340x340 and perfectly equal sky cannot both be met at native GUI scale 4 with the left-anchored command panel", "evidence": {"originalSurvivingCropInkBBox": [36, 112, 280, 256], "wider5120FrameInkBBox": [36, 1956, 280, 2100], "requiredCenteredCrop": [-12, 1820, 328, 2160], "missingNativeSkyLeft": 12, "bestFeasible340Crop": [0, 1820, 340, 2160], "measuredSky": [36, 60], "minimumAsymmetryPx": 24}, "resolution": "Primary delivered at 340x340 with minimum possible 24px margin difference, per parent direction. Also supplied the closest perfectly symmetric integer crop at 316x316, 36/36. No pixels invented."},
        {"project": "SimpleTPA / SimpleHomes / SpawnCommands", "constraint": "Original full-frame files referenced by captures-crops/crop-report.json were deleted before this work", "evidence": "captures-ui/renders/frames/ and captures-ui/runtime/screenshots/ were empty; opening captures-ui/renders/frames/simpletpa-tp-full.png raised FileNotFoundError. Surviving approved PNGs remain.", "resolution": "Preserved approved SimpleTPA PNG byte-for-byte; re-captured Homes and Spawn in copied UI Void world at 5120x2160, GUI scale4, camera pitch -90, clouds off, chat cleared."}
    ], "notes": [{"project": "SimpleHomes", "constraint": "Centered x44 crop would cut the native input command starting x16", "resolution": "Vertical crop stops at y2104, immediately before the input bar; all autocomplete entries remain fully visible, and no partial input command is shown."}]})
    print(json.dumps({"size": [SIZE, SIZE], "files": [r["path"] for r in records], "margins": {r["project"]: r["measuredSky"] for r in records}, "allPixelIdenticalToSourceCrops": True}, indent=2))

if __name__ == "__main__":
    main()
