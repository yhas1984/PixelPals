#!/usr/bin/env python3
"""Validate the Lumi atlas extracted from the supplied 4x4 source sheet."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[4]
ATLAS_DIR = ROOT / "tools/lumi/archive/v1/source_atlas"
ATLAS_PATH = ATLAS_DIR / "lumi_sheet_source_v1.png"
SPEC_PATH = ATLAS_DIR / "lumi_sheet_source_v1.json"
REPORT_PATH = ATLAS_DIR / "lumi_source_report.json"
FRAME_SIZE = 384
FRAME_COUNT = 16
MINIMUM_PADDING = 16


def main() -> int:
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    report = json.loads(REPORT_PATH.read_text(encoding="utf-8"))
    atlas = Image.open(ATLAS_PATH).convert("RGBA")

    expected_size = (FRAME_SIZE * 4, FRAME_SIZE * 4)
    if atlas.size != expected_size:
        raise ValueError(f"Invalid atlas size {atlas.size}; expected {expected_size}")
    if int(spec["frameCount"]) != FRAME_COUNT:
        raise ValueError(f"Invalid frame count: {spec['frameCount']}")
    indexes = [int(frame["index"]) for frame in spec["frames"]]
    if indexes != list(range(FRAME_COUNT)):
        raise ValueError(f"Frame indexes are not contiguous: {indexes}")
    used = {int(frame) for clip in spec["clips"] for frame in clip["frames"]}
    if used != set(range(FRAME_COUNT)):
        raise ValueError(f"Unused frames: {sorted(set(range(FRAME_COUNT)) - used)}")
    if len(report["frames"]) != FRAME_COUNT:
        raise ValueError("Source report does not contain all frames")

    crossing_frames: list[int] = []
    for index in range(FRAME_COUNT):
        column, row = index % 4, index // 4
        cell = atlas.crop(
            (
                column * FRAME_SIZE,
                row * FRAME_SIZE,
                (column + 1) * FRAME_SIZE,
                (row + 1) * FRAME_SIZE,
            )
        )
        bounds = cell.getchannel("A").getbbox()
        if bounds is None:
            raise ValueError(f"Frame {index} is empty")
        margins = (bounds[0], bounds[1], FRAME_SIZE - bounds[2], FRAME_SIZE - bounds[3])
        if min(margins) < MINIMUM_PADDING:
            raise ValueError(f"Frame {index} lacks padding: {margins}")
        if any(report["frames"][index]["crossesNominalCellBounds"].values()):
            crossing_frames.append(index)

    print(
        f"LUMI_SOURCE_ATLAS_VALID atlas={atlas.size[0]}x{atlas.size[1]} "
        f"frames={FRAME_COUNT} padding>={MINIMUM_PADDING}px"
    )
    print(f"source_frames_crossing_nominal_rows={crossing_frames}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
