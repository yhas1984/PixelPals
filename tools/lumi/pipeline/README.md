# Lumi V2 Motion Atlas

This is the canonical pipeline for Lumi's disconnected pose atlas. Generated
clips are promoted as a production candidate after visual review; release
promotion still depends on the master-pose and QA gates.

The pipeline uses ten GPT Image 2 contact sheets at `quality=low`, each with
four sequential frames in a 2x2 layout, for a 40-frame atlas. The canonical
Lumi reference remains the source of truth; the other reference sheets only
provide view and motion guidance.

## Generate boards

```bash
source ~/.config/pixelpals/load_keys.sh
python3 tools/lumi/pipeline/generate_boards.py --all --quality low
```

Generated boards are written to `tools/lumi/pipeline/raw/`. The builder writes
the candidate atlas under `tools/lumi/pipeline/atlas_v2/` and refreshes the
debug copy. The production candidate is copied explicitly to
`app/src/main/assets/pets/lumi/` after validation.

## Build and validate

```bash
python3 tools/lumi/pipeline/build_atlas_v2.py
python3 tools/lumi/pipeline/validate_atlas_v2.py
```

The builder writes the normalized frames, review contact sheet, atlas, and
debug copy under `tools/lumi/pipeline/` and `app/src/debug/assets/pets/lumi/`.

## Deterministic review

The debug `LumiDebugActivity` has controls for every V2 clip, direction, and
speed. Use the activity before autonomous testing. The approval criteria are in
`REVIEW_CHECKLIST.md`.
