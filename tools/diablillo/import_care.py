"""Deterministic importer for the reviewed Diablillo RGBA care board."""
from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path

import numpy as np
from PIL import Image

from tools.pet_pipeline import _components


CELL = 256
FRAME_COUNT = 24
PADDING = 16
ALPHA_THRESHOLD = 16
TARGET_BASELINE = CELL - PADDING - 1
SOURCE_NAME = "raw/care-redesign-palette.png"
SOURCE_FRAME_ORDER = tuple(18 if index == 9 else 9 if index == 18 else index for index in range(FRAME_COUNT))


@dataclass(frozen=True)
class FrameTransform:
    index: int
    source_index: int
    source_bbox: tuple[int, int, int, int]
    output_bbox: tuple[int, int, int, int]
    scale: float
    translate_x: int
    translate_y: int


@dataclass(frozen=True)
class ImportedCareAtlas:
    image: Image.Image
    transforms: tuple[FrameTransform, ...]
    scale: float
    contact_points: tuple[dict[str, tuple[float, float]], ...]


def _clean_marginal_specks(cell: Image.Image) -> Image.Image:
    rgba = np.array(cell)
    alpha = rgba[:, :, 3]
    for component in _components(alpha > 0):
        if len(component) > 64:
            continue
        yy, xx = np.asarray(component).T
        if int(alpha[yy, xx].max()) <= 8:
            rgba[yy, xx, 3] = 0
    return Image.fromarray(rgba)


def _frame(cell: np.ndarray) -> tuple[Image.Image, tuple[int, int, int, int]]:
    cleaned = _clean_marginal_specks(Image.fromarray(cell, "RGBA"))
    alpha = np.asarray(cleaned)[:, :, 3]
    yy, xx = np.where(alpha >= ALPHA_THRESHOLD)
    if len(xx) == 0:
        raise ValueError("Diablillo care frame has no visible artwork")
    return cleaned, (int(xx.min()), int(yy.min()), int(xx.max() + 1), int(yy.max() + 1))


def import_care_atlas(source: Path) -> ImportedCareAtlas:
    raw = Image.open(source).convert("RGBA")
    if raw.size != (CELL * 4, CELL * 6):
        raise ValueError("Diablillo source must be a 1024x1536 RGBA board")
    cells: list[Image.Image] = []
    bounds: list[tuple[int, int, int, int]] = []
    for index, source_index in enumerate(SOURCE_FRAME_ORDER):
        box = (source_index % 4 * CELL, source_index // 4 * CELL,
               (source_index % 4 + 1) * CELL, (source_index // 4 + 1) * CELL)
        cell, bbox = _frame(np.asarray(raw.crop(box)))
        cells.append(cell)
        bounds.append(bbox)
    max_dimension = max(max(right - left, bottom - top) for left, top, right, bottom in bounds)
    scaled_size = round(CELL * (CELL - 2 * PADDING - 1) / max_dimension)
    # Anchors use the exact same (integer raster) scale as the artwork.
    scale = scaled_size / CELL
    atlas = Image.new("RGBA", (CELL * 4, CELL * 6))
    transforms: list[FrameTransform] = []
    for index, (cell, source_bbox, source_index) in enumerate(zip(cells, bounds, SOURCE_FRAME_ORDER)):
        scaled = cell.resize((scaled_size, scaled_size), Image.Resampling.LANCZOS)
        scaled_bbox = tuple(round(value * scale) for value in source_bbox)
        source_center = (scaled_bbox[0] + scaled_bbox[2]) / 2
        translate_x = round(CELL / 2 - source_center)
        translate_y = round(TARGET_BASELINE - scaled_bbox[3])
        placed = Image.new("RGBA", (CELL, CELL))
        placed.alpha_composite(scaled, (translate_x, translate_y))
        placed_rgba = np.array(placed)
        placed_rgba[:, :, 3][placed_rgba[:, :, 3] < ALPHA_THRESHOLD] = 0
        placed = Image.fromarray(placed_rgba)
        output_alpha = placed_rgba[:, :, 3]
        output_y, output_x = np.where(output_alpha >= ALPHA_THRESHOLD)
        if len(output_x) == 0:
            raise ValueError(f"Diablillo frame {index} became empty")
        output_box = (int(output_x.min()), int(output_y.min()),
                      int(output_x.max() + 1), int(output_y.max() + 1))
        atlas.alpha_composite(placed, (index % 4 * CELL, index // 4 * CELL))
        transforms.append(FrameTransform(index, source_index, source_bbox, output_box, scale, translate_x, translate_y))
    points_path = source.parent.parent / "contact_points.json"
    contact_points: list[dict[str, tuple[float, float]]] = []
    source_points = json.loads(points_path.read_text())["frames"]
    if len(source_points) != FRAME_COUNT:
        raise ValueError("Diablillo requires contact points for every source frame")
    for transform in transforms:
        points = source_points[transform.source_index]
        contact_points.append({name: ((value[0] * scale + transform.translate_x) / CELL,
                                      (value[1] * scale + transform.translate_y) / CELL)
                               for name, value in points.items()})
    return ImportedCareAtlas(atlas, tuple(transforms), scale, tuple(contact_points))
