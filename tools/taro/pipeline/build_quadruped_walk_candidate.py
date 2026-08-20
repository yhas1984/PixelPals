#!/usr/bin/env python3
"""Build Taro's quadruped walk candidate without touching production assets."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from copy import deepcopy
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))

from pet_pipeline import validate_atlas, write_previews


SOURCE_SHEET = ROOT / "tools/taro/pipeline/raw/11_walk_quadruped_candidate_01.png"
BASE_ATLAS = ROOT / "app/src/main/assets/pets/taro/taro_motion_v2.png"
BASE_SPEC = ROOT / "app/src/main/assets/pets/taro/taro_motion_v2.json"
CANDIDATE_DIR = ROOT / "tools/taro/pipeline/candidates/quadruped_walk_01"
FRAME_DIR = CANDIDATE_DIR / "frames"
PREVIEW_DIR = CANDIDATE_DIR / "previews"
CANDIDATE_ATLAS = CANDIDATE_DIR / "taro_motion_v2.png"
CANDIDATE_SPEC = CANDIDATE_DIR / "taro_motion_v2.json"
CANDIDATE_REPORT = CANDIDATE_DIR / "quality_report.json"
DEBUG_DIR = ROOT / "app/src/debug/assets/pets/taro"

FRAME_SIZE = 384
GRID_COLUMNS = 4
GRID_ROWS = 2
FIRST_WALK_FRAME = 4
WALK_FRAME_COUNT = 8
PADDING = 16
TARGET_MAX_SIZE = 320
WALK_SPEED_PIXELS_PER_SECOND = 42.0


def source_cells(sheet: Image.Image) -> list[Image.Image]:
    """Extract the fixed 4x2 pose sheet without guessing segmentation."""
    cell_width = sheet.width // GRID_COLUMNS
    cell_height = sheet.height // GRID_ROWS
    cells: list[Image.Image] = []
    for row in range(GRID_ROWS):
        for column in range(GRID_COLUMNS):
            left = column * cell_width
            top = row * cell_height
            right = sheet.width if column == GRID_COLUMNS - 1 else (column + 1) * cell_width
            bottom = sheet.height if row == GRID_ROWS - 1 else (row + 1) * cell_height
            cell = sheet.crop((left, top, right, bottom)).convert("RGBA")
            rgba = np.asarray(cell).copy()
            rgba[rgba[:, :, 3] <= 8] = 0
            cell = Image.fromarray(rgba, "RGBA")
            bounds = cell.getchannel("A").getbbox()
            if bounds is None:
                raise ValueError(f"Empty source cell row={row} column={column}")
            cells.append(cell.crop(bounds))
    if len(cells) != WALK_FRAME_COUNT:
        raise ValueError(f"Expected {WALK_FRAME_COUNT} source cells, got {len(cells)}")
    return cells


def normalize_cells(cells: list[Image.Image]) -> list[Image.Image]:
    """Keep one scale, pivot and contact line across the full walk cycle."""
    source_extent = max(max(cell.width, cell.height) for cell in cells)
    common_scale = TARGET_MAX_SIZE / source_extent
    frames: list[Image.Image] = []
    for index, cell in enumerate(cells):
        resized = cell.resize(
            (
                max(1, round(cell.width * common_scale)),
                max(1, round(cell.height * common_scale)),
            ),
            Image.Resampling.LANCZOS,
        )
        frame = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
        x = (FRAME_SIZE - resized.width) // 2
        y = FRAME_SIZE - PADDING - resized.height
        if x < PADDING or y < PADDING:
            raise ValueError(f"walk_{index:02d}: padding would be {(x, y)}")
        frame.alpha_composite(resized, (x, y))
        rgba = np.asarray(frame).copy()
        rgba[rgba[:, :, 3] == 0, :3] = 0
        frame = Image.fromarray(rgba, "RGBA")
        bounds = frame.getchannel("A").getbbox()
        if bounds is None:
            raise ValueError(f"walk_{index:02d}: normalized frame is empty")
        margins = (bounds[0], bounds[1], FRAME_SIZE - bounds[2], FRAME_SIZE - bounds[3])
        if min(margins) < PADDING:
            raise ValueError(f"walk_{index:02d}: padding={margins}")
        frames.append(frame)
    return frames


def replace_walk_frames(base: Image.Image, walk_frames: list[Image.Image]) -> Image.Image:
    atlas = base.copy().convert("RGBA")
    for offset, frame in enumerate(walk_frames):
        index = FIRST_WALK_FRAME + offset
        left = (index % 8) * FRAME_SIZE
        top = (index // 8) * FRAME_SIZE
        atlas.paste((0, 0, 0, 0), (left, top, left + FRAME_SIZE, top + FRAME_SIZE))
        atlas.alpha_composite(frame, (left, top))
    return atlas


def candidate_spec() -> dict[str, object]:
    spec = deepcopy(json.loads(BASE_SPEC.read_text(encoding="utf-8")))
    spec["atlasPath"] = "pets/taro/taro_motion_v2.png"
    render_hints = spec.setdefault("renderHints", {})
    if isinstance(render_hints, dict):
        render_hints["walkPosture"] = "quadruped"
        render_hints["useFrameOccupancyNormalization"] = False
    walk_clip = next(
        clip
        for clip in spec["clips"]
        if isinstance(clip, dict) and clip.get("id") == "walk"
    )
    cycle_seconds = len(walk_clip["frames"]) * int(walk_clip["frameDurationMs"]) / 1000.0
    spec["locomotion"] = {
        "walk": {
            "posture": "quadruped",
            "cycleDisplacementPx": WALK_SPEED_PIXELS_PER_SECOND * cycle_seconds,
            "plantFrames": [4, 6, 8, 10],
        }
    }
    spec["contactAnchors"] = [
        {
            "clip": "walk",
            "bottomTolerance": 3,
            "centerTolerance": 3,
        }
    ]
    details = spec.get("frames", [])
    if isinstance(details, list):
        for index in range(FIRST_WALK_FRAME, FIRST_WALK_FRAME + WALK_FRAME_COUNT):
            frame = details[index]
            if isinstance(frame, dict):
                frame["poseClass"] = "quadruped"
                frame["source"] = str(SOURCE_SHEET.relative_to(ROOT))
                frame["sourceCell"] = index - FIRST_WALK_FRAME
    return spec


def write_contact_preview(frames: list[Image.Image]) -> None:
    width = FRAME_SIZE * len(frames)
    preview = Image.new("RGBA", (width, FRAME_SIZE), (255, 0, 180, 255))
    draw = ImageDraw.Draw(preview)
    for index, frame in enumerate(frames):
        left = index * FRAME_SIZE
        preview.alpha_composite(frame, (left, 0))
        draw.line((left, FRAME_SIZE - PADDING, left + FRAME_SIZE, FRAME_SIZE - PADDING), fill=(0, 0, 0, 255), width=2)
        draw.text((left + 8, 8), str(index), fill=(0, 0, 0, 255))
    preview.convert("RGB").save(CANDIDATE_DIR / "walk_contact_preview.png", optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--publish-debug",
        action="store_true",
        help="copy the validated candidate to src/debug only",
    )
    args = parser.parse_args()

    CANDIDATE_DIR.mkdir(parents=True, exist_ok=True)
    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    source = Image.open(SOURCE_SHEET).convert("RGBA")
    walk_frames = normalize_cells(source_cells(source))
    atlas = replace_walk_frames(Image.open(BASE_ATLAS), walk_frames)
    atlas.save(CANDIDATE_ATLAS, optimize=True)
    spec = candidate_spec()
    CANDIDATE_SPEC.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")

    for index in range(40):
        left = (index % 8) * FRAME_SIZE
        top = (index // 8) * FRAME_SIZE
        frame = atlas.crop((left, top, left + FRAME_SIZE, top + FRAME_SIZE))
        name = str(spec["frames"][index]["name"])
        frame.save(FRAME_DIR / f"taro_{index:02d}_{name}.png", optimize=True)

    report = validate_atlas(CANDIDATE_ATLAS, CANDIDATE_SPEC)
    CANDIDATE_REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if not report["passed"]:
        print(json.dumps(report, indent=2))
        return 1

    write_previews(CANDIDATE_ATLAS, CANDIDATE_SPEC, PREVIEW_DIR)
    write_contact_preview(walk_frames)
    if args.publish_debug:
        DEBUG_DIR.mkdir(parents=True, exist_ok=True)
        shutil.copy2(CANDIDATE_ATLAS, DEBUG_DIR / "taro_motion_v2.png")
        shutil.copy2(CANDIDATE_SPEC, DEBUG_DIR / "taro_motion_v2.json")
    print(
        "TARO_QUADRUPED_CANDIDATE_OK "
        f"frames={WALK_FRAME_COUNT} publishedDebug={args.publish_debug} "
        f"output={CANDIDATE_DIR.relative_to(ROOT)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
