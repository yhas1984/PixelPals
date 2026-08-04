#!/usr/bin/env python3
"""Validate Ginger atlas dimensions, clips, alpha, and frame coverage."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
ATLAS_PATH = ROOT / "app/src/main/assets/pets/ginger/ginger_sheet_v2.png"
SPEC_PATH = ROOT / "app/src/main/assets/pets/ginger/ginger_sheet_v2.json"
PREVIEW_PATH = ROOT / "app/src/main/res/drawable-nodpi/pet_ginger.png"
MIN_MARGIN = 8


def main() -> int:
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    atlas = Image.open(ATLAS_PATH).convert("RGBA")
    width = int(spec["frameWidth"])
    height = int(spec["frameHeight"])
    columns = int(spec["columns"])
    rows = int(spec["rows"])
    count = int(spec["frameCount"])
    expected_size = (columns * width, rows * height)
    if atlas.size != expected_size:
        raise ValueError(f"Atlas size {atlas.size} does not match {expected_size}")
    indices = [int(frame["index"]) for frame in spec["frames"]]
    if indices != list(range(count)):
        raise ValueError(f"Non-contiguous frame indices: {indices}")
    used: set[int] = set()
    for clip in spec["clips"]:
        clip_frames = [int(frame) for frame in clip["frames"]]
        if not clip_frames or any(frame not in range(count) for frame in clip_frames):
            raise ValueError(f"Invalid clip {clip['id']}: {clip_frames}")
        used.update(clip_frames)
    if used != set(range(count)):
        raise ValueError(f"Unused frames: {sorted(set(range(count)) - used)}")
    for index in range(count):
        col, row = index % columns, index // columns
        cell = atlas.crop((col * width, row * height, (col + 1) * width, (row + 1) * height))
        bounds = cell.getchannel("A").getbbox()
        if bounds is None:
            raise ValueError(f"Frame {index} is empty")
        left, top, right, bottom = bounds
        margins = (left, top, width - right, height - bottom)
        if min(margins) < MIN_MARGIN:
            raise ValueError(f"Frame {index} lacks padding: {margins}")
    image_preview = Image.open(PREVIEW_PATH).convert("RGBA")
    if image_preview.size != (512, 512) or image_preview.getchannel("A").getbbox() is None:
        raise ValueError("Ginger preview is invalid")
    print(f"Ginger atlas OK: {count} frames, {len(spec['clips'])} clips, {atlas.size[0]}x{atlas.size[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
