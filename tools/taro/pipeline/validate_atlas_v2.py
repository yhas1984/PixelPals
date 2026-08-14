#!/usr/bin/env python3
"""Validate the Taro V2 atlas contract and frame coverage."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
ATLAS_PATH = ROOT / "tools/taro/pipeline/atlas_v2/taro_motion_v2.png"
SPEC_PATH = ROOT / "tools/taro/pipeline/atlas_v2/taro_motion_v2.json"
FRAME_DIR = ROOT / "tools/taro/pipeline/atlas_v2/frames"

REQUIRED_CLIPS = {
    "idle",
    "walk",
    "turn",
    "hide",
    "peek",
    "front_social",
    "touch",
    "sleep",
    "curiosity",
}


def main() -> int:
    atlas = Image.open(ATLAS_PATH).convert("RGBA")
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    assert atlas.size == (3072, 1920), atlas.size
    assert spec["frameCount"] == 40, spec["frameCount"]
    assert spec["frameWidth"] == 384 and spec["frameHeight"] == 384
    assert spec["pivot"] == {"x": 192, "y": 368}
    assert len(spec["frames"]) == 40
    assert len(list(FRAME_DIR.glob("taro_*.png"))) == 40
    assert {clip["id"] for clip in spec["clips"]} == REQUIRED_CLIPS

    indices = [int(frame["index"]) for frame in spec["frames"]]
    assert indices == list(range(40)), indices
    used = {int(frame) for clip in spec["clips"] for frame in clip["frames"]}
    assert used == set(range(40)), sorted(set(range(40)) - used)

    for frame in spec["frames"]:
        index = int(frame["index"])
        left = (index % 8) * 384
        top = (index // 8) * 384
        cell = atlas.crop((left, top, left + 384, top + 384))
        bounds = cell.getchannel("A").getbbox()
        assert bounds is not None, frame
        margins = (bounds[0], bounds[1], 384 - bounds[2], 384 - bounds[3])
        assert min(margins) >= 16, (frame, margins)

    print(f"TARO_V2_ATLAS_OK frames={spec['frameCount']} size={atlas.width}x{atlas.height}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
