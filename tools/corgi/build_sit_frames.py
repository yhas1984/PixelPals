"""Extract the generated Corgi sit-transition sheet for visual review only."""
from __future__ import annotations

from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/corgi/raw/sit-transition-2026-09-12/generated.png"
CLEAN = ROOT / "tools/corgi/clean"
EVIDENCE = ROOT / "docs/companion/evidence/corgi-sit-2026-09-12"
CELL = 512
COLS, ROWS = 3, 2


def exterior_neutral(rgb: np.ndarray) -> np.ndarray:
    """Find neutral pixels connected to the exterior; enclosed whites survive."""
    chroma = rgb.max(axis=2).astype(int) - rgb.min(axis=2).astype(int)
    candidate = (chroma <= 25) & (rgb.min(axis=2) >= 90)
    height, width = candidate.shape
    seen = np.zeros_like(candidate)
    queue: deque[tuple[int, int]] = deque()
    for x in range(width):
        if candidate[0, x]: queue.append((0, x))
        if candidate[-1, x]: queue.append((height - 1, x))
    for y in range(height):
        if candidate[y, 0]: queue.append((y, 0))
        if candidate[y, -1]: queue.append((y, width - 1))
    while queue:
        y, x = queue.popleft()
        if seen[y, x]:
            continue
        seen[y, x] = True
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < height and 0 <= nx < width and candidate[ny, nx] and not seen[ny, nx]:
                queue.append((ny, nx))
    return seen


def clean_sheet(image: Image.Image) -> Image.Image:
    rgba = np.array(image.convert("RGBA"))
    rgba[exterior_neutral(rgba[:, :, :3])] = (0, 0, 0, 0)
    return Image.fromarray(rgba)


def main() -> None:
    CLEAN.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    clean = clean_sheet(Image.open(SOURCE))
    cells: list[Image.Image] = []
    bboxes: list[tuple[int, int, int, int] | None] = []
    for index in range(COLS * ROWS):
        row, col = divmod(index, COLS)
        cell = clean.crop((col * CELL, row * CELL, (col + 1) * CELL, (row + 1) * CELL))
        cells.append(cell)
        bbox = cell.getchannel("A").getbbox()
        bboxes.append(bbox)
        cell.save(CLEAN / f"sit-{index}.png", optimize=True)

    for name, background, label_color in (
        ("sit-review-light.png", "#e7d7c7", "#342538"),
        ("sit-review-dark.png", "#252238", "#f4e9df"),
    ):
        sheet = Image.new("RGBA", clean.size, background)
        sheet.alpha_composite(clean)
        draw = ImageDraw.Draw(sheet)
        for index, bbox in enumerate(bboxes):
            row, col = divmod(index, COLS)
            x, y = col * CELL, row * CELL
            draw.rectangle((x, y, x + CELL - 1, y + CELL - 1), outline="#8a6c5c", width=2)
            draw.text((x + 8, y + 8), f"{index} {bbox}", fill=label_color)
        sheet.convert("RGB").save(EVIDENCE / name, optimize=True)

    for index, cell in enumerate(cells):
        alpha = np.asarray(cell.getchannel("A"))
        rgb = np.asarray(cell.convert("RGB"))
        opaque = alpha > 0
        colors = rgb[opaque]
        dark = opaque & (rgb.max(axis=2) < 64)
        light = opaque & (rgb.min(axis=2) >= 240)
        print(
            f"frame={index:02d} bbox={bboxes[index]} opaque_pixels={int((alpha > 0).sum())} "
            f"dark_protected={int(dark.sum())} light_protected={int(light.sum())} "
            f"protected_rgb_min={colors.min(axis=0).tolist()} protected_rgb_max={colors.max(axis=0).tolist()}"
        )


if __name__ == "__main__":
    main()
