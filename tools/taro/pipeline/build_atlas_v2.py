#!/usr/bin/env python3
"""Clean Taro V2 boards, normalize frames, and build a review atlas."""

from __future__ import annotations

import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[3]
RAW_DIR = ROOT / "tools/taro/pipeline/raw"
OUTPUT_DIR = ROOT / "tools/taro/pipeline/atlas_v2"
FRAME_DIR = OUTPUT_DIR / "frames"
ATLAS_PATH = OUTPUT_DIR / "taro_motion_v2.png"
PREVIEW_PATH = OUTPUT_DIR / "taro_motion_v2_preview.png"
SPEC_PATH = OUTPUT_DIR / "taro_motion_v2.json"
DEBUG_DIR = ROOT / "app/src/debug/assets/pets/taro"
DEBUG_ATLAS_PATH = DEBUG_DIR / "taro_motion_v2.png"
DEBUG_SPEC_PATH = DEBUG_DIR / "taro_motion_v2.json"

FRAME_SIZE = 384
PADDING = 16
TARGET_VISIBLE_SIZE = 320
COLUMNS = 8
ROWS = 5
ALPHA_THRESHOLD = 12
BACKGROUND_DISTANCE = 64.0

FRAME_NAMES = (
    *(f"idle_{index:02d}" for index in range(4)),
    *(f"walk_{index:02d}" for index in range(8)),
    *(f"turn_{index:02d}" for index in range(4)),
    *(f"hide_{index:02d}" for index in range(4)),
    *(f"peek_{index:02d}" for index in range(4)),
    *(f"front_social_{index:02d}" for index in range(4)),
    *(f"touch_{index:02d}" for index in range(4)),
    *(f"sleep_{index:02d}" for index in range(4)),
    *(f"curiosity_{index:02d}" for index in range(4)),
)

BOARD_NAMES = (
    "01_idle_breathe",
    "02_walk_a",
    "03_walk_b",
    "04_turn_in_place",
    "05_hide_shell",
    "06_peek_out",
    "07_front_social",
    "08_touch_recover",
    "09_sleep",
    "10_garden_curiosity",
)


def get_border_reference(rgb: np.ndarray) -> np.ndarray:
    border = np.concatenate(
        (
            rgb[:8].reshape(-1, 3),
            rgb[-8:].reshape(-1, 3),
            rgb[:, :8].reshape(-1, 3),
            rgb[:, -8:].reshape(-1, 3),
        )
    )
    return np.median(border, axis=0).astype(np.float32)


def get_exterior_mask(matches_background: np.ndarray) -> np.ndarray:
    height, width = matches_background.shape
    exterior = np.zeros((height, width), dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    def add_pixel(y: int, x: int) -> None:
        if matches_background[y, x] and not exterior[y, x]:
            exterior[y, x] = True
            queue.append((y, x))

    for x in range(width):
        add_pixel(0, x)
        add_pixel(height - 1, x)
    for y in range(height):
        add_pixel(y, 0)
        add_pixel(y, width - 1)
    while queue:
        y, x = queue.popleft()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                ny, nx = y + dy, x + dx
                if 0 <= ny < height and 0 <= nx < width:
                    add_pixel(ny, nx)
    return exterior


def remove_background(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    rgb = rgba[:, :, :3].astype(np.float32)
    background = get_border_reference(rgb)
    distance = np.linalg.norm(rgb - background, axis=2)
    exterior = get_exterior_mask(distance < BACKGROUND_DISTANCE)
    rgba[exterior, 3] = 0
    rgba[rgba[:, :, 3] == 0, :3] = 0
    return Image.fromarray(rgba, "RGBA")


def extract_cells(board: Image.Image) -> list[Image.Image]:
    """Use the fixed 2x2 layout so shell and flippers stay in one pose crop."""
    cleaned = remove_background(board)
    cell_width = board.width // 2
    cell_height = board.height // 2
    cells: list[Image.Image] = []
    for row in range(2):
        for col in range(2):
            left = col * cell_width
            top = row * cell_height
            right = (col + 1) * cell_width
            bottom = (row + 1) * cell_height
            cell = cleaned.crop((left, top, right, bottom))
            bounds = cell.getchannel("A").getbbox()
            if bounds is None:
                raise ValueError(f"Empty Taro board cell row={row} col={col}")
            cells.append(cell.crop(bounds))
    return cells


def normalize(crop: Image.Image, frame_name: str) -> Image.Image:
    bounds = crop.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError(f"Empty source for {frame_name}")
    crop = crop.crop(bounds)
    scale = TARGET_VISIBLE_SIZE / max(crop.width, crop.height)
    resized = crop.resize(
        (max(1, round(crop.width * scale)), max(1, round(crop.height * scale))),
        Image.Resampling.LANCZOS,
    )
    frame = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
    x = (FRAME_SIZE - resized.width) // 2
    y = FRAME_SIZE - PADDING - resized.height
    if x < PADDING or y < PADDING:
        raise ValueError(f"Padding violation before placement for {frame_name}: {(x, y)}")
    frame.alpha_composite(resized, (x, y))
    frame = remove_ground_shadow(frame)
    output_bounds = frame.getchannel("A").getbbox()
    if output_bounds is None:
        raise ValueError(f"Normalized frame is empty for {frame_name}")
    margins = (
        output_bounds[0],
        output_bounds[1],
        FRAME_SIZE - output_bounds[2],
        FRAME_SIZE - output_bounds[3],
    )
    if min(margins) < PADDING:
        raise ValueError(f"Padding violation for {frame_name}: {margins}")
    return frame


def remove_ground_shadow(frame: Image.Image) -> Image.Image:
    """Drop a baked neutral floor shadow without touching colored anatomy."""
    rgba = np.asarray(frame.convert("RGBA")).copy()
    rgb = rgba[:, :, :3].astype(np.int16)
    alpha = rgba[:, :, 3] > ALPHA_THRESHOLD
    saturation = rgb.max(axis=2) - rgb.min(axis=2)
    value = rgb.mean(axis=2)
    neutral = alpha & (saturation < 30) & (value < 245)
    neutral[: int(FRAME_SIZE * 0.72)] = False

    height, width = neutral.shape
    visited = np.zeros_like(neutral, dtype=bool)
    for sy, sx in zip(*np.where(neutral)):
        if visited[sy, sx]:
            continue
        stack = [(int(sy), int(sx))]
        visited[sy, sx] = True
        points: list[tuple[int, int]] = []
        while stack:
            y, x = stack.pop()
            points.append((y, x))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    if not dy and not dx:
                        continue
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < height and 0 <= nx < width and neutral[ny, nx] and not visited[ny, nx]:
                        visited[ny, nx] = True
                        stack.append((ny, nx))
        if len(points) < 50:
            continue
        ys = np.asarray([point[0] for point in points])
        xs = np.asarray([point[1] for point in points])
        component_width = int(xs.max() - xs.min() + 1)
        touches_floor = int(ys.max()) >= FRAME_SIZE - 4
        if component_width >= 45 and touches_floor:
            rgba[ys, xs, 3] = 0
            rgba[ys, xs, :3] = 0
    y_grid = np.indices((height, width))[0]
    residual_shadow = (
        (rgba[:, :, 3] > ALPHA_THRESHOLD)
        & (y_grid >= int(FRAME_SIZE * 0.84))
        & (saturation < 50)
    )
    rgba[residual_shadow, 3] = 0
    rgba[residual_shadow, :3] = 0
    return Image.fromarray(rgba, "RGBA")


def build_spec(atlas_path: str) -> dict[str, object]:
    return {
        "version": 2,
        "petId": "taro",
        "atlasPath": atlas_path,
        "previewPath": "",
        "frameWidth": FRAME_SIZE,
        "frameHeight": FRAME_SIZE,
        "columns": COLUMNS,
        "rows": ROWS,
        "frameCount": len(FRAME_NAMES),
        "pivot": {"x": FRAME_SIZE // 2, "y": FRAME_SIZE - PADDING},
        "renderHints": {
            "innerTransparentPaddingPx": PADDING,
            "recommendedBleedInsetPx": 1,
            "filterBitmap": True,
            "backgroundRemoval": "exterior_white_flood_fill",
            "sourceCellCompositionPreserved": True,
        },
        "clips": [
            {"id": "idle", "frames": list(range(0, 4)), "loop": True, "frameDurationMs": 900},
            {"id": "walk", "frames": list(range(4, 12)), "loop": True, "frameDurationMs": 420},
            {"id": "turn", "frames": list(range(12, 16)), "loop": False, "frameDurationMs": 420},
            {"id": "hide", "frames": list(range(16, 20)), "loop": False, "frameDurationMs": 650},
            {"id": "peek", "frames": list(range(20, 24)), "loop": False, "frameDurationMs": 520},
            {"id": "front_social", "frames": list(range(24, 28)), "loop": False, "frameDurationMs": 650},
            {"id": "touch", "frames": list(range(28, 32)), "loop": False, "frameDurationMs": 360},
            {"id": "sleep", "frames": list(range(32, 36)), "loop": True, "frameDurationMs": 1600},
            {"id": "curiosity", "frames": list(range(36, 40)), "loop": False, "frameDurationMs": 700},
        ],
        "frames": [{"index": index, "name": name} for index, name in enumerate(FRAME_NAMES)],
    }


def write_preview(atlas: Image.Image) -> None:
    gap = 8
    preview = Image.new(
        "RGBA",
        (COLUMNS * (FRAME_SIZE + gap) + gap, ROWS * (FRAME_SIZE + gap) + gap),
        (25, 28, 36, 255),
    )
    checker = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (230, 230, 236, 255))
    for y in range(0, FRAME_SIZE, 24):
        for x in range(0, FRAME_SIZE, 24):
            if (x // 24 + y // 24) % 2 == 0:
                checker.paste((255, 255, 255, 255), (x, y, x + 24, y + 24))
    draw = ImageDraw.Draw(preview)
    for index in range(len(FRAME_NAMES)):
        row = index // COLUMNS
        col = index % COLUMNS
        ox = gap + col * (FRAME_SIZE + gap)
        oy = gap + row * (FRAME_SIZE + gap)
        preview.alpha_composite(checker, (ox, oy))
        source = (
            (index % COLUMNS) * FRAME_SIZE,
            (index // COLUMNS) * FRAME_SIZE,
            ((index % COLUMNS) + 1) * FRAME_SIZE,
            ((index // COLUMNS) + 1) * FRAME_SIZE,
        )
        preview.alpha_composite(atlas.crop(source), (ox, oy))
        draw.text((ox + 4, oy + 2), str(index), fill=(230, 60, 60, 255))
    preview.convert("RGB").save(PREVIEW_PATH, optimize=True)


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    DEBUG_DIR.mkdir(parents=True, exist_ok=True)
    for path in FRAME_DIR.glob("taro_*.png"):
        path.unlink()

    frames: list[Image.Image] = []
    source_details: list[dict[str, object]] = []
    for board_name in BOARD_NAMES:
        path = RAW_DIR / f"{board_name}.png"
        if not path.exists():
            raise FileNotFoundError(path)
        cells = extract_cells(Image.open(path).convert("RGBA"))
        start = len(frames)
        for cell_index, cell in enumerate(cells):
            frame_name = FRAME_NAMES[start + cell_index]
            frame = normalize(cell, frame_name)
            frame.save(FRAME_DIR / f"taro_{start + cell_index:02d}_{frame_name}.png", optimize=True)
            frames.append(frame)
            source_details.append(
                {
                    "index": start + cell_index,
                    "name": frame_name,
                    "source": str(path.relative_to(ROOT)),
                    "sourceCell": cell_index,
                }
            )

    if len(frames) != len(FRAME_NAMES):
        raise ValueError(f"Expected {len(FRAME_NAMES)} frames, got {len(frames)}")

    atlas = Image.new("RGBA", (FRAME_SIZE * COLUMNS, FRAME_SIZE * ROWS), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % COLUMNS) * FRAME_SIZE, (index // COLUMNS) * FRAME_SIZE))
    atlas.save(ATLAS_PATH, optimize=True)
    write_preview(atlas)

    spec = build_spec("tools/taro/pipeline/atlas_v2/taro_motion_v2.png")
    spec["frameDetails"] = source_details
    SPEC_PATH.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
    debug_spec = build_spec("pets/taro/taro_motion_v2.png")
    DEBUG_SPEC_PATH.write_text(json.dumps(debug_spec, indent=2) + "\n", encoding="utf-8")
    atlas.save(DEBUG_ATLAS_PATH, optimize=True)
    print(f"TARO_V2_BUILT frames={len(frames)} atlas={ATLAS_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
