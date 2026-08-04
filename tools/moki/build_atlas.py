#!/usr/bin/env python3
"""Split generated Moki boards, remove backgrounds, and build the debug atlas."""

from __future__ import annotations

import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "tools/moki/raw"
CLEAN_DIR = ROOT / "tools/moki/clean"
ATLAS_PATH = ROOT / "app/src/debug/assets/pets/moki/moki_sheet_v1.png"
SPEC_PATH = ROOT / "app/src/debug/assets/pets/moki/moki_sheet_v1.json"
PREVIEW_PATH = ROOT / "app/src/debug/res/drawable-nodpi/pet_moki.png"
CONTACT_SHEET_PATH = CLEAN_DIR / "moki_frames_preview.png"

SOURCE_CELL_SIZE = 512
CELL_SIZE = 384
ATLAS_COLUMNS = 5
BACKGROUND_DISTANCE = 68.0
CELL_INSET = 12

FRAMES = (
    ("board_01_perch.png", 0, "perch_neutral"),
    ("board_01_perch.png", 1, "perch_breathe"),
    ("board_01_perch.png", 2, "perch_scan_left"),
    ("board_01_perch.png", 3, "perch_scan_right"),
    ("board_02_crawl.png", 0, "crawl_reach"),
    ("board_02_crawl.png", 1, "crawl_plant"),
    ("board_02_crawl.png", 2, "crawl_follow"),
    ("board_02_crawl.png", 3, "crawl_recover"),
    ("board_03_corner_v2.png", 0, "corner_approach"),
    ("board_03_corner_v2.png", 1, "corner_front_plant"),
    ("board_03_corner_v2.png", 2, "corner_rear_follow"),
    ("board_03_corner_v2.png", 3, "corner_settle"),
    ("board_04_camouflage_tongue.png", 0, "camouflage_hold"),
    ("board_04_camouflage_tongue.png", 1, "tongue_aim"),
    ("board_04_camouflage_tongue.png", 2, "tongue_extend"),
    ("board_04_camouflage_tongue.png", 3, "tongue_contact"),
    ("board_05_reactions.png", 0, "tongue_retract"),
    ("board_05_reactions.png", 1, "drag_resist"),
    ("board_05_reactions.png", 2, "fling_tuck"),
    ("board_05_reactions.png", 3, "cling_land"),
)


def get_border_reference(rgb: np.ndarray) -> np.ndarray:
    border = np.concatenate(
        (rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3), rgb[:, :8].reshape(-1, 3), rgb[:, -8:].reshape(-1, 3))
    )
    return np.median(border, axis=0).astype(np.float32)


def get_exterior_mask(matches_background: np.ndarray) -> np.ndarray:
    height, width = matches_background.shape
    exterior = np.zeros((height, width), dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    def add_pixel(y: int, x: int) -> None:
        if matches_background[y, x] and not exterior[y, x]:
            exterior[y, x] = True
            queue.append((y, x))

    for x in range(width):
        add_pixel(0, x)
        add_pixel(height - 1, x)
    for y in range(height):
        add_pixel(y, 0)
        add_pixel(y, width - 1)
    while queue:
        y, x = queue.popleft()
        for delta_y in (-1, 0, 1):
            for delta_x in (-1, 0, 1):
                if delta_x == 0 and delta_y == 0:
                    continue
                next_y = y + delta_y
                next_x = x + delta_x
                if 0 <= next_y < height and 0 <= next_x < width:
                    add_pixel(next_y, next_x)
    return exterior


def remove_white_background(image: Image.Image, keep_only_largest: bool = True) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    rgb = rgba[:, :, :3].astype(np.float32)
    background = get_border_reference(rgb)
    distance = np.linalg.norm(rgb - background, axis=2)
    exterior = get_exterior_mask(distance < BACKGROUND_DISTANCE)
    rgba[exterior, 3] = 0
    rgba[rgba[:, :, 3] == 0, :3] = 0
    result = Image.fromarray(rgba, "RGBA")
    return keep_largest_component(result) if keep_only_largest else result


def get_components(foreground: np.ndarray) -> list[list[tuple[int, int]]]:
    height, width = foreground.shape
    visited = np.zeros((height, width), dtype=bool)
    components: list[list[tuple[int, int]]] = []
    for start_y, start_x in np.argwhere(foreground):
        if visited[start_y, start_x]:
            continue
        component: list[tuple[int, int]] = []
        queue: deque[tuple[int, int]] = deque([(int(start_y), int(start_x))])
        visited[start_y, start_x] = True
        while queue:
            y, x = queue.popleft()
            component.append((y, x))
            for delta_y in (-1, 0, 1):
                for delta_x in (-1, 0, 1):
                    next_y = y + delta_y
                    next_x = x + delta_x
                    if 0 <= next_y < height and 0 <= next_x < width:
                        if foreground[next_y, next_x] and not visited[next_y, next_x]:
                            visited[next_y, next_x] = True
                            queue.append((next_y, next_x))
        components.append(component)
    return components


def keep_largest_component(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image).copy()
    height, width = rgba.shape[:2]
    components = get_components(rgba[:, :, 3] > 8)
    largest_component = max(components, key=len, default=[])
    keep_mask = np.zeros((height, width), dtype=bool)
    if largest_component:
        component_y, component_x = zip(*largest_component)
        keep_mask[np.asarray(component_y), np.asarray(component_x)] = True
    rgba[~keep_mask] = 0
    return Image.fromarray(rgba, "RGBA")


def extract_board_subjects(board: Image.Image) -> list[Image.Image]:
    cleaned = remove_white_background(board, keep_only_largest=False)
    rgba = np.asarray(cleaned)
    components = sorted(get_components(rgba[:, :, 3] > 8), key=len, reverse=True)[:4]
    if len(components) != 4:
        raise ValueError(f"Expected four subjects, found {len(components)}")
    subjects: list[tuple[float, float, Image.Image]] = []
    for component in components:
        component_y, component_x = zip(*component)
        left = max(0, min(component_x) - 4)
        top = max(0, min(component_y) - 4)
        right = min(cleaned.width, max(component_x) + 5)
        bottom = min(cleaned.height, max(component_y) + 5)
        isolated = Image.new("RGBA", cleaned.size, (0, 0, 0, 0))
        isolated_pixels = np.asarray(isolated).copy()
        isolated_pixels[np.asarray(component_y), np.asarray(component_x)] = rgba[
            np.asarray(component_y), np.asarray(component_x)
        ]
        crop = Image.fromarray(isolated_pixels, "RGBA").crop((left, top, right, bottom))
        scale = min(440 / crop.width, 440 / crop.height)
        resized = crop.resize(
            (max(1, round(crop.width * scale)), max(1, round(crop.height * scale))),
            Image.Resampling.LANCZOS,
        )
        canvas = Image.new("RGBA", (SOURCE_CELL_SIZE, SOURCE_CELL_SIZE), (0, 0, 0, 0))
        canvas.alpha_composite(resized, ((SOURCE_CELL_SIZE - resized.width) // 2, (SOURCE_CELL_SIZE - resized.height) // 2))
        subjects.append((sum(component_y) / len(component), sum(component_x) / len(component), canvas))
    subjects.sort(key=lambda item: (0 if item[0] < board.height / 2 else 1, item[1]))
    return [subject for _, _, subject in subjects]


def create_preview(frame: Image.Image) -> Image.Image:
    alpha = frame.getchannel("A")
    bounds = alpha.getbbox()
    if bounds is None:
        return Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    cropped = frame.crop(bounds)
    scale = min(440 / cropped.width, 440 / cropped.height)
    resized = cropped.resize(
        (max(1, round(cropped.width * scale)), max(1, round(cropped.height * scale))),
        Image.Resampling.LANCZOS,
    )
    preview = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    preview.alpha_composite(resized, ((512 - resized.width) // 2, (512 - resized.height) // 2))
    return preview


def get_alpha_geometry(frame: Image.Image) -> tuple[tuple[int, int, int, int], tuple[float, float]]:
    alpha = np.asarray(frame.getchannel("A"), dtype=np.float32)
    coordinates = np.argwhere(alpha > 8)
    if coordinates.size == 0:
        return (0, 0, frame.width, frame.height), (frame.width / 2, frame.height / 2)
    weights = alpha[coordinates[:, 0], coordinates[:, 1]]
    center_y = float(np.average(coordinates[:, 0], weights=weights))
    center_x = float(np.average(coordinates[:, 1], weights=weights))
    bounds = frame.getchannel("A").getbbox() or (0, 0, frame.width, frame.height)
    return bounds, (center_x, center_y)


def translate_frame(frame: Image.Image, frame_name: str) -> Image.Image:
    bounds, center = get_alpha_geometry(frame)
    left, top, right, bottom = bounds
    if frame_name.startswith("perch"):
        offset_x = round(CELL_SIZE / 2 - (left + right) / 2)
        offset_y = 348 - bottom
    elif frame_name.startswith("crawl"):
        offset_x = round(CELL_SIZE / 2 - (left + right) / 2)
        offset_y = 330 - bottom
    elif frame_name.startswith("corner") or frame_name == "fling_tuck":
        offset_x = round(CELL_SIZE / 2 - center[0])
        offset_y = round(CELL_SIZE / 2 - center[1])
    elif frame_name.startswith("tongue"):
        offset_x = round(145 - center[0])
        offset_y = 330 - bottom
    else:
        offset_x = round(CELL_SIZE / 2 - center[0])
        offset_y = 330 - bottom
    offset_x = max(8 - left, min(offset_x, CELL_SIZE - 8 - right))
    offset_y = max(8 - top, min(offset_y, CELL_SIZE - 8 - bottom))
    positioned = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0))
    positioned.alpha_composite(frame, (offset_x, offset_y))
    return positioned


def prepare_frame(source_cell: Image.Image, frame_name: str) -> Image.Image:
    cleaned = remove_white_background(source_cell)
    inner_size = CELL_SIZE - CELL_INSET * 2
    resized = cleaned.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0))
    frame.alpha_composite(resized, (CELL_INSET, CELL_INSET))
    positioned = translate_frame(frame, frame_name)
    if frame_name.startswith("corner"):
        return normalize_centered_content(positioned, target_extent=280)
    return positioned


def normalize_centered_content(frame: Image.Image, target_extent: int) -> Image.Image:
    bounds = frame.getchannel("A").getbbox()
    if bounds is None:
        return frame
    cropped = frame.crop(bounds)
    scale = target_extent / max(cropped.width, cropped.height)
    resized = cropped.resize(
        (max(1, round(cropped.width * scale)), max(1, round(cropped.height * scale))),
        Image.Resampling.LANCZOS,
    )
    normalized = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0))
    normalized.alpha_composite(resized, ((CELL_SIZE - resized.width) // 2, (CELL_SIZE - resized.height) // 2))
    return normalized


def tint_moki(frame: Image.Image, amount: float) -> Image.Image:
    rgba = np.asarray(frame).copy()
    rgb = rgba[:, :, :3].astype(np.float32)
    mint_mask = (
        (rgba[:, :, 3] > 8)
        & (rgb[:, :, 1] > rgb[:, :, 0] + 12)
        & (rgb[:, :, 2] > rgb[:, :, 0] + 4)
    )
    target = np.array([208.0, 226.0, 218.0], dtype=np.float32)
    rgb[mint_mask] = rgb[mint_mask] * (1.0 - amount) + target * amount
    rgba[:, :, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def build_spec() -> dict[str, object]:
    return {
        "version": 1,
        "petId": "moki",
        "atlasPath": "pets/moki/moki_sheet_v1.png",
        "previewPath": "res://drawable-nodpi/pet_moki.png",
        "frameWidth": CELL_SIZE,
        "frameHeight": CELL_SIZE,
        "columns": ATLAS_COLUMNS,
        "rows": 4,
        "frameCount": len(FRAMES),
        "pivot": {"x": CELL_SIZE // 2, "y": round(CELL_SIZE * 0.78)},
        "renderHints": {
            "innerTransparentPaddingPx": 8,
            "recommendedBleedInsetPx": 1,
            "filterBitmap": True,
        },
        "clips": [
            {"id": "perch_loop", "frames": [0, 1, 2, 1, 0, 3], "loop": True, "frameDurationMs": 260},
            {"id": "crawl_loop", "frames": [4, 5, 6, 7], "loop": True, "frameDurationMs": 150},
            {"id": "corner_turn", "frames": [8, 9, 10, 11], "loop": False, "frameDurationMs": 210},
            {"id": "camouflage", "frames": [0, 12, 0], "loop": False, "frameDurationMs": 420},
            {"id": "tongue_strike", "frames": [13, 14, 15, 16], "loop": False, "frameDurationMs": 125},
            {"id": "drag_resist", "frames": [17], "loop": False, "frameDurationMs": 250},
            {"id": "fling_land", "frames": [18, 19], "loop": False, "frameDurationMs": 280},
        ],
        "frames": [
            {"index": index, "name": frame_name, "sourceHint": board_name}
            for index, (board_name, _, frame_name) in enumerate(FRAMES)
        ],
    }


def main() -> int:
    CLEAN_DIR.mkdir(parents=True, exist_ok=True)
    ATLAS_PATH.parent.mkdir(parents=True, exist_ok=True)
    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    boards: dict[str, Image.Image] = {}
    board_subjects: dict[str, list[Image.Image]] = {}
    frames: list[Image.Image] = []
    for index, (board_name, cell_index, frame_name) in enumerate(FRAMES):
        board_path = RAW_DIR / board_name
        if not board_path.exists():
            raise FileNotFoundError(board_path)
        board = boards.setdefault(board_name, Image.open(board_path).convert("RGBA"))
        subjects = board_subjects.setdefault(board_name, extract_board_subjects(board))
        source_cell = subjects[cell_index]
        frames.append(prepare_frame(source_cell, frame_name))
    frames[12] = tint_moki(frames[0], 0.76)
    for index, (_, _, frame_name) in enumerate(FRAMES):
        frame_path = CLEAN_DIR / f"moki_{index:02d}_{frame_name}.png"
        frames[index].save(frame_path, optimize=True)
    atlas = Image.new("RGBA", (ATLAS_COLUMNS * CELL_SIZE, 4 * CELL_SIZE), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % ATLAS_COLUMNS) * CELL_SIZE, (index // ATLAS_COLUMNS) * CELL_SIZE))
    atlas.save(ATLAS_PATH, optimize=True)
    atlas.save(CONTACT_SHEET_PATH, optimize=True)
    create_preview(frames[0]).save(PREVIEW_PATH, optimize=True)
    SPEC_PATH.write_text(json.dumps(build_spec(), indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(frames)} frames")
    print(ATLAS_PATH.relative_to(ROOT))
    print(SPEC_PATH.relative_to(ROOT))
    print(PREVIEW_PATH.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
