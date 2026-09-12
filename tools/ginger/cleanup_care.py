"""Remove the reviewed floor matte from placed Ginger care cells, alpha only.

Keep the original camera, cream paws, facial highlights and whiskers. This is
specific to the 24-frame care board, not a general background remover.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw


def clean_care_cell(cell: Image.Image, index: int, mouth: tuple[int, int]) -> Image.Image:
    if cell.mode != "RGBA" or cell.size != (256, 256) or not 0 <= index < 24:
        raise ValueError("Ginger cleanup needs a placed 256px RGBA care cell")
    rgba = np.array(cell)
    rgb = rgba[:, :, :3].astype(int)
    yy, xx = np.mgrid[:256, :256]
    # The two crouches expose more of the painted floor between their legs.
    first_floor_row = 150 if index in (0, 4) else 175
    neutral_floor = (rgb.max(axis=2) - rgb.min(axis=2) <= 20) & (rgb.min(axis=2) >= 90)
    whiskers = (abs(xx - mouth[0]) < 48) & (abs(yy - mouth[1]) < 16)
    candidate = (neutral_floor & (yy >= first_floor_row) & ~whiskers) | (rgba[:, :, 3] == 0)
    padded = np.pad(candidate.astype(np.uint8) * 255, 1, constant_values=255)
    # Copy makes Pillow's floodfill writable rather than a NumPy-backed view.
    flood = Image.fromarray(padded).copy()
    ImageDraw.floodfill(flood, (0, 0), 128)
    exterior = np.asarray(flood)[1:-1, 1:-1] == 128
    rgba[exterior, 3] = 0
    return Image.fromarray(rgba)
