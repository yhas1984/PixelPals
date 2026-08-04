#!/usr/bin/env python3
"""Extract Querubin storyboard cells and build the production atlas."""

from __future__ import annotations

import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "tools/angel/raw"
CLEAN_DIR = ROOT / "tools/angel/clean"
ASSET_DIR = ROOT / "app/src/main/assets/pets/angel"
ATLAS_PATH = ASSET_DIR / "angel_sheet_v4.png"
SPEC_PATH = ASSET_DIR / "angel_sheet_v4.json"
PREVIEW_PATH = ROOT / "app/src/main/res/drawable-nodpi/pet_angel.png"

SOURCE_SIZE = 512
CELL_SIZE = 384
PADDING = 16
BACKGROUND_DISTANCE = 10.0

FRAMES = (
    ("board_01_hover.png", 0, "hover_neutral"),
    ("board_01_hover.png", 1, "hover_upstroke"),
    ("board_01_hover.png", 2, "hover_downstroke"),
    ("board_01_hover.png", 3, "hover_wide"),
    ("board_02_flight.png", 0, "flight_power"),
    ("board_02_flight.png", 1, "flight_pass"),
    ("board_02_flight.png", 2, "glide_level"),
    ("board_02_flight.png", 3, "glide_reach"),
    ("board_03_grace.png", 0, "halo_brighten"),
    ("board_03_grace.png", 1, "blessing"),
    ("board_03_grace.png", 2, "prayer"),
    ("board_03_grace.png", 3, "prayer_rest"),
    ("board_04_reactions.png", 0, "touch_reach"),
    ("board_04_reactions.png", 1, "drag_resist"),
    ("board_04_reactions.png", 2, "fling_tuck"),
    ("board_04_reactions.png", 3, "recover_hover"),
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


def components(mask: np.ndarray) -> list[list[tuple[int, int]]]:
    height, width = mask.shape
    visited = np.zeros((height, width), dtype=bool)
    result: list[list[tuple[int, int]]] = []
    for start_y, start_x in np.argwhere(mask):
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
                        if mask[ny, nx] and not visited[ny, nx]:
                            visited[ny, nx] = True
                            queue.append((ny, nx))
        result.append(component)
    return result


def keep_subject_components(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image).copy()
    found = components(rgba[:, :, 3] > 8)
    largest = max(found, key=len, default=[])
    if not largest:
        return Image.fromarray(rgba, "RGBA")
    largest_y, largest_x = zip(*largest)
    center_y = sum(largest_y) / len(largest)
    center_x = sum(largest_x) / len(largest)
    keep = np.zeros(rgba.shape[:2], dtype=bool)
    for component in found:
        ys, xs = zip(*component)
        component_y = sum(ys) / len(component)
        component_x = sum(xs) / len(component)
        distance = ((component_x - center_x) ** 2 + (component_y - center_y) ** 2) ** 0.5
        if component is largest or (len(component) >= 120 and distance <= 210):
            keep[np.asarray(ys), np.asarray(xs)] = True
    rgba[~keep] = 0
    return Image.fromarray(rgba, "RGBA")


def remove_background(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    rgb = rgba[:, :, :3].astype(np.float32)
    border = np.concatenate((rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3), rgb[:, :8].reshape(-1, 3), rgb[:, -8:].reshape(-1, 3)))
    background = np.median(border, axis=0)
    distance = np.linalg.norm(rgb - background, axis=2)
    rgba[exterior_mask(distance < BACKGROUND_DISTANCE), 3] = 0
    rgba[rgba[:, :, 3] == 0, :3] = 0
    return keep_subject_components(Image.fromarray(rgba, "RGBA"))


def prepare(cell: Image.Image) -> Image.Image:
    cleaned = remove_background(cell)
    bounds = cleaned.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Empty Querubin frame")
    subject = cleaned.crop(bounds)
    extent = CELL_SIZE - PADDING * 2
    scale = min(extent / subject.width, extent / subject.height)
    subject = subject.resize(
        (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
        Image.Resampling.LANCZOS,
    )
    frame = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0))
    frame.alpha_composite(subject, ((CELL_SIZE - subject.width) // 2, (CELL_SIZE - subject.height) // 2))
    return frame


def preview(frame: Image.Image) -> Image.Image:
    bounds = frame.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Empty Querubin preview")
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
        "petId": "angel",
        "atlasPath": "pets/angel/angel_sheet_v4.png",
        "previewPath": "res://drawable-nodpi/pet_angel.png",
        "frameWidth": CELL_SIZE,
        "frameHeight": CELL_SIZE,
        "columns": 4,
        "rows": 4,
        "frameCount": 16,
        "pivot": {"x": CELL_SIZE // 2, "y": CELL_SIZE // 2},
        "renderHints": {
            "innerTransparentPaddingPx": PADDING,
            "recommendedBleedInsetPx": 1,
            "filterBitmap": True,
        },
        "clips": [
            {"id": "hover", "frames": [0, 1, 2, 3, 2, 1], "loop": True, "frameDurationMs": 180},
            {"id": "flap", "frames": [4, 5], "loop": True, "frameDurationMs": 145},
            {"id": "glide", "frames": [6, 7, 6], "loop": True, "frameDurationMs": 240},
            {"id": "grace", "frames": [8, 9, 10, 11], "loop": False, "frameDurationMs": 230},
            {"id": "prayer", "frames": [10, 11, 10], "loop": True, "frameDurationMs": 420},
            {"id": "touch", "frames": [12], "loop": False, "frameDurationMs": 550},
            {"id": "drag", "frames": [13], "loop": False, "frameDurationMs": 180},
            {"id": "fling_recover", "frames": [14, 15], "loop": False, "frameDurationMs": 180},
        ],
        "frames": [
            {"index": index, "name": name, "sourceHint": board}
            for index, (board, _, name) in enumerate(FRAMES)
        ],
    }


def main() -> int:
    CLEAN_DIR.mkdir(parents=True, exist_ok=True)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    frames: list[Image.Image] = []
    boards: dict[str, Image.Image] = {}
    for index, (board_name, cell_index, frame_name) in enumerate(FRAMES):
        board_path = RAW_DIR / board_name
        if not board_path.exists():
            raise FileNotFoundError(board_path)
        board = boards.setdefault(board_name, Image.open(board_path).convert("RGBA"))
        left = (cell_index % 2) * SOURCE_SIZE
        top = (cell_index // 2) * SOURCE_SIZE
        frame = prepare(board.crop((left, top, left + SOURCE_SIZE, top + SOURCE_SIZE)))
        frame.save(CLEAN_DIR / f"angel_{index:02d}_{frame_name}.png", optimize=True)
        frames.append(frame)
    atlas = Image.new("RGBA", (CELL_SIZE * 4, CELL_SIZE * 4), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % 4) * CELL_SIZE, (index // 4) * CELL_SIZE))
    atlas.save(ATLAS_PATH, optimize=True)
    preview(frames[0]).save(PREVIEW_PATH, optimize=True)
    SPEC_PATH.write_text(json.dumps(build_spec(), indent=2) + "\n", encoding="utf-8")
    print(f"Querubin atlas written: {len(frames)} frames, {atlas.size[0]}x{atlas.size[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
