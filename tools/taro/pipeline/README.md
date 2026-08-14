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
debug copy under `app/src/debug/assets/pets/taro/`. Production assets are not
replaced until the device review is approved.
