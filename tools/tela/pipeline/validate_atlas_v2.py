#!/usr/bin/env python3
"""Validate Tela V2 atlas geometry and clip contract."""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
from pet_pipeline import validate_atlas


ATLAS = ROOT / "tools/tela/pipeline/atlas_v2/tela_motion_v2.png"
SPEC = ROOT / "tools/tela/pipeline/atlas_v2/tela_motion_v2.json"
EXPECTED_CLIPS = {"idle", "walk", "climb", "ceiling", "web_descend", "web_hang", "web_ascend", "land_touch", "sleep"}


def main() -> int:
    report = validate_atlas(ATLAS, SPEC)
    if not report["passed"]:
        print(json.dumps(report, indent=2))
        return 1
    image = Image.open(ATLAS).convert("RGBA")
    spec = json.loads(SPEC.read_text(encoding="utf-8"))
    assert image.size == (3072, 1920), image.size
    assert spec["frameCount"] == 40
    assert spec["frameWidth"] == 384 and spec["frameHeight"] == 384
    assert spec["columns"] == 8 and spec["rows"] == 5
    assert EXPECTED_CLIPS.issubset({clip["id"] for clip in spec["clips"]})
    assert all(len(clip["frames"]) > 0 for clip in spec["clips"])
    for index in range(40):
        row, col = divmod(index, 8)
        frame = image.crop((col * 384, row * 384, (col + 1) * 384, (row + 1) * 384))
        bbox = frame.getchannel("A").getbbox()
        assert bbox is not None, index
        assert min(bbox[0], bbox[1], 384 - bbox[2], 384 - bbox[3]) >= 16, (index, bbox)
        assert all(
            alpha != 0 or (red, green, blue) == (0, 0, 0)
            for red, green, blue, alpha in frame.getdata()
        ), f"non-black RGB in transparent pixels: frame {index}"
    print("TELA_V2_ATLAS_OK frames=40 size=3072x1920")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
