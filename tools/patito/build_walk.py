"""Extract the authorised Patito walk sheet without changing its artwork scale."""
from __future__ import annotations

from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/patito/source/walk-2026-09-12.png"
REVIEW = ROOT / "tools/patito/review"
CELL = 512
COLS, ROWS = 3, 2


def exterior_neutral(rgb: np.ndarray) -> np.ndarray:
    """Return neutral pixels connected to the sheet boundary (enclosed whites survive)."""
    chroma = rgb.max(axis=2).astype(int) - rgb.min(axis=2).astype(int)
    candidate = (chroma <= 25) & (rgb.min(axis=2) >= 90)
    height, width = candidate.shape
    seen = np.zeros_like(candidate)
    queue: deque[tuple[int, int]] = deque()
    for x in range(width):
        if candidate[0, x]: queue.append((0, x))
        if candidate[height - 1, x]: queue.append((height - 1, x))
    for y in range(height):
        if candidate[y, 0]: queue.append((y, 0))
        if candidate[y, width - 1]: queue.append((y, width - 1))
    while queue:
        y, x = queue.popleft()
        if seen[y, x]: continue
        seen[y, x] = True
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < height and 0 <= nx < width and candidate[ny, nx] and not seen[ny, nx]:
                queue.append((ny, nx))
    return seen


def clean_sheet(image: Image.Image) -> Image.Image:
    rgba = np.array(image.convert("RGBA"))
    outside = exterior_neutral(rgba[:, :, :3])
    rgba[outside] = (0, 0, 0, 0)
    return Image.fromarray(rgba)


def bbox_for(cell: Image.Image) -> tuple[int, int, int, int] | None:
    return cell.getchannel("A").getbbox()


def main() -> None:
    REVIEW.mkdir(parents=True, exist_ok=True)
    clean = clean_sheet(Image.open(SOURCE))
    cells: list[Image.Image] = []
    bboxes: list[tuple[int, int, int, int] | None] = []
    for index in range(COLS * ROWS):
        row, col = divmod(index, COLS)
        cell = clean.crop((col * CELL, row * CELL, (col + 1) * CELL, (row + 1) * CELL))
        cells.append(cell)
        bboxes.append(bbox_for(cell))
        cell.save(REVIEW / f"walk-{index}.png", optimize=True)

    for name, background in (("walk-review-beige.png", "#e7d7c7"), ("walk-review-dark.png", "#252238")):
        sheet = Image.new("RGBA", clean.size, background)
        sheet.alpha_composite(clean)
        draw = ImageDraw.Draw(sheet)
        for index, bbox in enumerate(bboxes):
            row, col = divmod(index, COLS)
            x, y = col * CELL, row * CELL
            draw.rectangle((x, y, x + CELL - 1, y + CELL - 1), outline="#8a6c5c", width=2)
            draw.text((x + 8, y + 8), f"{index} {bbox}", fill="#342538" if "beige" in name else "#f4e9df")
        sheet.convert("RGB").save(REVIEW / name, optimize=True)

    lines = ["source=tools/patito/source/walk-2026-09-12.png", f"dimensions={clean.size}"]
    for index, cell in enumerate(cells):
        bbox = bbox_for(cell)
        alpha = np.asarray(cell.getchannel("A"))
        rgb = np.asarray(cell.convert("RGB"))
        interior = alpha > 0
        colors = rgb[interior]
        lines.append(f"frame={index:02d} bbox={bbox} opaque_pixels={int(interior.sum())} interior_rgb_min={colors.min(axis=0).tolist()} interior_rgb_max={colors.max(axis=0).tolist()}")
    (REVIEW / "walk-metrics.txt").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
