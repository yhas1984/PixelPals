#!/usr/bin/env python3
"""Import the user-created Taro sheet into the production atlas.

Source: tools/taro/taro_atlas_preview.png (1254x1254 RGB) — a 4x4 contact sheet
of the 16 Taro poses on a light checkerboard, with red index labels and
irregular gutters.

Pipeline:
  1. Detect the 4x4 cell grid from the near-white gutter bands.
  2. Per cell, flood-fill the border-connected light background (keeps enclosed
     white details: eyes, nails, highlights).
  3. Drop the red labels and stray blobs (keep only the largest component).
  4. Scale ALL poses with one shared factor so the tallest fits the 256 cell
     (min 8px margin), then anchor: centered horizontally, base at y=232.
  5. Write app/src/main/assets/pets/taro/taro_sheet_v1.png (1024x1024 RGBA),
     regenerate app/src/main/res/drawable-nodpi/pet_taro.png (512x512) from
     frame 0, print the geometric report for PetView fractions, and write a
     verification preview (tools/taro/taro_final_preview.png).
"""
from __future__ import annotations

import shutil
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
TOOLS_DIR = ROOT / "tools" / "taro"
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "pets" / "taro"
ATLAS_PATH = ASSET_DIR / "taro_sheet_v1.png"
AVATAR_PATH = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "pet_taro.png"
SOURCE = TOOLS_DIR / "taro_atlas_preview.png"

CELL = 256
COLUMNS = 4
ROWS = 4
FRAME_COUNT = COLUMNS * ROWS
SOLID_ALPHA = 8
BASE_Y = 232
MIN_MARGIN = 8
AVATAR_SIZE = 512
AVATAR_BOTTOM = 491

BACKUP_DIR = TOOLS_DIR / "backup"

# Distancia RGB máxima (Euclídea) al color de referencia para considerar un
# pixel como fondo. El damero alterna dos tonos claros: (246..248) y (253..255)
# → dist 0..15.3. Los blancos puros del sprite (dist 15.3) se conservan porque
# están ENCERRADOS por el cuerpo y el flood-fill no puede alcanzarlos.
FUZZ_BG = 16.0

# Límites del arte de cada celda en el preview 1254x1254 (medidos sobre la
# imagen; se expanden PAD px). Las etiquetas rojas y restos del damero se
# eliminan después con el filtro del componente más grande.
ART_BOXES = [
    (60, 29, 284, 295),     # 0
    (360, 29, 582, 294),    # 1
    (666, 32, 893, 291),    # 2
    (951, 44, 1172, 294),   # 3
    (68, 341, 306, 599),    # 4
    (376, 340, 605, 598),   # 5
    (645, 374, 894, 582),   # 6
    (946, 368, 1186, 568),  # 7
    (74, 631, 295, 905),    # 8
    (382, 633, 611, 905),   # 9
    (671, 646, 878, 905),   # 10
    (957, 638, 1168, 907),  # 11
    (57, 974, 309, 1209),   # 12
    (361, 975, 603, 1209),  # 13
    (639, 974, 895, 1213),  # 14
    (942, 976, 1188, 1210), # 15
]
BOX_PAD = 10


def flood_background(bg: np.ndarray) -> np.ndarray:
    """Píxeles de fondo alcanzables desde el borde de la imagen (4-conectado)."""
    filled = np.zeros_like(bg, dtype=bool)
    filled[0, :] = filled[-1, :] = filled[:, 0] = filled[:, -1] = True
    filled &= bg
    frontier = filled.copy()
    while frontier.any():
        grown = np.zeros_like(frontier)
        grown[1:, :] |= frontier[:-1, :]
        grown[:-1, :] |= frontier[1:, :]
        grown[:, 1:] |= frontier[:, :-1]
        grown[:, :-1] |= frontier[:, 1:]
        newly = grown & bg & ~filled
        filled |= newly
        frontier = newly
    return filled


def largest_component(mask: np.ndarray) -> np.ndarray:
    """Conserva solo el componente conectado más grande de la máscara."""
    seen = np.zeros_like(mask, dtype=bool)
    best = None
    best_size = 0
    for sy in range(mask.shape[0]):
        for sx in range(mask.shape[1]):
            if not mask[sy, sx] or seen[sy, sx]:
                continue
            queue = deque([(sy, sx)])
            seen[sy, sx] = True
            count = 0
            ys: list[int] = []
            xs: list[int] = []
            while queue:
                y, x = queue.popleft()
                count += 1
                ys.append(y)
                xs.append(x)
                for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < mask.shape[0] and 0 <= nx < mask.shape[1] and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        queue.append((ny, nx))
            if count > best_size:
                best_size = count
                best = (np.array(ys), np.array(xs))
    out = np.zeros_like(mask)
    if best is not None:
        out[best[0], best[1]] = True
    return out


def extract_pose(source: np.ndarray, box: tuple[int, int, int, int]) -> np.ndarray:
    """Recorta una celda, elimina el fondo del damero y devuelve el sprite RGBA."""
    x0, y0, x1, y1 = box
    crop = source[y0:y1, x0:x1].astype(np.int32)
    h, w, _ = crop.shape
    # La referencia es el tono gris más común del recorte (el damero domina),
    # nunca la esquina: las cajas de los frames 3 y 7 empiezan sobre la etiqueta.
    gray = (np.abs(crop[..., 0] - crop[..., 1]) < 8) & (np.abs(crop[..., 1] - crop[..., 2]) < 8)
    gray_px = crop[gray]
    if len(gray_px) == 0:
        gray_px = crop[:1, :1]
    colors, counts = np.unique(gray_px.reshape(-1, 3), axis=0, return_counts=True)
    ref = colors[counts.argmax()]
    dist = np.sqrt(((crop - ref) ** 2).sum(axis=2))
    bg = dist <= FUZZ_BG
    background = flood_background(bg)
    keep = ~background
    alpha = keep.astype(np.uint8) * 255
    rgba = np.zeros((h, w, 4), dtype=np.uint8)
    rgba[..., :3] = np.clip(crop, 0, 255)
    rgba[..., 3] = alpha
    rgba[..., 3] = largest_component(alpha > SOLID_ALPHA).astype(np.uint8) * 255
    return rgba


def place_pose(rgba: np.ndarray, scale: float, cell: np.ndarray) -> tuple[int, int, int, int]:
    """Escala el sprite y lo coloca centrado con base en BASE_Y. Devuelve bbox."""
    solid = rgba[..., 3] > SOLID_ALPHA
    ys, xs = np.nonzero(solid)
    if len(xs) == 0:
        raise ValueError("pose vacía")
    x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
    content = rgba[y0 : y1 + 1, x0 : x1 + 1]
    new_w = max(1, round(content.shape[1] * scale))
    new_h = max(1, round(content.shape[0] * scale))
    scaled = np.asarray(
        Image.fromarray(content, "RGBA").resize((new_w, new_h), Image.Resampling.LANCZOS),
        dtype=np.uint8,
    )
    s_solid = scaled[..., 3] > SOLID_ALPHA
    s_ys, s_xs = np.nonzero(s_solid)
    s_h = int(s_ys.max() - s_ys.min()) + 1
    s_w = int(s_xs.max() - s_xs.min()) + 1
    paste_x = (CELL - s_w) // 2
    paste_y = BASE_Y - s_h + 1
    if paste_y < 0:
        raise ValueError(f"pose de {s_h}px no cabe con base en {BASE_Y}")
    cell[paste_y : paste_y + new_h, paste_x : paste_x + new_w] = scaled
    return paste_x, paste_y, paste_x + s_w - 1, paste_y + s_h - 1


def write_backed_up(path: Path, image: Image.Image) -> None:
    if path.exists():
        BACKUP_DIR.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, BACKUP_DIR / path.name)
    image.save(path, optimize=True)
    print(f"Written: {path}")


def build() -> None:
    source = np.asarray(Image.open(SOURCE).convert("RGB"), dtype=np.uint8)
    sh, sw = source.shape[:2]

    poses: list[np.ndarray] = []
    for index in range(FRAME_COUNT):
        x0, y0, x1, y1 = ART_BOXES[index]
        box = (
            max(0, x0 - BOX_PAD),
            max(0, y0 - BOX_PAD),
            min(sw, x1 + 1 + BOX_PAD),
            min(sh, y1 + 1 + BOX_PAD),
        )
        poses.append(extract_pose(source, box))

    # Factor único: la pose más alta debe caber con margen mínimo y base en 232.
    heights = []
    widths = []
    for pose in poses:
        solid = pose[..., 3] > SOLID_ALPHA
        ys, xs = np.nonzero(solid)
        heights.append(int(ys.max() - ys.min()) + 1)
        widths.append(int(xs.max() - xs.min()) + 1)
    scale = min(1.0, (BASE_Y + 1) / max(heights), (CELL - 2 * MIN_MARGIN) / max(widths))
    print(f"Escala compartida: {scale:.4f} (alturas {min(heights)}..{max(heights)}, anchos {min(widths)}..{max(widths)})")

    atlas = np.zeros((CELL * ROWS, CELL * COLUMNS, 4), dtype=np.uint8)
    report: list[dict[str, object]] = []
    for index, pose in enumerate(poses):
        cell = np.zeros((CELL, CELL, 4), dtype=np.uint8)
        bbox = place_pose(pose, scale, cell)
        row = index // COLUMNS
        col = index % COLUMNS
        atlas[row * CELL : (row + 1) * CELL, col * CELL : (col + 1) * CELL] = cell
        x0, y0, x1, y1 = bbox
        report.append(
            {
                "index": index,
                "bbox": bbox,
                "base_y": y1,
                "content_fraction": round((y1 - y0) / CELL, 4),
            }
        )

    write_backed_up(ATLAS_PATH, Image.fromarray(atlas, "RGBA"))
    build_avatar(poses[0], scale)
    write_preview(atlas, report)

    print("=== Taro atlas report (index, bbox, base_y, content_fraction) ===")
    for entry in report:
        print(entry)


def build_avatar(frame0: np.ndarray, scale: float) -> None:
    solid = frame0[..., 3] > SOLID_ALPHA
    ys, xs = np.nonzero(solid)
    x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
    content = Image.fromarray(frame0[y0 : y1 + 1, x0 : x1 + 1], "RGBA")
    new_w = max(1, round(content.width * scale))
    new_h = max(1, round(content.height * scale))
    resized = content.resize((new_w, new_h), Image.Resampling.LANCZOS)
    # Escala al alto de referencia del resto de avatares (Piru usa 470 -> 491).
    target_h = 470
    ratio = target_h / resized.height
    if ratio > 1.0:
        resized = resized.resize(
            (max(1, round(resized.width * ratio)), max(1, round(resized.height * ratio))),
            Image.Resampling.LANCZOS,
        )
    canvas = Image.new("RGBA", (AVATAR_SIZE, AVATAR_SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(resized, ((AVATAR_SIZE - resized.width) // 2, AVATAR_BOTTOM - resized.height))
    write_backed_up(AVATAR_PATH, canvas)
    print(f"Avatar bbox: {canvas.getchannel('A').point(lambda v: 255 if v > SOLID_ALPHA else 0).getbbox()}")


def write_preview(atlas: np.ndarray, report: list[dict[str, object]]) -> None:
    gap = 8
    preview = Image.new("RGBA", (COLUMNS * (CELL + gap) + gap, ROWS * (CELL + gap) + gap), (18, 18, 26, 255))
    checker = Image.new("RGBA", (CELL, CELL), (230, 230, 236, 255))
    for y in range(0, CELL, 24):
        for x in range(0, CELL, 24):
            if (x // 24 + y // 24) % 2 == 0:
                checker.paste((255, 255, 255, 255), (x, y, x + 24, y + 24))
    draw = ImageDraw.Draw(preview)
    for index in range(FRAME_COUNT):
        row = index // COLUMNS
        col = index % COLUMNS
        ox = gap + col * (CELL + gap)
        oy = gap + row * (CELL + gap)
        preview.alpha_composite(checker, (ox, oy))
        cell = atlas[row * CELL : (row + 1) * CELL, col * CELL : (col + 1) * CELL]
        preview.alpha_composite(Image.fromarray(cell, "RGBA"), (ox, oy))
        draw.text((ox + 4, oy + 2), str(index), fill=(230, 60, 60, 255))
    preview.convert("RGB").save(TOOLS_DIR / "taro_final_preview.png", optimize=True)
    print(f"Preview: {TOOLS_DIR / 'taro_final_preview.png'}")


def main() -> int:
    build()
    return 0


if __name__ == "__main__":
    sys.exit(main())
