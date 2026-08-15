#!/usr/bin/env python3
"""Normalize Tela V2 boards, build the 40-frame atlas, and write debug assets."""

from __future__ import annotations

import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


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
BACKGROUND_DISTANCE = 32.0

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
    # Remove only the lower tail of the floor-plane cast shadow. The character
    # silhouette reaches the contact band through the legs, so a broad flood
    # fill would punch holes in white fur. In the normalized floor poses the
    # remaining matte is a neutral, dark horizontal ellipse below y=346 and
    # inside the central body span; highlights above that line remain intact.
    if name.startswith(("idle_", "walk_")):
        before_floor_cleanup = pixels.copy()
        rgb = pixels[:, :, :3].astype(np.int16)
        saturation = rgb.max(axis=2) - rgb.min(axis=2)
        value = rgb.max(axis=2)
        yy, xx = np.indices((FRAME_SIZE, FRAME_SIZE))
        threshold = 0.81 if name == "idle_02" else (0.84 if name.startswith("idle_") else 0.86)
        right_bound = 0.90 if name == "walk_06" else 0.75
        left_bound = 0.38 if name == "walk_01" else 0.25
        if name == "walk_01":
            threshold = 0.90
        floor_shadow = (
            (pixels[:, :, 3] > 8)
            & (saturation < 60)
            & (yy >= int(FRAME_SIZE * threshold))
            & (xx >= int(FRAME_SIZE * left_bound))
            & (xx <= int(FRAME_SIZE * right_bound))
        )
        if name == "walk_01":
            floor_shadow |= (
                (pixels[:, :, 3] > 8)
                & (saturation < 60)
                & (value < 230)
                & (yy >= int(FRAME_SIZE * 0.84))
                & (xx >= int(FRAME_SIZE * 0.35))
                & (xx <= int(FRAME_SIZE * 0.65))
            )
        pixels[floor_shadow, 3] = 0
        pixels[floor_shadow, :3] = 0

        # Las fuentes de suelo tienen una segunda variante de matte: una
        # sombra clara queda unida a los pies y, al normalizar por el bbox,
        # termina convertida en una franja blanca debajo de Tela. No basta con
        # buscar componentes desconectados porque esta sombra toca la silueta.
        # Localizamos sus islas neutras en la cola inferior del frame. Las
        # iluminaciones pequeñas de las patas no cumplen el tamaño horizontal
        # mínimo y se conservan.
        visible = pixels[:, :, 3] > 8
        visible_y = np.where(visible)[0]
        if visible_y.size:
            visible_bottom = int(visible_y.max())
            matte_candidates = (
                visible
                & (saturation < 72)
                & (value > 170)
                & (yy >= max(0, visible_bottom - 46))
            )
            visited = np.zeros_like(matte_candidates, dtype=bool)
            for seed_y, seed_x in zip(*np.where(matte_candidates)):
                if visited[seed_y, seed_x]:
                    continue
                queue = deque([(int(seed_y), int(seed_x))])
                visited[seed_y, seed_x] = True
                component: list[tuple[int, int]] = []
                while queue:
                    current_y, current_x = queue.pop()
                    component.append((current_y, current_x))
                    for next_y, next_x in (
                        (current_y - 1, current_x),
                        (current_y + 1, current_x),
                        (current_y, current_x - 1),
                        (current_y, current_x + 1),
                    ):
                        if (
                            0 <= next_y < FRAME_SIZE
                            and 0 <= next_x < FRAME_SIZE
                            and matte_candidates[next_y, next_x]
                            and not visited[next_y, next_x]
                        ):
                            visited[next_y, next_x] = True
                            queue.append((next_y, next_x))
                component_y = np.fromiter((point[0] for point in component), dtype=np.int16)
                component_x = np.fromiter((point[1] for point in component), dtype=np.int16)
                component_width = int(component_x.max()) - int(component_x.min()) + 1
                component_height = int(component_y.max()) - int(component_y.min()) + 1
                is_floor_matte = (
                    len(component) >= 20
                    and component_width >= 12
                    and component_height >= 3
                    and int(component_y.max()) >= visible_bottom - 15
                )
                if is_floor_matte:
                    pixels[component_y, component_x, 3] = 0
                    pixels[component_y, component_x, :3] = 0

            # Una sombra puede atravesar el punto de unión de una pata al
            # abdomen. Si la limpieza ha separado una pata real, restaura el
            # camino mínimo de píxeles originales hasta el componente mayor;
            # así quitamos la mancha sin publicar componentes desconectados.
            before_solid = before_floor_cleanup[:, :, 3] > 8
            after_solid = pixels[:, :, 3] > 8
            removed = before_solid & ~after_solid
            components: list[list[tuple[int, int]]] = []
            visited = np.zeros_like(after_solid, dtype=bool)
            for seed_y, seed_x in zip(*np.where(after_solid)):
                if visited[seed_y, seed_x]:
                    continue
                queue = deque([(int(seed_y), int(seed_x))])
                visited[seed_y, seed_x] = True
                component: list[tuple[int, int]] = []
                while queue:
                    current_y, current_x = queue.pop()
                    component.append((current_y, current_x))
                    for next_y, next_x in (
                        (current_y - 1, current_x),
                        (current_y + 1, current_x),
                        (current_y, current_x - 1),
                        (current_y, current_x + 1),
                    ):
                        if (
                            0 <= next_y < FRAME_SIZE
                            and 0 <= next_x < FRAME_SIZE
                            and after_solid[next_y, next_x]
                            and not visited[next_y, next_x]
                        ):
                            visited[next_y, next_x] = True
                            queue.append((next_y, next_x))
                components.append(component)
            components.sort(key=len, reverse=True)
            if components:
                largest_component = set(components[0])
                for detached_component in components[1:]:
                    if len(detached_component) <= 64:
                        continue
                    path_queue = deque(detached_component)
                    previous: dict[tuple[int, int], tuple[int, int] | None] = {
                        point: None for point in detached_component
                    }
                    path_end: tuple[int, int] | None = None
                    while path_queue and path_end is None:
                        current_y, current_x = path_queue.popleft()
                        for next_y, next_x in (
                            (current_y - 1, current_x),
                            (current_y + 1, current_x),
                            (current_y, current_x - 1),
                            (current_y, current_x + 1),
                        ):
                            if not (0 <= next_y < FRAME_SIZE and 0 <= next_x < FRAME_SIZE):
                                continue
                            next_point = (next_y, next_x)
                            if next_point in largest_component:
                                path_end = (current_y, current_x)
                                break
                            if removed[next_y, next_x] and next_point not in previous:
                                previous[next_point] = (current_y, current_x)
                                path_queue.append(next_point)
                    if path_end is not None:
                        point: tuple[int, int] | None = path_end
                        while point is not None:
                            point_y, point_x = point
                            pixels[point_y, point_x] = before_floor_cleanup[point_y, point_x]
                            point = previous[point]
    # El atlas no puede transportar sombras de la mesa de generación. Solo
    # quitamos grises neutros en la franja inferior que no están cerca de una
    # zona cromática del personaje; los brillos violetas de patas y ojos se
    # conservan porque tienen saturación alta.
    rgb = pixels[:, :, :3].astype(np.int16)
    saturation = rgb.max(axis=2) - rgb.min(axis=2)
    value = rgb.max(axis=2)
    lower = np.zeros((FRAME_SIZE, FRAME_SIZE), dtype=bool)
    lower[int(FRAME_SIZE * 0.72):, :] = True
    # En los ciclos de seda algunas líneas verticales quedan pegadas al borde
    # superior del personaje y por eso no aparecen como componente separado.
    # Eliminamos únicamente runs largos, estrechos y casi blancos del margen
    # superior; nunca tocamos el pelo coloreado ni las patas.
    silk_frame = name.startswith(("web_descend_", "web_hang_", "web_ascend_"))
    for x in range(FRAME_SIZE):
        column = (
            (pixels[:, x, 3] > 8) & (saturation[:, x] < 45) & (value[:, x] > 180)
            if silk_frame
            else np.zeros(FRAME_SIZE, dtype=bool)
        )
        upper_column = column[: int(FRAME_SIZE * 0.32)]
        if int(upper_column.sum()) >= 20:
            upper_indices = np.where(upper_column)[0]
            pixels[upper_indices, x, 3] = 0
            pixels[upper_indices, x, :3] = 0
        run_start = None
        for y in range(FRAME_SIZE + 1):
            active = y < FRAME_SIZE and bool(column[y])
            if active and run_start is None:
                run_start = y
            elif not active and run_start is not None:
                if run_start <= int(FRAME_SIZE * 0.22) and y - run_start >= 40:
                    pixels[run_start:y, x, 3] = 0
                    pixels[run_start:y, x, :3] = 0
                run_start = None
    # Descarta residuos neutros desconectados (la sombra de la fuente suele
    # quedar como un óvalo separado bajo el abdomen). Los brillos blancos de
    # ojos/patas permanecen porque no están en la franja inferior.
    alpha_mask = pixels[:, :, 3] > 8
    components = []
    visited = np.zeros_like(alpha_mask, dtype=bool)
    for sy, sx in zip(*np.where(alpha_mask)):
        if visited[sy, sx]:
            continue
        stack = [(int(sy), int(sx))]
        visited[sy, sx] = True
        component = []
        while stack:
            cy, cx = stack.pop()
            component.append((cy, cx))
            for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                if 0 <= ny < FRAME_SIZE and 0 <= nx < FRAME_SIZE and alpha_mask[ny, nx] and not visited[ny, nx]:
                    visited[ny, nx] = True
                    stack.append((ny, nx))
        components.append(component)
    if components:
        largest = max(components, key=len)
        for component in components:
            if component is largest:
                continue
            ys = np.fromiter((point[0] for point in component), dtype=np.int16)
            xs = np.fromiter((point[1] for point in component), dtype=np.int16)
            mean_saturation = float(saturation[ys, xs].mean())
            mean_value = float(value[ys, xs].mean())
            thin_thread = (int(xs.max()) - int(xs.min()) + 1) <= 8 and (int(ys.max()) - int(ys.min()) + 1) >= 40
            horizontal_thread = (
                (int(ys.max()) - int(ys.min()) + 1) <= 3
                and (int(xs.max()) - int(xs.min()) + 1) >= 40
                and int(ys.min()) <= int(FRAME_SIZE * 0.22)
            )
            matte_fragment = mean_saturation < 80 and mean_value > 220
            clipped_fragment = int(ys.max()) >= FRAME_SIZE - PADDING - 1 and len(component) >= 24
            if (thin_thread or horizontal_thread or matte_fragment or clipped_fragment) and mean_saturation < 80:
                pixels[ys, xs, 3] = 0
            elif len(component) >= 24 and float(ys.mean()) > FRAME_SIZE * 0.68 and mean_saturation < 45:
                pixels[ys, xs, 3] = 0
        pixels[pixels[:, :, 3] == 0, :3] = 0
    # Elimina el borde de matte casi transparente que aparece al componer
    # sobre blanco; el atlas conserva antialiasing útil a partir de 64/255.
    low_alpha = pixels[:, :, 3] < 64
    pixels[low_alpha, 3] = 0
    pixels[low_alpha, :3] = 0
    # La eliminación del matte puede partir un componente en dos; repite la
    # limpieza sobre la máscara final para no publicar fragmentos flotantes.
    final_mask = pixels[:, :, 3] > 8
    final_components = []
    visited = np.zeros_like(final_mask, dtype=bool)
    for sy, sx in zip(*np.where(final_mask)):
        if visited[sy, sx]:
            continue
        stack = [(int(sy), int(sx))]
        visited[sy, sx] = True
        component = []
        while stack:
            cy, cx = stack.pop()
            component.append((cy, cx))
            for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                if 0 <= ny < FRAME_SIZE and 0 <= nx < FRAME_SIZE and final_mask[ny, nx] and not visited[ny, nx]:
                    visited[ny, nx] = True
                    stack.append((ny, nx))
        final_components.append(component)
    if final_components:
        largest = max(final_components, key=len)
        for component in final_components:
            if component is largest:
                continue
            ys = np.fromiter((point[0] for point in component), dtype=np.int16)
            xs = np.fromiter((point[1] for point in component), dtype=np.int16)
            fragment_saturation = float(saturation[ys, xs].mean())
            fragment_value = float(value[ys, xs].mean())
            touches_bottom = int(ys.max()) >= FRAME_SIZE - PADDING - 1
            if (fragment_saturation < 80 and fragment_value > 220) or touches_bottom:
                pixels[ys, xs, 3] = 0
                pixels[ys, xs, :3] = 0

    if name.startswith("walk_"):
        def remove_light_matte() -> None:
            # The dilation protects genuine white fur/highlights by requiring a
            # nearby chromatic or dark leg edge before a neutral pixel can be
            # discarded.
            rgb = pixels[:, :, :3].astype(np.int16)
            saturation = rgb.max(axis=2) - rgb.min(axis=2)
            value = rgb.max(axis=2)
            chromatic_anchor = (
                (pixels[:, :, 3] > 8) & ((saturation >= 50) | (value < 145))
            ).astype(np.uint8) * 255
            protected = np.asarray(Image.fromarray(chromatic_anchor).filter(ImageFilter.MaxFilter(17))) > 0
            y_grid = np.indices((FRAME_SIZE, FRAME_SIZE))[0]
            light_matte = (
                (pixels[:, :, 3] > 8)
                & (saturation < 50)
                & (value > 145)
                & (y_grid >= int(FRAME_SIZE * 0.80))
                & ~protected
            )
            pixels[light_matte, 3] = 0
            pixels[light_matte, :3] = 0

        def anchor_walk() -> None:
            nonlocal pixels
            visible = pixels[:, :, 3] > 8
            visible_y, visible_x = np.where(visible)
            if not visible_y.size:
                return
            left = int(visible_x.min())
            right = int(visible_x.max()) + 1
            bottom = int(visible_y.max()) + 1
            dx = int(round(FRAME_SIZE / 2 - (left + right) / 2))
            dy = (FRAME_SIZE - PADDING) - bottom
            if not (dx or dy):
                return
            anchored = np.zeros_like(pixels)
            source_left = max(0, -dx)
            source_top = max(0, -dy)
            source_right = min(FRAME_SIZE, FRAME_SIZE - dx)
            source_bottom = min(FRAME_SIZE, FRAME_SIZE - dy)
            if source_left < source_right and source_top < source_bottom:
                target_left = source_left + dx
                target_top = source_top + dy
                target_right = source_right + dx
                target_bottom = source_bottom + dy
                anchored[target_top:target_bottom, target_left:target_right] = pixels[
                    source_top:source_bottom, source_left:source_right
                ]
                pixels = anchored

        # Anchor, remove matte now that it is in the contact band, then anchor
        # once more in case the cleanup removed the old bottom-most shadow row.
        anchor_walk()
        remove_light_matte()
        anchor_walk()
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
            "drawScale": 0.963,
            "backgroundRemoval": "exterior_background_flood_fill",
        },
        "contactAnchors": [
            {
                "clip": "walk",
                "bottomTolerance": 1,
                "centerTolerance": 1,
                "reason": "floor walk poses share a stable leg contact and pivot",
            }
        ],
        "qualityExceptions": {
            "interiorAlphaRegions": [
                {"frame": 1, "bounds": [50, 260, 95, 320], "reason": "leg separation"},
                {"frame": 3, "bounds": [230, 180, 270, 240], "reason": "front leg separation"},
                {"frame": 11, "bounds": [78, 206, 261, 352], "reason": "floor-walk leg separation after matte cleanup"},
                {"frame": 12, "bounds": [150, 60, 225, 160], "reason": "wall-climb leg separation"},
                {"frame": 13, "bounds": [140, 70, 230, 145], "reason": "wall-climb leg separation"},
                {"frame": 14, "bounds": [120, 70, 230, 140], "reason": "wall-climb leg separation"},
                {"frame": 15, "bounds": [130, 50, 240, 160], "reason": "wall-climb leg separation"},
                {"frame": 16, "bounds": [270, 155, 320, 205], "reason": "ceiling leg separation"},
                {"frame": 36, "bounds": [100, 220, 165, 270], "reason": "sleeping leg separation"},
                {"frame": 37, "bounds": [250, 140, 315, 210], "reason": "sleeping leg separation"},
            ],
            "disconnectedRegions": [
                {"frame": 14, "bounds": [175, 75, 205, 105], "reason": "intentional facial mark"},
            ],
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

    # Keep one portable asset path so the approved JSON can be promoted
    # byte-for-byte to debug and main assets.
    spec = build_spec("pets/tela/tela_motion_v2.png")
    spec["frameDetails"] = details
    SPEC_PATH.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
    debug_spec = spec
    DEBUG_SPEC_PATH.write_text(json.dumps(debug_spec, indent=2) + "\n", encoding="utf-8")
    atlas.save(DEBUG_ATLAS_PATH, optimize=True)
    print(f"TELA_V2_BUILT frames={len(frames)} atlas={ATLAS_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
