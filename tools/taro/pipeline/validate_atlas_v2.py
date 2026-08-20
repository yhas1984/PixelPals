#!/usr/bin/env python3
"""Validate the Taro V2 atlas contract and frame coverage."""

from __future__ import annotations

import json
import hashlib
import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
from pet_pipeline import validate_atlas
ATLAS_PATH = ROOT / "tools/taro/pipeline/atlas_v2/taro_motion_v2.png"
SPEC_PATH = ROOT / "tools/taro/pipeline/atlas_v2/taro_motion_v2.json"
DEBUG_ATLAS_PATH = ROOT / "app/src/debug/assets/pets/taro/taro_motion_v2.png"
DEBUG_SPEC_PATH = ROOT / "app/src/debug/assets/pets/taro/taro_motion_v2.json"
MAIN_ATLAS_PATH = ROOT / "app/src/main/assets/pets/taro/taro_motion_v2.png"
MAIN_SPEC_PATH = ROOT / "app/src/main/assets/pets/taro/taro_motion_v2.json"
FRAME_DIR = ROOT / "tools/taro/pipeline/atlas_v2/frames"
WALK_CANDIDATE_DIR = ROOT / "tools/taro/pipeline/candidates/quadruped_walk_01"
FULL_CANDIDATE_DIR = ROOT / "tools/taro/pipeline/candidates/quadruped_full_02"
FULL_CANDIDATE_SOURCE = FULL_CANDIDATE_DIR / "source.json"

REQUIRED_CLIPS = {
    "idle",
    "idle_front",
    "walk",
    "turn",
    "hide",
    "peek",
    "front_social",
    "playful_wave",
    "playful_delight",
    "playful_surprise",
    "touch",
    "sleep",
    "curiosity",
}


def assert_runtime_assets_match_expected_variant() -> None:
    promoted_atlas = (FULL_CANDIDATE_DIR / "taro_motion_v2.png").read_bytes()
    promoted_spec = (FULL_CANDIDATE_DIR / "taro_motion_v2.json").read_bytes()
    for runtime_atlas in (ATLAS_PATH, DEBUG_ATLAS_PATH, MAIN_ATLAS_PATH):
        assert runtime_atlas.read_bytes() == promoted_atlas, runtime_atlas
    for runtime_spec in (SPEC_PATH, DEBUG_SPEC_PATH, MAIN_SPEC_PATH):
        assert runtime_spec.read_bytes() == promoted_spec, runtime_spec


def main() -> int:
    assert_runtime_assets_match_expected_variant()
    report = validate_atlas(ATLAS_PATH, SPEC_PATH)
    if not report["passed"]:
        print(json.dumps(report, indent=2))
        return 1
    atlas = Image.open(ATLAS_PATH).convert("RGBA")
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    assert atlas.size == (3072, 1920), atlas.size
    assert spec["frameCount"] == 40, spec["frameCount"]
    assert spec["frameWidth"] == 384 and spec["frameHeight"] == 384
    assert spec["pivot"] == {"x": 192, "y": 368}
    assert len(spec["frames"]) == 40
    assert len(list(FRAME_DIR.glob("taro_*.png"))) == 40
    assert {clip["id"] for clip in spec["clips"]} == REQUIRED_CLIPS
    playful_clips = {
        clip["id"]: clip
        for clip in spec["clips"]
        if str(clip["id"]).startswith("playful_")
    }
    assert playful_clips == {
        "playful_wave": {
            "id": "playful_wave",
            "frames": [24, 25, 25, 24],
            "loop": False,
            "frameDurationMs": 300,
        },
        "playful_delight": {
            "id": "playful_delight",
            "frames": [24, 26, 26, 24],
            "loop": False,
            "frameDurationMs": 300,
        },
        "playful_surprise": {
            "id": "playful_surprise",
            "frames": [24, 27, 27, 24],
            "loop": False,
            "frameDurationMs": 300,
        },
    }

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

    for candidate_dir in (WALK_CANDIDATE_DIR, FULL_CANDIDATE_DIR):
        candidate_atlas = candidate_dir / "taro_motion_v2.png"
        candidate_spec_path = candidate_dir / "taro_motion_v2.json"
        if not candidate_atlas.exists():
            continue
        candidate_report = validate_atlas(candidate_atlas, candidate_spec_path)
        assert candidate_report["passed"], candidate_report["violations"]
        candidate_spec = json.loads(candidate_spec_path.read_text(encoding="utf-8"))
        assert candidate_spec["renderHints"]["walkPosture"] == "quadruped"
        expected_frames = range(40) if candidate_dir == FULL_CANDIDATE_DIR else range(4, 12)
        for index in expected_frames:
            expected_pose = (
                "playful_front"
                if candidate_dir == FULL_CANDIDATE_DIR and index in range(24, 28)
                else "quadruped"
            )
            assert candidate_spec["frames"][index]["poseClass"] == expected_pose
        if candidate_dir == FULL_CANDIDATE_DIR:
            assert candidate_spec["renderHints"]["posture"] == "quadruped_with_front_playful_social"
            assert len(candidate_spec["frameDetails"]) == 40
            for index in range(40):
                runtime_frame = candidate_spec["frames"][index]
                provenance_frame = candidate_spec["frameDetails"][index]
                expected_pose = "playful_front" if index in range(24, 28) else "quadruped"
                assert provenance_frame["poseClass"] == expected_pose
                assert provenance_frame["source"] == runtime_frame["source"]
                assert provenance_frame["sourceCell"] == runtime_frame["sourceCell"]
            source = json.loads(FULL_CANDIDATE_SOURCE.read_text(encoding="utf-8"))
            assert source["candidate"]["pngSha256"] == hashlib.sha256(
                candidate_atlas.read_bytes()
            ).hexdigest()
            assert source["candidate"]["manifestSha256"] == hashlib.sha256(
                candidate_spec_path.read_bytes()
            ).hexdigest()

    print(
        f"TARO_V2_ATLAS_OK frames={spec['frameCount']} size={atlas.width}x{atlas.height} "
        f"quadrupedWalkCandidate={WALK_CANDIDATE_DIR.exists()} "
        f"quadrupedFullCandidate={FULL_CANDIDATE_DIR.exists()}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
