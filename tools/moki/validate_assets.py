#!/usr/bin/env python3
"""Validate the generated Moki atlas and metadata contract."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
ATLAS_PATH = ROOT / "app/src/debug/assets/pets/moki/moki_sheet_v1.png"
SPEC_PATH = ROOT / "app/src/debug/assets/pets/moki/moki_sheet_v1.json"
PREVIEW_PATH = ROOT / "app/src/debug/res/drawable-nodpi/pet_moki.png"
MINIMUM_MARGIN = 8


def validate_cell(atlas: Image.Image, index: int, frame_width: int, frame_height: int, columns: int) -> None:
    column = index % columns
    row = index // columns
    cell = atlas.crop(
        (
            column * frame_width,
            row * frame_height,
            (column + 1) * frame_width,
            (row + 1) * frame_height,
        )
    )
    bounds = cell.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError(f"Frame {index} is empty")
    left, top, right, bottom = bounds
    margins = (left, top, frame_width - right, frame_height - bottom)
    if min(margins) < MINIMUM_MARGIN:
        raise ValueError(f"Frame {index} is clipped or lacks padding: margins={margins}")


def main() -> int:
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    atlas = Image.open(ATLAS_PATH).convert("RGBA")
    frame_width = int(spec["frameWidth"])
    frame_height = int(spec["frameHeight"])
    columns = int(spec["columns"])
    rows = int(spec["rows"])
    frame_count = int(spec["frameCount"])
    expected_size = (columns * frame_width, rows * frame_height)
    if atlas.size != expected_size:
        raise ValueError(f"Atlas size {atlas.size} does not match {expected_size}")
    frame_indices = [int(frame["index"]) for frame in spec["frames"]]
    if frame_indices != list(range(frame_count)):
        raise ValueError(f"Frame indices are not contiguous: {frame_indices}")
    used_frames: set[int] = set()
    for clip in spec["clips"]:
        clip_frames = [int(frame) for frame in clip["frames"]]
        if not clip_frames or any(frame not in range(frame_count) for frame in clip_frames):
            raise ValueError(f"Invalid clip {clip['id']}: {clip_frames}")
        used_frames.update(clip_frames)
    if used_frames != set(range(frame_count)):
        raise ValueError(f"Unused frames: {sorted(set(range(frame_count)) - used_frames)}")
    for index in range(frame_count):
        validate_cell(atlas, index, frame_width, frame_height, columns)
    preview = Image.open(PREVIEW_PATH).convert("RGBA")
    if preview.size != (512, 512) or preview.getchannel("A").getbbox() is None:
        raise ValueError("Moki preview is invalid")
    print(f"Moki atlas OK: {frame_count} frames, {len(spec['clips'])} clips, {atlas.size[0]}x{atlas.size[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
