#!/usr/bin/env python3
"""Extract Ginger storyboard cells and build the production atlas."""

from __future__ import annotations

import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "tools/ginger/raw"
CLEAN_DIR = ROOT / "tools/ginger/clean"
ASSET_DIR = ROOT / "app/src/main/assets/pets/ginger"
ATLAS_PATH = ASSET_DIR / "ginger_sheet_v2.png"
SPEC_PATH = ASSET_DIR / "ginger_sheet_v2.json"
PREVIEW_PATH = ROOT / "app/src/main/res/drawable-nodpi/pet_ginger.png"

SOURCE_SIZE = 512
CELL_SIZE = 384
BACKGROUND_DISTANCE = 70.0
PADDING = 16

FRAMES = (
    ("board_01_idle.png", 0, "sit_neutral"),
    ("board_01_idle.png", 1, "groom_paw"),
    ("board_01_idle.png", 2, "sleep_curl"),
    ("board_01_idle.png", 3, "wake_stretch"),
    ("board_02_walk.png", 0, "walk_contact_a"),
    ("board_02_walk.png", 1, "walk_pass_a"),
    ("board_02_walk.png", 2, "walk_contact_b"),
    ("board_02_walk.png", 3, "walk_pass_b"),
    ("board_03_stalk.png", 0, "stalk_crouch"),
    ("board_03_stalk.png", 1, "stalk_step"),
    ("board_03_stalk.png", 2, "stalk_freeze"),
    ("board_03_stalk.png", 3, "pounce_coil"),
    ("board_04_action.png", 0, "pounce_air"),
    ("board_04_action.png", 1, "land_impact"),
    ("board_04_action.png", 2, "land_recover"),
    ("board_04_action.png", 3, "touch_drag"),
)


def exterior_mask(matches: np.ndarray) -> np.ndarray:
    height, width = matches.shape
    exterior = np.zeros((height, width), dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    def add(y: int, x: int) -> None:
        if matches[y, x] and not exterior[y, x]:
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
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx or dy:
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < height and 0 <= nx < width:
                        add(ny, nx)
    return exterior


def remove_background(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    rgb = rgba[:, :, :3].astype(np.float32)
    border = np.concatenate((rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3), rgb[:, :8].reshape(-1, 3), rgb[:, -8:].reshape(-1, 3)))
    background = np.median(border, axis=0)
    distance = np.linalg.norm(rgb - background, axis=2)
    rgba[exterior_mask(distance < BACKGROUND_DISTANCE), 3] = 0
    rgba[rgba[:, :, 3] == 0, :3] = 0
    return keep_largest_component(Image.fromarray(rgba, "RGBA"))


def keep_largest_component(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image).copy()
    foreground = rgba[:, :, 3] > 8
    height, width = foreground.shape
    visited = np.zeros((height, width), dtype=bool)
    largest: list[tuple[int, int]] = []
    for start_y, start_x in np.argwhere(foreground):
        if visited[start_y, start_x]:
            continue
        component: list[tuple[int, int]] = []
        queue: deque[tuple[int, int]] = deque([(int(start_y), int(start_x))])
        visited[start_y, start_x] = True
        while queue:
            y, x = queue.popleft()
            component.append((y, x))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < height and 0 <= nx < width:
                        if foreground[ny, nx] and not visited[ny, nx]:
                            visited[ny, nx] = True
                            queue.append((ny, nx))
        if len(component) > len(largest):
            largest = component
    keep = np.zeros((height, width), dtype=bool)
    if largest:
        ys, xs = zip(*largest)
        keep[np.asarray(ys), np.asarray(xs)] = True
    rgba[~keep] = 0
    return Image.fromarray(rgba, "RGBA")


def extract_cell(board: Image.Image, cell_index: int) -> Image.Image:
    left = (cell_index % 2) * SOURCE_SIZE
    top = (cell_index // 2) * SOURCE_SIZE
    return board.crop((left, top, left + SOURCE_SIZE, top + SOURCE_SIZE))


def prepare_frame(cell: Image.Image, frame_name: str) -> Image.Image:
    cleaned = remove_background(cell)
    bounds = cleaned.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError(f"Empty frame: {frame_name}")
    subject = cleaned.crop(bounds)
    max_width = CELL_SIZE - PADDING * 2
    max_height = CELL_SIZE - PADDING * 2
    scale = min(max_width / subject.width, max_height / subject.height)
    resized = subject.resize(
        (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
        Image.Resampling.LANCZOS,
    )
    frame = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0))
    x = (CELL_SIZE - resized.width) // 2
    if frame_name == "pounce_air":
        y = (CELL_SIZE - resized.height) // 2
    else:
        y = CELL_SIZE - PADDING - resized.height
    frame.alpha_composite(resized, (x, y))
    return frame


def preview(frame: Image.Image) -> Image.Image:
    bounds = frame.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Cannot preview an empty frame")
    subject = frame.crop(bounds)
    scale = min(440 / subject.width, 440 / subject.height)
    subject = subject.resize(
        (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
        Image.Resampling.LANCZOS,
    )
    result = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    result.alpha_composite(subject, ((512 - subject.width) // 2, (512 - subject.height) // 2))
    return result


def build_spec() -> dict[str, object]:
    return {
        "version": 1,
        "petId": "ginger",
        "atlasPath": "pets/ginger/ginger_sheet_v2.png",
        "previewPath": "res://drawable-nodpi/pet_ginger.png",
        "frameWidth": CELL_SIZE,
        "frameHeight": CELL_SIZE,
        "columns": 4,
        "rows": 4,
        "frameCount": len(FRAMES),
        "pivot": {"x": CELL_SIZE // 2, "y": CELL_SIZE - PADDING},
        "renderHints": {
            "innerTransparentPaddingPx": PADDING,
            "recommendedBleedInsetPx": 1,
            "filterBitmap": True,
        },
        "clips": [
            {"id": "sit", "frames": [0], "loop": True, "frameDurationMs": 1200},
            {"id": "groom", "frames": [0, 1, 0], "loop": False, "frameDurationMs": 360},
            {"id": "sleep", "frames": [2], "loop": True, "frameDurationMs": 1200},
            {"id": "wake", "frames": [2, 3, 0], "loop": False, "frameDurationMs": 280},
            {"id": "walk", "frames": [4, 5, 6, 7], "loop": True, "frameDurationMs": 135},
            {"id": "stalk", "frames": [8, 9, 10], "loop": True, "frameDurationMs": 190},
            {"id": "pounce", "frames": [11, 12], "loop": False, "frameDurationMs": 120},
            {"id": "land", "frames": [13, 14], "loop": False, "frameDurationMs": 150},
            {"id": "touch_drag", "frames": [15], "loop": False, "frameDurationMs": 240},
        ],
        "frames": [
            {"index": index, "name": name, "sourceHint": board}
            for index, (board, _, name) in enumerate(FRAMES)
        ],
    }


def main() -> int:
    CLEAN_DIR.mkdir(parents=True, exist_ok=True)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    boards: dict[str, Image.Image] = {}
    frames: list[Image.Image] = []
    for index, (board_name, cell_index, frame_name) in enumerate(FRAMES):
        board_path = RAW_DIR / board_name
        if not board_path.exists():
            raise FileNotFoundError(board_path)
        board = boards.setdefault(board_name, Image.open(board_path).convert("RGBA"))
        frame = prepare_frame(extract_cell(board, cell_index), frame_name)
        frame.save(CLEAN_DIR / f"ginger_{index:02d}_{frame_name}.png", optimize=True)
        frames.append(frame)
    atlas = Image.new("RGBA", (CELL_SIZE * 4, CELL_SIZE * 4), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % 4) * CELL_SIZE, (index // 4) * CELL_SIZE))
    atlas.save(ATLAS_PATH, optimize=True)
    preview(frames[0]).save(PREVIEW_PATH, optimize=True)
    SPEC_PATH.write_text(json.dumps(build_spec(), indent=2) + "\n", encoding="utf-8")
    print(f"Ginger atlas written: {len(frames)} frames, {atlas.size[0]}x{atlas.size[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
