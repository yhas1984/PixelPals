# Jelly: medicine expressions

Generated with the built-in `image_gen` tool. Exact prompt: [`prompt.txt`](prompt.txt). Reference: [`reference.png`](reference.png), composited from the current clean `jelly_0.png`. Generated source: [`generated.png`](generated.png), RGBA1254×1254 with real alpha.

The four drawings show attention, acceptance with an open mouth, swallowing with closed eyes, and relief. They keep the same glossy green material and no exterior white stroke. The sheet is prepared using `python3 -m tools.jelly.prepare_medicine`: one source scale (202/627) for the four cells, translation to a shared center/support, removal of isolated generated flecks. It never fits each frame at runtime.

Final resources: `tools/jelly/clean/medicine_0.png` through `medicine_3.png`,256×256 RGBA. They replace only care cells20–23 through `tools/jelly/medicine_artwork.py` and the shared care atlas builder. The remaining20care cells and all9legacy resources retain their previous artwork.

The prepared review is [`prepared-review.png`](prepared-review.png); measurements are in [`prepared.json`](prepared.json). Width193px in all four drawings; height133–134px, alpha>=32 area20528–20601px (less than0.4% spread). Mouth contacts were inspected against the drawn dark mouths and are checked against packaged pixels.

The original facial perspective is slightly angled; these expressions face the viewer more directly. This is an authored change of expression, with the source camera and body mass matched to the original. It does not close the pending art for rest or the other care actions. Android renderer and installation evidence is kept in `docs/companion/evidence/jelly-medicine-2026-09-12/`.
