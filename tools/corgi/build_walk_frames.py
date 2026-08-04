#!/usr/bin/env python3
"""Extract and clean the generated Corgi walk-cycle cells."""

from __future__ import annotations

from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
BOARD_PATH = ROOT / "tools/corgi/raw/corgi_walk_board.png"
CLEAN_DIR = ROOT / "tools/corgi/clean"
OUTPUT_DIR = ROOT / "app/src/main/res/drawable-nodpi"
SOURCE_SIZE = 512
OUTPUT_SIZE = 768
PADDING = 28
BACKGROUND_DISTANCE = 70.0


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


def largest_component(image: Image.Image) -> Image.Image:
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


def clean(cell: Image.Image) -> Image.Image:
    rgba = np.asarray(cell.convert("RGBA")).copy()
    rgb = rgba[:, :, :3].astype(np.float32)
    border = np.concatenate((rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3), rgb[:, :8].reshape(-1, 3), rgb[:, -8:].reshape(-1, 3)))
    background = np.median(border, axis=0)
    distance = np.linalg.norm(rgb - background, axis=2)
    rgba[exterior_mask(distance < BACKGROUND_DISTANCE), 3] = 0
    rgba[rgba[:, :, 3] == 0, :3] = 0
    return largest_component(Image.fromarray(rgba, "RGBA"))


def prepare(cell: Image.Image) -> Image.Image:
    subject = clean(cell)
    bounds = subject.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Generated Corgi cell is empty")
    subject = subject.crop(bounds)
    extent = OUTPUT_SIZE - PADDING * 2
    scale = min(extent / subject.width, extent / subject.height)
    subject = subject.resize(
        (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
        Image.Resampling.LANCZOS,
    )
    frame = Image.new("RGBA", (OUTPUT_SIZE, OUTPUT_SIZE), (0, 0, 0, 0))
    frame.alpha_composite(subject, ((OUTPUT_SIZE - subject.width) // 2, OUTPUT_SIZE - PADDING - subject.height))
    return frame


def main() -> int:
    board = Image.open(BOARD_PATH).convert("RGBA")
    CLEAN_DIR.mkdir(parents=True, exist_ok=True)
    for index in range(4):
        left = (index % 2) * SOURCE_SIZE
        top = (index // 2) * SOURCE_SIZE
        frame = prepare(board.crop((left, top, left + SOURCE_SIZE, top + SOURCE_SIZE)))
        clean_path = CLEAN_DIR / f"corgi_walk_{index}.png"
        output_path = OUTPUT_DIR / f"corgi_{index + 10}.png"
        frame.save(clean_path, optimize=True)
        frame.save(output_path, optimize=True)
        print(f"wrote {output_path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
