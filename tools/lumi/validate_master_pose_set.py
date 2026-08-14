#!/usr/bin/env python3
"""Validate Lumi master-pose review assets before manual approval."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
REVIEW_DIR = ROOT / "tools/lumi/archive/v1/review/master_pose_review_v2"
MANIFEST_PATH = REVIEW_DIR / "lumi_master_pose_review_v2.json"
FRAME_SIZE = 384
MINIMUM_PADDING = 16


def validate_frame(path: Path) -> dict[str, object]:
    image = Image.open(path)
    if image.size != (FRAME_SIZE, FRAME_SIZE):
        raise ValueError(f"{path.name}: expected {FRAME_SIZE}x{FRAME_SIZE}, got {image.size}")
    if image.mode != "RGBA":
        raise ValueError(f"{path.name}: expected RGBA, got {image.mode}")
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError(f"{path.name}: frame is empty")
    margins = (bounds[0], bounds[1], FRAME_SIZE - bounds[2], FRAME_SIZE - bounds[3])
    if min(margins) < MINIMUM_PADDING:
        raise ValueError(f"{path.name}: padding below {MINIMUM_PADDING}px: {margins}")
    return {"path": str(path), "bounds": list(bounds), "margins": list(margins)}


def main() -> int:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    technical = manifest["technicalContract"]
    if technical["frameWidth"] != FRAME_SIZE or technical["frameHeight"] != FRAME_SIZE:
        raise ValueError("Manifest frame size does not match validator contract")
    if technical["minimumTransparentPaddingPx"] != MINIMUM_PADDING:
        raise ValueError("Manifest padding does not match validator contract")
    results = []
    for index, candidate in enumerate(manifest["candidates"]):
        path = REVIEW_DIR / "frames" / f"lumi_{index:02d}_{candidate['id']}.png"
        results.append(validate_frame(path))
    statuses = {candidate["status"] for candidate in manifest["candidates"]}
    if manifest["promotionAllowed"]:
        raise ValueError("Review manifest cannot allow promotion")
    if "manual_paint_required" not in statuses or "candidate_needs_retouch" not in statuses:
        raise ValueError("Review manifest must distinguish missing art from retouch candidates")
    print(
        f"LUMI_MASTER_POSE_REVIEW_VALID frames={len(results)} "
        f"promotionAllowed={manifest['promotionAllowed']} statuses={sorted(statuses)}"
    )
    for result in results:
        print(f"{Path(result['path']).name}: margins={result['margins']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
