# Diablillo care artwork

`raw/care-redesign-palette.png` is the selected generated RGBA source, corrected
against the native `diablillo_0.png` and `pet_diablillo.png` palette. The earlier
orange draft was rejected. The new source replaces the old care board, including
its malformed three-eye expression and white floor matte; native walking and
idle artwork remain the reference for the character.

Rebuild with `python3 tools/care/build_atlases.py --pet diablillo`.
The dedicated importer preserves generated transparency instead of extracting a
white background. It removes disconnected low-alpha specks, uses one raster
scale for the whole bank, and translates cells onto the foot baseline. It never
normalizes each pose to a different height or stretches its anatomy.

`contact_points.json` stores manually reviewed mouth and forehead positions,
body contacts and measured foot contacts in source-cell coordinates. The importer
transforms those same points with the artwork. Source poses 9 and 18 are exchanged
so runtime pose 9 retains the closed-eye upright sleep contract. Existing care
clip timing is preserved. The reaching hands in output frame 5 correspond to the
trident grip near `(0.70, 0.654)` in `SpeciesCareRenderer`.

`python3 -m unittest tools.diablillo.test_import_care tools.care.test_atlases`
checks reproduction of the packaged pixels, 24 unique frames, RGBA padding and
contact metadata. Visual review on light/dark backgrounds and Android care
rendering remain necessary: structural tests do not detect extra eyes or judge
anatomical quality.
