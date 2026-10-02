# SimpleTPA icon

## What this is

The mod's icon: `icon.png` — 512x512 RGBA PNG, 4 573 bytes,
sha256 `5b6882552eb49275a931d6267404fb55b7573487efc5023cf1f5dfca0ee2ff3d`.

It is a real in-game screenshot of the mod's `/tp` tab-completion: the 340x340 square capture
crop `provenance/capture-crop.png` (GUI scale 4), reduced to its native 85x85 GUI pixels,
enlarged 6x with NEAREST to 510x510 and centred on a transparent 512x512 canvas (1 px margin).
No interpolation, no compositing. The mod ships 128x128 LANCZOS reductions of `icon.png` at
`common/src/main/resources/assets/simpletpa/icon.png` and
`fabric/src/gametest/resources/assets/simpletpa/icon.png`.

## How it was made

**Method: real Minecraft capture.**

The screenshot itself comes from the BrainageHUD/UI capture session (round-3 `captures-ui`):

| | |
|---|---|
| Client | Minecraft 26.2, Fabric Loader 0.19.3, OpenJDK 25.0.4.1+1 |
| Mods loaded | fabric-api 0.156.0+26.2, simpletpa 1.2.0, simplehomes 1.0.0, spawncommands 1.0.0, brainagehud 1.0.3, hudrendererlib 1.0.7, cloth-config-fabric 26.2.155, brainagelib 1.0.1 |
| Display | own Xvfb `:182`, 3840x2160x24; the primary desktop was never used |
| Audio | own PulseAudio null sink `round3_ui_mcscreens` |
| Renderer | software OpenGL (Mesa llvmpipe) |
| Shader pack / resource pack | **none** / **none** |
| World | `UI Void` — flat generator, `layers=[]`, biome `minecraft:the_void`, seed `20260910`: pure void |
| Scene | `/gamemode spectator`, `/time set noon`, `/weather clear`, `/tick freeze`, `/tp @s 0.5 -60 0.5 0 -90` — camera looking straight up into the void sky |
| Screenshot | chat history cleared with F3+D, then `t`, the literal text `/tp` typed, then the game's own native F2 PNG at GUI scale 4 (`captures-ui/renders/frames/simpletpa-tp-full.png`) |

The client completes `/tp` to `/tpaccept` and draws the completions `tpaccept` (selected,
yellow), `tpautoaccept`, `tpdeny` and `tprequest` above the input line.

The capture crop `provenance/capture-crop.png` (340x340 RGB, 2 927 bytes, sha256
`5ffcbd61c50e3854d43c3be00a0352c531cba2bafbb7836584c12b46e3f16eea`; until 2026-10-02 it was
shipped as `icon.png` itself, moved here unchanged) is the exact integer crop
`(0, 1820, 340, 2160)` of that 3840x2160 frame.
It was derived twice: `captures-crops/crop.py` measured the entry block by ink detection and
produced a 340x340 square by trimming 52 px from the top and the right of the previous
392x392 crop, then `captures-crops2/crop.py` copied that file byte for byte into the round-3
delivery tree, and `captures-crops2/verify.py` asserted the byte identity.

**Native-scale enlargement (2026-10-02).** The icon rule is square, a power of two, 512 or
1024 px. The crop is pixel art: 13 colours, on GUI scale 4's 4x4 grid (aligned with the crop).
7 121 of its 7 225 blocks are a single colour; the other 104 lie on faint diagonal seams in the
sky and panel shading where the colours differ by at most 1 level per channel.
`native_scale.py` takes each block's majority colour (7 blocks have no strict majority and go to
the colour that dominates the surrounding 3x3 blocks) to recover the native 85x85 image, enlarges
it by the largest integer factor that fits 512 (6x, 510x510) with NEAREST, and centres it on
a transparent 512x512 canvas. A LANCZOS 340→512 resize was rejected because it blurs every
GUI pixel edge.

Layout inside the capture crop (measured, crop pixels): the entry block (the suggestion
panel including its translucent background) occupies `x 36..303, y 88..280`, so the sky
margins are exactly 36 px left and 36 px right; the chat input line below shows `/tpaccept`
and its text ink (`x 16..215`) is fully inside the crop. The frame's full-width chat bar runs
to the right edge of the crop, as in the approved reference.

## Provenance files

Two sessions contributed files, and both are kept in separate sub-trees so the paths stay
unambiguous. `captures-crops/…` is the derivation that produced the image; everything at the
top level (and `evidence/`) is the later round-3 delivery pass that copied and re-verified it.

| Path | What it is |
|---|---|
| `capture-crop.png` | **The 340x340 capture crop** (formerly `icon.png`, byte-identical) — the only surviving copy of this crop |
| `native_scale.py` | **The script that writes `icon.png`** from `capture-crop.png` (native 85x85 recovery, 6x NEAREST, centred on 512x512) and the two shipped 128x128 copies |
| `manifest.json` | Round-3 delivery record: label, method ("byte-identical copy of approved reference"), source, measured sky margins, ink box and crop box |
| `captures-crops/crop.py` | **The script that produced the capture crop** — ink detection on the 3840x2160 frame, square side `2 * sky_left + block_width`, anchored on the frame's left/bottom edge, with assertions that the block and the input line stay inside |
| `captures-crops/crop-report.json` | Full measurement of the derivation: old 392 px square and its 88 px right margin, the new 340 px square, the entry block, the four entry line boxes, the glyph-only 344 px alternative, the 52 px top/right trim, the chat-bar top, and the per-file verification |
| `captures-crops/verify.py`, `captures-crops/verification.json` | The independent re-check: exact pixels from the frame, sky left == sky right == 36 px, all four entries and the input line fully inside |
| `captures-crops/ocr.py`, `captures-crops/ocr.json` | Exact client-font template matching that decodes the four entry texts and the input line — this is how the entries are named with certainty |
| `captures-crops/profile.py` | Row/column ink profile of the chat region, with the sky / chat-bar / ink colour classes |
| `captures-crops/manifest.json`, `captures-crops/blockers.json` | The derivation session's own delivery record and notes (including the loss of the source frame) |
| `crop.py`, `verify.py`, `measure.py`, `capture_scene.py`, `prepare.py` | The delivery pass's scripts: `crop.py` copies the approved file byte for byte and writes the three 340x340 crops, `verify.py` re-asserts the byte identity, `measure.py` re-measures the panel bands, `capture_scene.py`/`prepare.py` are the capture driver and runtime builder of that session |
| `crop-report.json`, `verification.json`, `manifest.json`, `blockers.json`, `cleanup.json` | The delivery pass's measurements, verification results, notes and resource-teardown record |
| `evidence/scene-commands.json`, `evidence/audio-before-interaction.json`, `evidence/geometry-probe.json` | The delivery session's scene commands, audio-route proof and measured panel geometry |
| `launch-crops2.sh`, `client-crops2.args` | The exact launcher and Java argfile of the delivery session |

Excluded on purpose: the source frame (it does not exist any more, see Notes), the other two
icons' non-shared evidence (they are in their own mods' provenance), `__pycache__`, and the
superseded 392x392 crop of the same frame.

## How to regenerate

`icon.png` is regenerated from the shipped capture crop (Pillow 12.3.0, from
`docs/icon/provenance`):

```sh
python3 native_scale.py    # prints: native (85, 85), 7 tied blocks, x6 -> (510, 510) centred on 512x512
```

The capture crop itself cannot be regenerated from anything shipped here, because its source
frame was destroyed (Notes). The two derivations are nonetheless fully documented:

```sh
# 1. recreate the frame: re-run the UI capture session (see BrainageHUD's provenance)
#    -> captures-ui/renders/frames/simpletpa-tp-full.png  (3840x2160, GUI scale 4)

# 2. re-derive the square  (working directory <round3>/captures-crops)
python3 crop.py

# 3. the delivery pass simply copies that file  (working directory <round3>/captures-crops2)
python3 crop.py && python3 verify.py
```

`captures-crops/crop.py` needs `captures-ui/renders/frames/simpletpa-tp-full.png`; the other
two frames it lists (`simplehomes-home-full.png`, `spawncommands-spawn-full.png`) belong to
superseded icons and are not needed for this one.

## Notes

* **The source frame is gone.** `captures-crops/blockers.json` records it: the full-frame files
  referenced by the crop report (`captures-ui/renders/frames/simpletpa-tp-full.png`) were
  deleted before the later crop pass, and re-opening them raised `FileNotFoundError`. The
  approved 340x340 PNG survived, and that PNG is `capture-crop.png` (shipped as the icon until
  2026-10-02) — it is the only surviving copy of this crop. Both sessions' measurement records
  are shipped, so the geometry remains reproducible *if* the scene is re-captured.
* The capture crop is byte-identical to the earlier approved reference
  (`captures-crops/simpletpa-autocomplete-square.png`, the same sha256); it was not re-captured
  and not re-cropped in the later pass, only copied and re-verified.
* The crop keeps the frame's left and bottom edges and trims only the top and the right, so the
  left/right sky margins are equal (36/36 px) while the vertical margins are not (88 px above
  the block, 60 px below it, inside the chat bar band). This was the requested geometry.
* Re-deriving the square on the glyph columns instead of on the panel would give 344 px instead
  of 340 px; `crop-report.json` keeps that alternative measurement.
* Minecraft 26.2 renders the chat autocomplete as an inline completion plus this suggestion
  panel; it does not draw a dropdown list.

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.
