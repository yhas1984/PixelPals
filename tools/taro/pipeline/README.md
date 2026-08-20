# Taro V2 Motion Atlas

Canonical Taro pipeline preserving the baby sea turtle identity from
`/home/yhas/Pictures/pixelpals_refs/taro.png` while replacing the sparse V1
atlas with a reviewed 40-frame candidate.

## Generate

```bash
source ~/.config/pixelpals/load_keys.sh
python3 tools/taro/pipeline/generate_boards.py --all --quality low
```

Generated boards remain under `tools/taro/pipeline/raw/`.

## Build and validate

```bash
python3 tools/taro/pipeline/build_atlas_v2.py
python3 tools/taro/pipeline/validate_atlas_v2.py
```

The builder writes the candidate atlas, individual frames, review preview, and
debug copy under `app/src/debug/assets/pets/taro/`. The fully quadruped
candidate was promoted to `src/main` after the API 36 device gates and explicit
visual approval on 2026-08-20.

## Quadruped walk candidate

Taro must never walk upright. The approved atlas remains untouched as the
rollback source while the replacement walk cycle is built and validated in an
isolated directory:

```bash
python3 tools/taro/pipeline/build_quadruped_walk_candidate.py
python3 tools/taro/pipeline/build_quadruped_walk_candidate.py --publish-debug
```

The second command publishes the same validated PNG and JSON to `src/debug`
only. Historical candidates remain isolated for provenance and rollback.

## Full quadruped candidate

The follow-up candidate keeps every clip in normal turtle anatomy so idle and
interaction transitions cannot make Taro stand upright:

```bash
python3 tools/taro/pipeline/build_quadruped_full_candidate.py
python3 tools/taro/pipeline/build_quadruped_full_candidate.py --publish-debug
```

The image-generation sources are versioned separately. RGB checkerboards are
converted to real alpha by removing only neutral light pixels connected to the
cell border; enclosed eye, claw, and highlight details remain part of Taro. A
one-pixel source fringe is discarded before normalization to avoid baked matte.
The promoted PNG and JSON must remain byte-identical in the full candidate,
canonical pipeline output, debug assets, and main assets.
