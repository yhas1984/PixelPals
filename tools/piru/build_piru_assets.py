#!/usr/bin/env python3
"""Repair the Piru atlas and avatar (numpy-vectorized).

The 97e3e9c..426805d commits solidified the internal transparent holes of
Piru's body WITHOUT rebuilding their RGB, leaving dark body-colored blobs
(avg RGB 8,28,55) visible inside the white belly/face.

Strategy:
  1. Load the last healthy layout (c3dfe4f, kept in tools/piru/piru_sheet_source_v1.png)
     and the current production atlas.
  2. For every pixel solidified later (src alpha == 0, cur alpha > 8): recolor to the
     nearest pixel that was already solid in the source (vectorized multi-source BFS),
     so the belly/face come back white and the body keeps its real colors.
  3. Re-zero any exterior-connected pixel (halo/fringes around the silhouette).
  4. Re-anchor each frame: centered horizontally, content bottom on y=235.
  5. Rebuild the 4x4 atlas and regenerate the avatar from tools/piru/piru.png with
     the same flood-fill alpha repair.
  6. Emit a geometric report so PetView content fractions stay in sync.

Usage: python3 tools/piru/build_piru_assets.py [--atlas-only]
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
TOOLS_DIR = ROOT / "tools" / "piru"
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "pets" / "piru"
ATLAS_PATH = ASSET_DIR / "piru_sheet_v1.png"
AVATAR_PATH = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "pet_piru.png"
SOURCE_ATLAS = TOOLS_DIR / "piru_sheet_source_v1.png"
REFERENCE = TOOLS_DIR / "piru.png"

CELL = 256
COLUMNS = 4
ROWS = 4
FRAME_COUNT = COLUMNS * ROWS
SOLID_ALPHA = 8
BASE_Y = 235
AVATAR_SIZE = 512
AVATAR_BOTTOM = 491
AVATAR_HEIGHT = 470

BACKUP_DIR = TOOLS_DIR / "backup"

DIRS = ((1, 0), (-1, 0), (0, 1), (0, -1))


def _neighbor(arr: np.ndarray, dy: int, dx: int) -> np.ndarray:
    h, w = arr.shape[:2]
    if len(arr.shape) == 2:
        padded = np.pad(arr, 1, constant_values=0)
        return padded[1 + dy : 1 + dy + h, 1 + dx : 1 + dx + w]
    padded = np.pad(arr, ((1, 1), (1, 1), (0, 0)), constant_values=-1)
    return padded[1 + dy : 1 + dy + h, 1 + dx : 1 + dx + w, :]


def exterior_mask(alpha: np.ndarray, solid_alpha: int) -> np.ndarray:
    """Bool mask of pixels connected to the border through non-solid space."""
    solid = alpha > solid_alpha
    exterior = np.zeros(solid.shape, dtype=bool)
    frontier = np.zeros(solid.shape, dtype=bool)
    frontier[0, :] = frontier[-1, :] = frontier[:, 0] = frontier[:, -1] = True
    frontier &= ~solid
    exterior |= frontier
    while frontier.any():
        grown = np.zeros_like(frontier)
        for dy, dx in DIRS:
            grown |= _neighbor(frontier, dy, dx)
        newly = grown & ~solid & ~exterior
        exterior |= newly
        frontier = newly
    return exterior


def nearest_solid_colors(rgba: np.ndarray, solid_alpha: int) -> np.ndarray:
    """RGB color of the nearest solid pixel for every pixel (multi-source BFS)."""
    solid = rgba[..., 3] > solid_alpha
    colors, _ = fill_masks(solid, rgba)
    return colors


def fill_masks(
    mask: np.ndarray, rgba: np.ndarray
) -> tuple[np.ndarray, np.ndarray]:
    """(nearest seed color, BFS distance) for every pixel, seeded from `mask`."""
    h, w = mask.shape[:2]
    idx = np.arange(h * w).reshape(h, w)
    owner = np.full((h, w), -1, dtype=np.int64)
    dist = np.full((h, w), 1 << 30, dtype=np.int64)
    owner[mask] = idx[mask]
    dist[mask] = 0
    filled = mask.copy()
    frontier = mask.copy()
    layer = 0
    while True:
        grown = np.zeros_like(frontier)
        for dy, dx in DIRS:
            grown |= _neighbor(frontier, dy, dx)
        newly = grown & ~filled
        if not newly.any():
            break
        layer += 1
        remaining = newly.copy()
        for dy, dx in DIRS:
            if not remaining.any():
                break
            neighbor_owner = _neighbor(owner, dy, dx)
            neighbor_dist = _neighbor(dist, dy, dx)
            take = remaining & (neighbor_dist < layer)
            owner[take] = neighbor_owner[take]
            dist[take] = layer
            remaining &= ~take
        filled |= grown
        frontier = newly
    colors = rgba[..., :3].astype(np.int32).reshape(-1, 3)
    return colors[owner.reshape(-1)].reshape(h, w, 3), dist


def nearest_class_colors(
    rgba: np.ndarray, good: np.ndarray, light_luma: int = 128
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Recolor non-good pixels from the closest class: light (belly/face) or dark.

    `good` marks the pixels whose colors are trusted (already solid in the
    healthy source layout). The old holes are not seeds, so each hole pixel is
    colored by whichever trusted class boundary is closer: belly interior
    becomes white, gaps adjacent to the body become body-colored.

    Returns (colors, light_colors, dark_colors).
    """
    luma = (
        rgba[..., 0].astype(np.int32) * 299
        + rgba[..., 1].astype(np.int32) * 587
        + rgba[..., 2].astype(np.int32) * 114
    ) // 1000
    light = good & (luma > light_luma)
    dark = good & ~light
    light_colors, light_dist = fill_masks(light, rgba)
    dark_colors, dark_dist = fill_masks(dark, rgba)
    decision = light_dist <= dark_dist
    decision = majority3(decision, passes=2)
    return (
        np.where(decision[..., np.newaxis], light_colors, dark_colors),
        light_colors,
        dark_colors,
    )


def smooth_bad_colors(
    out: np.ndarray,
    bad: np.ndarray,
    light_colors: np.ndarray,
    dark_colors: np.ndarray,
    light_luma: int = 128,
) -> None:
    """Re-vote the light/dark decision after the first fill to kill seam lines."""
    luma = (
        out[..., 0].astype(np.int32) * 299
        + out[..., 1].astype(np.int32) * 587
        + out[..., 2].astype(np.int32) * 114
    ) // 1000
    # Many passes collapse thin dark bands enclosed in the white belly into
    # light, while large dark regions (body/outline) stay supported.
    decision = majority3(luma > light_luma, passes=8)
    out[bad, :3] = np.where(
        decision[..., np.newaxis][bad],
        light_colors[bad],
        dark_colors[bad],
    )


REFERENCE_IMAGE = np.asarray(Image.open(REFERENCE).convert("RGBA"), dtype=np.uint8)
REF_BBOX = (15, 14, 1010, 1501)  # alpha bbox of the reference character
REF_FACE_BAND = 0.45  # top fraction of the content considered "face"
REF_MIN_LUMA = 150  # only stamp white-ish reference pixels


def stamp_reference_white(
    out: np.ndarray, bad: np.ndarray, min_standing_aspect: float = 1.2
) -> None:
    """Paint the corrupted face/forehead area with the reference's white.

    The current art's face band is heavily corrupted (up to ~40% of its pixels
    were wrongly solidified dark). The reference render is clean and the head
    framing matches, so a per-frame affine (content-bbox -> reference-bbox)
    lets us sample the reference's white for the corrupted face pixels.
    Only standing frames are stamped (lying poses break the vertical mapping)
    and only where the reference pixel is white-ish, so a slight misalignment
    never paints dark features over the frame.
    """
    solid = out[..., 3] > 8
    ys, xs = np.nonzero(solid)
    if ys.size == 0:
        return
    top, bottom, left, right = ys.min(), ys.max(), xs.min(), xs.max()
    content_h = bottom - top + 1
    content_w = right - left + 1
    if content_h < min_standing_aspect * content_w:
        return
    ref_h, ref_w = REFERENCE_IMAGE.shape[:2]
    ref_origin_x, ref_origin_y, ref_max_x, ref_max_y = REF_BBOX
    scale = (ref_max_y - ref_origin_y + 1) / content_h
    face_top = top + int(content_h * REF_FACE_BAND)

    yy, xx = np.mgrid[top : bottom + 1, left : right + 1]
    ry = np.clip(
        ((yy - top) * scale + ref_origin_y).astype(np.int64), 0, ref_h - 1
    )
    rx = np.clip(
        ((xx - left) * scale + ref_origin_x).astype(np.int64), 0, ref_w - 1
    )
    ref_rgb = REFERENCE_IMAGE[ry, rx, :3]
    ref_alpha = REFERENCE_IMAGE[ry, rx, 3]
    ref_luma = (
        ref_rgb[..., 0].astype(np.int32) * 299
        + ref_rgb[..., 1].astype(np.int32) * 587
        + ref_rgb[..., 2].astype(np.int32) * 114
    ) // 1000
    band = bad[top : bottom + 1, left : right + 1]
    take = band & (yy <= face_top) & (ref_alpha > 8) & (ref_luma > REF_MIN_LUMA)
    out[top : bottom + 1, left : right + 1, :][take, :3] = ref_rgb[take]
    out[top : bottom + 1, left : right + 1, :][take, 3] = 255


def majority3(mask: np.ndarray, passes: int = 1) -> np.ndarray:
    """Smooth a boolean decision with a 3x3 majority vote (vectorized)."""
    result = mask.astype(np.int16)
    for _ in range(passes):
        total = np.zeros_like(result)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dy == 0 and dx == 0:
                    total += result
                else:
                    total += _neighbor(result, dy, dx)
        result = (total >= 5).astype(np.int16)
    return result > 0


def repair(rgba_src: np.ndarray, rgba_cur: np.ndarray, solid_alpha: int) -> np.ndarray:
    """Recolor wrongly solidified pixels and strip exterior halos."""
    out = rgba_cur.copy()
    src_alpha = rgba_src[..., 3]
    cur_alpha = rgba_cur[..., 3]
    solid = (src_alpha > solid_alpha) | (cur_alpha > solid_alpha)
    solid_rgba = rgba_src.copy()
    solid_rgba[..., 3] = np.where(solid, 255, 0).astype(np.uint8)

    exterior = exterior_mask(solid_rgba[..., 3], solid_alpha)
    nearest, light_colors, dark_colors = nearest_class_colors(
        solid_rgba, good=src_alpha > solid_alpha
    )

    out[exterior] = (0, 0, 0, 0)

    bad = (cur_alpha > solid_alpha) & (src_alpha <= solid_alpha) & ~exterior
    if bad.any():
        out[bad, :3] = nearest[bad]
        out[bad, 3] = 255
        smooth_bad_colors(out, bad, light_colors, dark_colors)
        stamp_reference_white(out, bad)

    restored = (src_alpha > solid_alpha) & (cur_alpha <= solid_alpha) & ~exterior
    if restored.any():
        out[restored] = rgba_src[restored]

    kept = (cur_alpha > solid_alpha) & ~bad & ~exterior
    if kept.any():
        out[kept] = rgba_cur[kept]

    fill_interior_holes(out, solid_alpha)
    return out


def fill_interior_holes(out: np.ndarray, solid_alpha: int) -> None:
    """Fill transparent pockets enclosed by solid with the nearest solid color."""
    solid = out[..., 3] > solid_alpha
    ext = exterior_mask(out[..., 3], solid_alpha)
    holes = ~ext & ~solid
    if not holes.any():
        return
    nearest = nearest_solid_colors(out, solid_alpha)
    out[holes, :3] = nearest[holes]
    out[holes, 3] = 255


def reanchor(frame: np.ndarray, bottom: int, solid_alpha: int) -> np.ndarray:
    solid = frame[..., 3] > solid_alpha
    ys, xs = np.nonzero(solid)
    if ys.size == 0:
        return frame
    content = frame[ys.min() : ys.max() + 1, xs.min() : xs.max() + 1]
    out = np.zeros_like(frame)
    x = (frame.shape[1] - content.shape[1]) // 2
    y = max(0, bottom - content.shape[0])
    x = max(0, min(x, frame.shape[1] - content.shape[1]))
    out[y : y + content.shape[0], x : x + content.shape[1]] = content
    return out


def write_backed_up(path: Path, image: Image.Image) -> None:
    if path.exists():
        BACKUP_DIR.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, BACKUP_DIR / path.name)
    image.save(path, optimize=True)
    print(f"Written: {path}")


def build_atlas() -> list[np.ndarray]:
    source = np.asarray(Image.open(SOURCE_ATLAS).convert("RGBA"), dtype=np.uint8)
    current = np.asarray(Image.open(ATLAS_PATH).convert("RGBA"), dtype=np.uint8)
    if source.shape[:2] != current.shape[:2] or current.shape[:2] != (CELL * ROWS, CELL * COLUMNS):
        raise ValueError(f"Unexpected atlas size: source={source.shape} current={current.shape}")

    frames: list[np.ndarray] = []
    report: list[dict[str, object]] = []
    for index in range(FRAME_COUNT):
        col = index % COLUMNS
        row = index // COLUMNS
        box = (slice(row * CELL, (row + 1) * CELL), slice(col * CELL, (col + 1) * CELL))
        repaired = repair(source[box], current[box], SOLID_ALPHA)
        anchored = reanchor(repaired, BASE_Y, SOLID_ALPHA)
        frames.append(anchored)
        solid = anchored[..., 3] > SOLID_ALPHA
        ys, xs = np.nonzero(solid)
        content_height = int(ys.max() - ys.min()) if ys.size else 0
        report.append(
            {
                "index": index,
                "bbox": (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())),
                "base_y": int(ys.max()) if ys.size else None,
                "content_fraction": round(content_height / CELL, 4),
            }
        )

    atlas = np.zeros((CELL * ROWS, CELL * COLUMNS, 4), dtype=np.uint8)
    for index, frame in enumerate(frames):
        row = index // COLUMNS
        col = index % COLUMNS
        atlas[row * CELL : (row + 1) * CELL, col * CELL : (col + 1) * CELL] = frame
    write_backed_up(ATLAS_PATH, Image.fromarray(atlas, "RGBA"))

    print("=== Piru atlas report (index, bbox, base_y, content_fraction) ===")
    for entry in report:
        print(entry)
    write_preview(frames)
    return frames


def write_preview(frames: list[np.ndarray]) -> None:
    gap = 8
    preview = Image.new(
        "RGBA",
        (COLUMNS * (CELL + gap) + gap, ROWS * (CELL + gap) + gap),
        (18, 18, 26, 255),
    )
    checker = Image.new("RGBA", (CELL, CELL), (230, 230, 236, 255))
    for y in range(0, CELL, 24):
        for x in range(0, CELL, 24):
            if (x // 24 + y // 24) % 2 == 0:
                checker.paste((255, 255, 255, 255), (x, y, x + 24, y + 24))
    draw = ImageDraw.Draw(preview)
    for index, frame in enumerate(frames):
        col = index % COLUMNS
        row = index // COLUMNS
        ox = gap + col * (CELL + gap)
        oy = gap + row * (CELL + gap)
        preview.alpha_composite(checker, (ox, oy))
        preview.alpha_composite(Image.fromarray(frame, "RGBA"), (ox, oy))
        draw.text((ox + 4, oy + 2), str(index), fill=(230, 60, 60, 255))
    preview.convert("RGB").save(TOOLS_DIR / "piru_atlas_preview.png", optimize=True)
    print(f"Preview: {TOOLS_DIR / 'piru_atlas_preview.png'}")


def build_avatar() -> None:
    source = np.asarray(Image.open(REFERENCE).convert("RGBA"), dtype=np.uint8)
    solid_rgba = source.copy()
    solid_rgba[source[..., 3] <= SOLID_ALPHA, 3] = 0
    solid_rgba[source[..., 3] <= SOLID_ALPHA, :3] = 0
    exterior = exterior_mask(solid_rgba[..., 3], SOLID_ALPHA)
    nearest = nearest_solid_colors(solid_rgba, SOLID_ALPHA)

    cleaned = source.copy()
    cleaned[exterior] = (0, 0, 0, 0)
    interior = ~exterior & (source[..., 3] <= SOLID_ALPHA)
    cleaned[interior, :3] = nearest[interior]
    cleaned[interior, 3] = 255

    solid = cleaned[..., 3] > SOLID_ALPHA
    ys, xs = np.nonzero(solid)
    if ys.size == 0:
        raise ValueError("Empty avatar reference")
    subject = cleaned[ys.min() : ys.max() + 1, xs.min() : xs.max() + 1]
    pil_subject = Image.fromarray(subject, "RGBA")
    scale = AVATAR_HEIGHT / subject.shape[0]
    resized = pil_subject.resize(
        (max(1, round(subject.shape[1] * scale)), max(1, round(subject.shape[0] * scale))),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new("RGBA", (AVATAR_SIZE, AVATAR_SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(resized, ((AVATAR_SIZE - resized.width) // 2, AVATAR_BOTTOM - resized.height))
    write_backed_up(AVATAR_PATH, canvas)
    bounds = canvas.getchannel("A").point(lambda v: 255 if v > SOLID_ALPHA else 0).getbbox()
    print(f"Avatar bbox: {bounds}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--atlas-only", action="store_true", help="skip the avatar")
    args = parser.parse_args()
    build_atlas()
    if not args.atlas_only:
        build_avatar()
    return 0


if __name__ == "__main__":
    sys.exit(main())
