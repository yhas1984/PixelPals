#!/usr/bin/env python3
"""Normalize Tela V2 boards, build the 40-frame atlas, and write debug assets."""

from __future__ import annotations

import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[3]
RAW_DIR = ROOT / "tools/tela/pipeline/raw"
OUTPUT_DIR = ROOT / "tools/tela/pipeline/atlas_v2"
FRAME_DIR = OUTPUT_DIR / "frames"
ATLAS_PATH = OUTPUT_DIR / "tela_motion_v2.png"
PREVIEW_PATH = OUTPUT_DIR / "tela_motion_v2_preview.png"
SPEC_PATH = OUTPUT_DIR / "tela_motion_v2.json"
DEBUG_DIR = ROOT / "app/src/debug/assets/pets/tela"
DEBUG_ATLAS_PATH = DEBUG_DIR / "tela_motion_v2.png"
DEBUG_SPEC_PATH = DEBUG_DIR / "tela_motion_v2.json"

FRAME_SIZE = 384
PADDING = 16
TARGET_VISIBLE_SIZE = 320
COLUMNS = 8
ROWS = 5
BACKGROUND_DISTANCE = 64.0

BOARD_NAMES = (
    "01_idle_grounded",
    "02_floor_walk_a",
    "03_floor_walk_b",
    "04_wall_climb",
    "05_ceiling_crawl",
    "06_web_descend",
    "07_web_hang",
    "08_web_ascend",
    "09_land_touch",
    "10_sleep",
)

FRAME_GROUPS = (
    ("idle", "idle", 4),
    ("walk", "floor_walk", 8),
    ("climb", "climb", 4),
    ("ceiling", "ceiling", 4),
    ("web_descend", "web_descend", 4),
    ("web_hang", "web_hang", 4),
    ("web_ascend", "web_ascend", 4),
    ("land_touch", "land_touch", 4),
    ("sleep", "sleep", 4),
)

FRAME_NAMES = (
    *(f"idle_{i:02d}" for i in range(4)),
    *(f"walk_{i:02d}" for i in range(8)),
    *(f"climb_{i:02d}" for i in range(4)),
    *(f"ceiling_{i:02d}" for i in range(4)),
    *(f"web_descend_{i:02d}" for i in range(4)),
    *(f"web_hang_{i:02d}" for i in range(4)),
    *(f"web_ascend_{i:02d}" for i in range(4)),
    *(f"land_touch_{i:02d}" for i in range(4)),
    *(f"sleep_{i:02d}" for i in range(4)),
)


def remove_background(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    rgb = rgba[:, :, :3].astype(np.float32)
    border = np.concatenate((rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3), rgb[:, :8].reshape(-1, 3), rgb[:, -8:].reshape(-1, 3)))
    reference = np.median(border, axis=0)
    matches = np.linalg.norm(rgb - reference, axis=2) < BACKGROUND_DISTANCE
    exterior = np.zeros(matches.shape, dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    def visit(y: int, x: int) -> None:
        if matches[y, x] and not exterior[y, x]:
            exterior[y, x] = True
            queue.append((y, x))

    for x in range(matches.shape[1]):
        visit(0, x)
        visit(matches.shape[0] - 1, x)
    for y in range(matches.shape[0]):
        visit(y, 0)
        visit(y, matches.shape[1] - 1)
    while queue:
        y, x = queue.popleft()
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < matches.shape[0] and 0 <= nx < matches.shape[1]:
                visit(ny, nx)
    rgba[exterior, 3] = 0
    rgba[rgba[:, :, 3] == 0, :3] = 0
    return Image.fromarray(rgba, "RGBA")


def extract_cells(board: Image.Image) -> list[Image.Image]:
    cleaned = remove_background(board)
    cell_size = board.width // 2
    cells = []
    for row in range(2):
        for col in range(2):
            cell = cleaned.crop((col * cell_size, row * cell_size, (col + 1) * cell_size, (row + 1) * cell_size))
            bounds = cell.getchannel("A").getbbox()
            if bounds is None:
                raise ValueError(f"Empty Tela board cell row={row} col={col}")
            cells.append(cell.crop(bounds))
    return cells


def normalize(crop: Image.Image, name: str) -> Image.Image:
    bounds = crop.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError(f"Empty Tela source for {name}")
    crop = crop.crop(bounds)
    scale = TARGET_VISIBLE_SIZE / max(crop.width, crop.height)
    resized = crop.resize((max(1, round(crop.width * scale)), max(1, round(crop.height * scale))), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
    x = (FRAME_SIZE - resized.width) // 2
    y = FRAME_SIZE - PADDING - resized.height
    frame.alpha_composite(resized, (x, y))
    pixels = np.asarray(frame).copy()
    pixels[pixels[:, :, 3] == 0, :3] = 0
    frame = Image.fromarray(pixels, "RGBA")
    margins = frame.getchannel("A").getbbox()
    if margins is None or min(margins[0], margins[1], FRAME_SIZE - margins[2], FRAME_SIZE - margins[3]) < PADDING:
        raise ValueError(f"Padding violation for {name}: {margins}")
    return frame


def build_spec(atlas_path: str) -> dict[str, object]:
    clips = [
        {"id": "idle", "frames": list(range(0, 4)), "loop": True, "frameDurationMs": 500},
        {"id": "walk", "frames": list(range(4, 12)), "loop": True, "frameDurationMs": 180},
        {"id": "climb", "frames": list(range(12, 16)), "loop": True, "frameDurationMs": 220},
        {"id": "ceiling", "frames": list(range(16, 20)), "loop": True, "frameDurationMs": 220},
        {"id": "web_descend", "frames": list(range(20, 24)), "loop": False, "frameDurationMs": 260},
        {"id": "web_hang", "frames": list(range(24, 28)), "loop": True, "frameDurationMs": 420},
        {"id": "web_ascend", "frames": list(range(28, 32)), "loop": False, "frameDurationMs": 260},
        {"id": "land_touch", "frames": list(range(32, 36)), "loop": False, "frameDurationMs": 240},
        {"id": "happy", "frames": list(range(32, 36)), "loop": False, "frameDurationMs": 240},
        {"id": "touch", "frames": list(range(32, 36)), "loop": False, "frameDurationMs": 240},
        {"id": "sleep", "frames": list(range(36, 40)), "loop": True, "frameDurationMs": 1200},
    ]
    return {
        "version": 2,
        "petId": "tela",
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
            "backgroundRemoval": "exterior_background_flood_fill",
        },
        "clips": clips,
        "frames": [{"index": i, "name": name} for i, name in enumerate(FRAME_NAMES)],
    }


def write_preview(atlas: Image.Image) -> None:
    gap = 8
    preview = Image.new("RGBA", (COLUMNS * (FRAME_SIZE + gap) + gap, ROWS * (FRAME_SIZE + gap) + gap), (28, 25, 36, 255))
    checker = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (226, 226, 232, 255))
    draw = ImageDraw.Draw(preview)
    for index in range(len(FRAME_NAMES)):
        x = gap + (index % COLUMNS) * (FRAME_SIZE + gap)
        y = gap + (index // COLUMNS) * (FRAME_SIZE + gap)
        preview.alpha_composite(checker, (x, y))
        preview.alpha_composite(atlas.crop((index % COLUMNS * FRAME_SIZE, index // COLUMNS * FRAME_SIZE, (index % COLUMNS + 1) * FRAME_SIZE, (index // COLUMNS + 1) * FRAME_SIZE)), (x, y))
        draw.text((x + 4, y + 2), str(index), fill=(240, 70, 90, 255))
    preview.convert("RGB").save(PREVIEW_PATH, optimize=True)


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    DEBUG_DIR.mkdir(parents=True, exist_ok=True)
    for path in FRAME_DIR.glob("tela_*.png"):
        path.unlink()

    frames: list[Image.Image] = []
    details: list[dict[str, object]] = []
    for board_index, board_name in enumerate(BOARD_NAMES):
        path = RAW_DIR / f"{board_name}.png"
        if not path.exists():
            raise FileNotFoundError(path)
        for cell_index, cell in enumerate(extract_cells(Image.open(path).convert("RGBA"))):
            index = len(frames)
            name = FRAME_NAMES[index]
            frame = normalize(cell, name)
            frame.save(FRAME_DIR / f"tela_{index:02d}_{name}.png", optimize=True)
            frames.append(frame)
            details.append({"index": index, "name": name, "source": str(path.relative_to(ROOT)), "sourceCell": cell_index})

    atlas = Image.new("RGBA", (FRAME_SIZE * COLUMNS, FRAME_SIZE * ROWS), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % COLUMNS) * FRAME_SIZE, (index // COLUMNS) * FRAME_SIZE))
    atlas.save(ATLAS_PATH, optimize=True)
    write_preview(atlas)

    spec = build_spec("tools/tela/pipeline/atlas_v2/tela_motion_v2.png")
    spec["frameDetails"] = details
    SPEC_PATH.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
    debug_spec = build_spec("pets/tela/tela_motion_v2.png")
    DEBUG_SPEC_PATH.write_text(json.dumps(debug_spec, indent=2) + "\n", encoding="utf-8")
    atlas.save(DEBUG_ATLAS_PATH, optimize=True)
    print(f"TELA_V2_BUILT frames={len(frames)} atlas={ATLAS_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
