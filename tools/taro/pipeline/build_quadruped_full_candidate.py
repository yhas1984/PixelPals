#!/usr/bin/env python3
"""Build Taro's runtime candidate with quadruped locomotion and playful social frames."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from collections import deque
from copy import deepcopy
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))

from pet_pipeline import validate_atlas, write_previews


WALK_SHEET = ROOT / "tools/taro/pipeline/raw/11_walk_quadruped_candidate_01.png"
CALM_SHEET = ROOT / "tools/taro/pipeline/raw/12_quadruped_idle_turn_curiosity_sleep_01.png"
SOCIAL_SHEET = ROOT / "tools/taro/pipeline/raw/13_quadruped_hide_peek_touch_social_01.png"
PLAYFUL_SHEET = ROOT / "tools/taro/taro_atlas_preview.png"
BASE_ATLAS = ROOT / "app/src/main/assets/pets/taro/taro_motion_v2.png"
BASE_SPEC = ROOT / "app/src/main/assets/pets/taro/taro_motion_v2.json"
CANDIDATE_DIR = ROOT / "tools/taro/pipeline/candidates/quadruped_full_02"
FRAME_DIR = CANDIDATE_DIR / "frames"
PREVIEW_DIR = CANDIDATE_DIR / "previews"
CANDIDATE_ATLAS = CANDIDATE_DIR / "taro_motion_v2.png"
CANDIDATE_SPEC = CANDIDATE_DIR / "taro_motion_v2.json"
CANDIDATE_REPORT = CANDIDATE_DIR / "quality_report.json"
DEBUG_DIR = ROOT / "app/src/debug/assets/pets/taro"

FRAME_SIZE = 384
PADDING = 16
TARGET_MAX_SIZE = 320
WALK_SPEED_PIXELS_PER_SECOND = 42.0
LIGHT_BACKGROUND_MINIMUM = 220
LIGHT_BACKGROUND_SATURATION = 20

CALM_FRAME_ROWS = (
    (0, 1, 2, 3),
    (12, 13, 14, 15),
    (36, 37, 38, 39),
    (32, 33, 34, 35),
)
SOCIAL_FRAME_ROWS = (
    (16, 17, 18, 19),
    (20, 21, 22, 23),
    (28, 29, 30, 31),
    (24, 25, 26, 27),
)
PLAYFUL_RUNTIME_FRAMES = (24, 25, 26, 27)
PLAYFUL_SOURCE_CELLS = (8, 9, 10, 11)


def exterior_mask(matches_background: np.ndarray) -> np.ndarray:
    """Return background-like pixels connected to a cell border."""
    height, width = matches_background.shape
    exterior = np.zeros_like(matches_background, dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    def add(y: int, x: int) -> None:
        if matches_background[y, x] and not exterior[y, x]:
            exterior[y, x] = True
            queue.append((y, x))

    for x in range(width):
        add(0, x)
        add(height - 1, x)
    for y in range(height):
        add(y, 0)
        add(y, width - 1)
    while queue:
        y, x = queue.popleft()
        for delta_y in (-1, 0, 1):
            for delta_x in (-1, 0, 1):
                if delta_x == 0 and delta_y == 0:
                    continue
                next_y = y + delta_y
                next_x = x + delta_x
                if 0 <= next_y < height and 0 <= next_x < width:
                    add(next_y, next_x)
    return exterior


def largest_component(mask: np.ndarray) -> np.ndarray:
    """Keep the turtle and discard isolated checker or generation residue."""
    height, width = mask.shape
    visited = np.zeros_like(mask, dtype=bool)
    best: list[tuple[int, int]] = []
    for start_y, start_x in np.argwhere(mask):
        if visited[start_y, start_x]:
            continue
        component: list[tuple[int, int]] = []
        queue: deque[tuple[int, int]] = deque([(int(start_y), int(start_x))])
        visited[start_y, start_x] = True
        while queue:
            y, x = queue.popleft()
            component.append((y, x))
            for delta_y in (-1, 0, 1):
                for delta_x in (-1, 0, 1):
                    if delta_x == 0 and delta_y == 0:
                        continue
                    next_y = y + delta_y
                    next_x = x + delta_x
                    if 0 <= next_y < height and 0 <= next_x < width:
                        if mask[next_y, next_x] and not visited[next_y, next_x]:
                            visited[next_y, next_x] = True
                            queue.append((next_y, next_x))
        if len(component) > len(best):
            best = component
    result = np.zeros_like(mask, dtype=bool)
    for y, x in best:
        result[y, x] = True
    return result


def erode_one_pixel(mask: np.ndarray) -> np.ndarray:
    """Remove the RGB-matted fringe while retaining the interior silhouette."""
    padded = np.pad(mask, 1, constant_values=False)
    eroded = np.ones_like(mask, dtype=bool)
    for delta_y in range(3):
        for delta_x in range(3):
            eroded &= padded[
                delta_y : delta_y + mask.shape[0],
                delta_x : delta_x + mask.shape[1],
            ]
    return eroded


def remove_checker_background(cell: Image.Image) -> Image.Image:
    """Recover real alpha from image-generation checkerboard RGB."""
    rgb = np.asarray(cell.convert("RGB"), dtype=np.uint8)
    maximum = rgb.max(axis=2)
    minimum = rgb.min(axis=2)
    neutral_light = (
        (minimum >= LIGHT_BACKGROUND_MINIMUM)
        & ((maximum.astype(np.int16) - minimum.astype(np.int16)) <= LIGHT_BACKGROUND_SATURATION)
    )
    foreground = largest_component(~exterior_mask(neutral_light))
    clean_foreground = erode_one_pixel(foreground)
    if not clean_foreground.any():
        raise ValueError("Checker removal erased the complete subject")
    rgba = np.zeros((*rgb.shape[:2], 4), dtype=np.uint8)
    rgba[:, :, :3] = rgb
    rgba[:, :, 3] = clean_foreground.astype(np.uint8) * 255
    rgba[~clean_foreground, :3] = 0
    return Image.fromarray(rgba, "RGBA")


def tighten_alpha(cell: Image.Image) -> Image.Image:
    """Remove one remaining checker fringe from the supplied preview poses."""
    rgba = np.asarray(cell.convert("RGBA")).copy()
    mask = rgba[:, :, 3] > 8
    padded = np.pad(mask, 1, constant_values=False)
    eroded = np.ones_like(mask, dtype=bool)
    for delta_y in range(3):
        for delta_x in range(3):
            eroded &= padded[
                delta_y : delta_y + mask.shape[0],
                delta_x : delta_x + mask.shape[1],
            ]
    rgba[:, :, 3] = eroded.astype(np.uint8) * 255
    rgba[~eroded, :3] = 0
    return Image.fromarray(rgba, "RGBA")


def fixed_cells(sheet_path: Path, columns: int, rows: int, has_alpha: bool) -> list[Image.Image]:
    sheet = Image.open(sheet_path)
    cell_width = sheet.width // columns
    cell_height = sheet.height // rows
    cells: list[Image.Image] = []
    for row in range(rows):
        for column in range(columns):
            left = column * cell_width
            top = row * cell_height
            right = sheet.width if column == columns - 1 else (column + 1) * cell_width
            bottom = sheet.height if row == rows - 1 else (row + 1) * cell_height
            raw_cell = sheet.crop((left, top, right, bottom))
            cell = raw_cell.convert("RGBA") if has_alpha else remove_checker_background(raw_cell)
            rgba = np.asarray(cell).copy()
            rgba[rgba[:, :, 3] <= 8] = 0
            cell = Image.fromarray(rgba, "RGBA")
            bounds = cell.getchannel("A").getbbox()
            if bounds is None:
                raise ValueError(f"Empty cell in {sheet_path.name}: row={row} column={column}")
            cells.append(cell.crop(bounds))
    return cells


def normalize_group(cells: list[Image.Image], label: str) -> list[Image.Image]:
    """Use a common camera scale and contact line within a generated sheet."""
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
            raise ValueError(f"{label}_{index:02d}: padding would be {(x, y)}")
        frame.alpha_composite(resized, (x, y))
        rgba = np.asarray(frame).copy()
        rgba[rgba[:, :, 3] <= 8] = 0
        rgba[rgba[:, :, 3] == 0, :3] = 0
        frame = Image.fromarray(rgba, "RGBA")
        bounds = frame.getchannel("A").getbbox()
        if bounds is None:
            raise ValueError(f"{label}_{index:02d}: normalized frame is empty")
        margins = (bounds[0], bounds[1], FRAME_SIZE - bounds[2], FRAME_SIZE - bounds[3])
        if min(margins) < PADDING:
            raise ValueError(f"{label}_{index:02d}: padding={margins}")
        frames.append(frame)
    return frames


def all_frames() -> tuple[dict[int, Image.Image], dict[int, tuple[Path, int]]]:
    frames: dict[int, Image.Image] = {}
    sources: dict[int, tuple[Path, int]] = {}

    walk = normalize_group(fixed_cells(WALK_SHEET, 4, 2, has_alpha=True), "walk")
    for offset, frame in enumerate(walk):
        frames[4 + offset] = frame
        sources[4 + offset] = (WALK_SHEET, offset)

    for sheet_path, rows, label in (
        (CALM_SHEET, CALM_FRAME_ROWS, "calm"),
        (SOCIAL_SHEET, SOCIAL_FRAME_ROWS, "social"),
    ):
        group = normalize_group(fixed_cells(sheet_path, 4, 4, has_alpha=False), label)
        for row_index, frame_indices in enumerate(rows):
            for column_index, frame_index in enumerate(frame_indices):
                source_cell = row_index * 4 + column_index
                frames[frame_index] = group[source_cell]
                sources[frame_index] = (sheet_path, source_cell)

    playful = fixed_cells(PLAYFUL_SHEET, 4, 4, has_alpha=False)
    playful_group = normalize_group(
        [tighten_alpha(playful[index]) for index in PLAYFUL_SOURCE_CELLS],
        "playful_front",
    )
    for frame_index, source_cell, frame in zip(
        PLAYFUL_RUNTIME_FRAMES,
        PLAYFUL_SOURCE_CELLS,
        playful_group,
    ):
        frames[frame_index] = frame
        sources[frame_index] = (PLAYFUL_SHEET, source_cell)

    if set(frames) != set(range(40)):
        raise ValueError(f"Quadruped candidate does not cover frames: {sorted(set(range(40)) - set(frames))}")
    return frames, sources


def pack_atlas(frames: dict[int, Image.Image]) -> Image.Image:
    base = Image.open(BASE_ATLAS).convert("RGBA")
    atlas = Image.new("RGBA", base.size, (0, 0, 0, 0))
    for index, frame in frames.items():
        atlas.alpha_composite(frame, ((index % 8) * FRAME_SIZE, (index // 8) * FRAME_SIZE))
    return atlas


def build_spec(sources: dict[int, tuple[Path, int]]) -> dict[str, object]:
    spec = deepcopy(json.loads(BASE_SPEC.read_text(encoding="utf-8")))
    spec["atlasPath"] = "pets/taro/taro_motion_v2.png"
    render_hints = spec.setdefault("renderHints", {})
    if isinstance(render_hints, dict):
        render_hints["posture"] = "quadruped_with_front_playful_social"
        render_hints["walkPosture"] = "quadruped"
        render_hints["useFrameOccupancyNormalization"] = False
        render_hints["backgroundRemoval"] = "checker_exterior_flood_and_fringe_erosion"
    walk_clip = next(
        clip for clip in spec["clips"] if isinstance(clip, dict) and clip.get("id") == "walk"
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
        {"clip": clip["id"], "bottomTolerance": 3, "centerTolerance": 3}
        for clip in spec["clips"]
        if isinstance(clip, dict)
    ]
    # Older Taro manifests carry both ``frames`` (the runtime contract) and
    # ``frameDetails`` (pipeline provenance). Keep both in lockstep so an
    # approved candidate can always be traced back to its actual pose sheet.
    for details_key in ("frames", "frameDetails"):
        details = spec.get(details_key, [])
        if isinstance(details, list):
            for index, frame in enumerate(details):
                if isinstance(frame, dict):
                    source_path, source_cell = sources[index]
                    frame["poseClass"] = (
                        "playful_front" if index in PLAYFUL_RUNTIME_FRAMES else "quadruped"
                    )
                    frame["source"] = str(source_path.relative_to(ROOT))
                    frame["sourceCell"] = source_cell
    return spec


def write_contact_preview(frames: dict[int, Image.Image]) -> None:
    preview = Image.new("RGBA", (FRAME_SIZE * 8, FRAME_SIZE * 5), (255, 0, 180, 255))
    draw = ImageDraw.Draw(preview)
    for index, frame in frames.items():
        left = (index % 8) * FRAME_SIZE
        top = (index // 8) * FRAME_SIZE
        preview.alpha_composite(frame, (left, top))
        draw.line(
            (left, top + FRAME_SIZE - PADDING, left + FRAME_SIZE, top + FRAME_SIZE - PADDING),
            fill=(0, 0, 0, 255),
            width=2,
        )
        draw.text((left + 8, top + 8), str(index), fill=(0, 0, 0, 255))
    preview.convert("RGB").save(CANDIDATE_DIR / "all_contact_preview.png", optimize=True)


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
    frames, sources = all_frames()
    atlas = pack_atlas(frames)
    spec = build_spec(sources)
    atlas.save(CANDIDATE_ATLAS, optimize=True)
    CANDIDATE_SPEC.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")

    for index in range(40):
        name = str(spec["frames"][index]["name"])
        frames[index].save(FRAME_DIR / f"taro_{index:02d}_{name}.png", optimize=True)

    report = validate_atlas(CANDIDATE_ATLAS, CANDIDATE_SPEC)
    CANDIDATE_REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if not report["passed"]:
        print(json.dumps(report, indent=2))
        return 1

    write_previews(CANDIDATE_ATLAS, CANDIDATE_SPEC, PREVIEW_DIR)
    write_contact_preview(frames)
    if args.publish_debug:
        DEBUG_DIR.mkdir(parents=True, exist_ok=True)
        shutil.copy2(CANDIDATE_ATLAS, DEBUG_DIR / "taro_motion_v2.png")
        shutil.copy2(CANDIDATE_SPEC, DEBUG_DIR / "taro_motion_v2.json")
    print(
        "TARO_FULL_QUADRUPED_CANDIDATE_OK "
        f"frames={len(frames)} publishedDebug={args.publish_debug} "
        f"output={CANDIDATE_DIR.relative_to(ROOT)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
