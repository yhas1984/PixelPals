#!/usr/bin/env python3
"""Validate frame coverage and remove orphaned production pet assets."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "app/src/main/res/drawable-nodpi"
BEHAVIORS = ROOT / "app/src/main/java/com/pixelpals/app/feature/overlay/behavior"

RASTER_PETS = {
    "bloop": ("BloopBehavior.kt", "fantasma", [1, 2, 3, 4, 5, 7, 8]),
    "nube_michi": ("NubeMichiBehavior.kt", "gato", list(range(11))),
    "jelly": ("JellyBehavior.kt", "jelly", list(range(8))),
    "corgi": ("CorgiBehavior.kt", "corgi", list(range(14))),
    "patito": ("DuckBehavior.kt", "patito", list(range(10))),
    "diablillo": ("ImpBehavior.kt", "diablillo", list(range(10))),
}

ATLASES = {
    "ginger": ("GingerBehavior.kt", "pets/ginger/ginger_sheet_v2.json"),
    "angel": ("AngelBehavior.kt", "pets/angel/angel_sheet_v4.json"),
    "moki": ("MokiBehavior.kt", "pets/moki/moki_sheet_v1.json"),
    "yuki": ("PetBehaviorFactory.kt", "pets/yuki/yuki_sheet_v1.json"),
    "piru": ("PetBehaviorFactory.kt", "pets/piru/piru_sheet_v1.json"),
    "taro": ("PetBehaviorFactory.kt", "pets/taro/taro_sheet_v1.json"),
    "menta": ("PetBehaviorFactory.kt", "pets/menta/menta_sheet_v1.json"),
}


def validate_raster_pet(name: str, behavior_name: str, prefix: str, indices: list[int]) -> int:
    behavior = (BEHAVIORS / behavior_name).read_text(encoding="utf-8")
    expected = {f"{prefix}_{index}.png" for index in indices}
    actual = {path.name for path in DRAWABLES.glob(f"{prefix}_*.png")}
    if actual != expected:
        raise ValueError(f"{name}: production frames differ; missing={sorted(expected - actual)}, extra={sorted(actual - expected)}")
    hashes: dict[str, str] = {}
    for filename in sorted(expected):
        path = DRAWABLES / filename
        stem = path.stem
        if f"R.drawable.{stem}" not in behavior:
            raise ValueError(f"{name}: {filename} is not referenced by {behavior_name}")
        image = Image.open(path).convert("RGBA")
        if image.getchannel("A").getbbox() is None:
            raise ValueError(f"{name}: {filename} is empty")
        digest = hashlib.sha256(image.tobytes()).hexdigest()
        duplicate = hashes.get(digest)
        if duplicate is not None:
            raise ValueError(f"{name}: exact duplicate frames {duplicate} and {filename}")
        hashes[digest] = filename
    return len(expected)


def validate_atlas(name: str, behavior_name: str, spec_path: str) -> int:
    behavior = (BEHAVIORS / behavior_name).read_text(encoding="utf-8")
    if spec_path not in behavior:
        raise ValueError(f"{name}: {behavior_name} does not load {spec_path}")
    spec_file = ROOT / "app/src/main/assets" / spec_path
    spec = json.loads(spec_file.read_text(encoding="utf-8"))
    atlas_file = ROOT / "app/src/main/assets" / str(spec["atlasPath"])
    atlas = Image.open(atlas_file).convert("RGBA")
    width = int(spec["frameWidth"])
    height = int(spec["frameHeight"])
    columns = int(spec["columns"])
    rows = int(spec["rows"])
    count = int(spec["frameCount"])
    if atlas.size != (columns * width, rows * height):
        raise ValueError(f"{name}: invalid atlas size {atlas.size}")
    indices = [int(frame["index"]) for frame in spec["frames"]]
    if indices != list(range(count)):
        raise ValueError(f"{name}: non-contiguous frame metadata {indices}")
    used = {
        int(frame)
        for clip in spec["clips"]
        for frame in clip["frames"]
    }
    if used != set(range(count)):
        raise ValueError(f"{name}: unused atlas frames {sorted(set(range(count)) - used)}")
    for index in range(count):
        col, row = index % columns, index // columns
        cell = atlas.crop((col * width, row * height, (col + 1) * width, (row + 1) * height))
        if cell.getchannel("A").getbbox() is None:
            raise ValueError(f"{name}: frame {index} is empty")
    return count


def main() -> int:
    total = 0
    for name, (behavior, prefix, indices) in RASTER_PETS.items():
        count = validate_raster_pet(name, behavior, prefix, indices)
        total += count
        print(f"{name}: {count} raster frames OK")
    for name, (behavior, spec) in ATLASES.items():
        count = validate_atlas(name, behavior, spec)
        total += count
        print(f"{name}: {count} atlas frames OK")
    print(f"All pet assets OK: {total} production frames, no orphaned or exact duplicate frames")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
