#!/usr/bin/env python3
"""Clean Lumi V2 boards, normalize frames, and build a review atlas."""

from __future__ import annotations

import json
import shutil
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
RAW_DIR = ROOT / "tools/lumi/pipeline/raw"
OUTPUT_DIR = ROOT / "tools/lumi/pipeline/atlas_v2"
FRAME_DIR = OUTPUT_DIR / "frames"
ATLAS_PATH = OUTPUT_DIR / "lumi_motion_v2.png"
PREVIEW_PATH = OUTPUT_DIR / "lumi_motion_v2_preview.png"
SPEC_PATH = OUTPUT_DIR / "lumi_motion_v2.json"
DEBUG_DIR = ROOT / "app/src/debug/assets/pets/lumi"
DEBUG_ATLAS_PATH = DEBUG_DIR / "lumi_motion_v2.png"
DEBUG_SPEC_PATH = DEBUG_DIR / "lumi_motion_v2.json"

FRAME_SIZE = 384
PADDING = 16
TARGET_VISIBLE_SIZE = 320
COLUMNS = 8
ROWS = 5
ALPHA_THRESHOLD = 12
BACKGROUND_DISTANCE = 72.0

FRAME_NAMES = (
    *(f"idle_{index:02d}" for index in range(4)),
    *(f"walk_{index:02d}" for index in range(8)),
    *(f"turn_{index:02d}" for index in range(4)),
    *(f"hop_up_{index:02d}" for index in range(4)),
    *(f"hop_down_{index:02d}" for index in range(4)),
    *(f"front_social_{index:02d}" for index in range(4)),
    *(f"pounce_{index:02d}" for index in range(4)),
    *(f"sleep_{index:02d}" for index in range(4)),
    *(f"magic_{index:02d}" for index in range(4)),
)

MIRROR_TO_RIGHT_FACING = {
    "05_hop_up",
    "06_hop_down",
    "08_play_pounce_recover",
}


def get_border_reference(rgb: np.ndarray) -> np.ndarray:
    border = np.concatenate(
        (
            rgb[:8].reshape(-1, 3),
            rgb[-8:].reshape(-1, 3),
            rgb[:, :8].reshape(-1, 3),
            rgb[:, -8:].reshape(-1, 3),
        )
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
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                ny, nx = y + dy, x + dx
                if 0 <= ny < height and 0 <= nx < width:
                    add_pixel(ny, nx)
    return exterior


def remove_background(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    rgb = rgba[:, :, :3].astype(np.float32)
    background = get_border_reference(rgb)
    distance = np.linalg.norm(rgb - background, axis=2)
    exterior = get_exterior_mask(distance < BACKGROUND_DISTANCE)
    rgba[exterior, 3] = 0
    rgba[rgba[:, :, 3] == 0, :3] = 0
    return Image.fromarray(rgba, "RGBA")


def components(alpha: np.ndarray) -> list[list[tuple[int, int]]]:
    height, width = alpha.shape
    visited = np.zeros((height, width), dtype=bool)
    found: list[list[tuple[int, int]]] = []
    for y, x in np.argwhere(alpha):
        y, x = int(y), int(x)
        if visited[y, x]:
            continue
        queue: deque[tuple[int, int]] = deque([(y, x)])
        visited[y, x] = True
        component: list[tuple[int, int]] = []
        while queue:
            cy, cx = queue.popleft()
            component.append((cy, cx))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = cy + dy, cx + dx
                    if 0 <= ny < height and 0 <= nx < width and alpha[ny, nx] and not visited[ny, nx]:
                        visited[ny, nx] = True
                        queue.append((ny, nx))
        found.append(component)
    return found


def extract_cells(board: Image.Image) -> list[Image.Image]:
    cleaned = remove_background(board)
    rgba = np.asarray(cleaned)
    found = sorted(components(rgba[:, :, 3] > ALPHA_THRESHOLD), key=len, reverse=True)
    if len(found) < 4:
        raise ValueError(f"Expected four connected subjects, found {len(found)}")
    subjects: list[tuple[float, float, Image.Image]] = []
    for component in found[:4]:
        ys = np.asarray([item[0] for item in component])
        xs = np.asarray([item[1] for item in component])
        left, top, right, bottom = max(0, int(xs.min()) - 4), max(0, int(ys.min()) - 4), min(board.width, int(xs.max()) + 5), min(board.height, int(ys.max()) + 5)
        isolated = np.zeros_like(rgba)
        isolated[ys, xs] = rgba[ys, xs]
        crop = Image.fromarray(isolated, "RGBA").crop((left, top, right, bottom))
        subjects.append((float(ys.mean()), float(xs.mean()), crop))
    subjects.sort(key=lambda item: (0 if item[0] < board.height / 2 else 1, item[1]))
    return [item[2] for item in subjects]


def normalize(crop: Image.Image, frame_name: str) -> Image.Image:
    bounds = crop.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError(f"Empty source for {frame_name}")
    crop = crop.crop(bounds)
    scale = TARGET_VISIBLE_SIZE / max(crop.width, crop.height)
    resized = crop.resize((max(1, round(crop.width * scale)), max(1, round(crop.height * scale))), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
    # Keep grounded clips on the shared pivot line. Airborne art remains visibly
    # airborne through its pose; the runtime supplies the actual hop trajectory.
    x = (FRAME_SIZE - resized.width) // 2
    y = FRAME_SIZE - PADDING - resized.height
    frame.alpha_composite(resized, (x, y))
    output_bounds = frame.getchannel("A").getbbox()
    if output_bounds is None:
        raise ValueError(f"Normalized frame is empty for {frame_name}")
    margins = (output_bounds[0], output_bounds[1], FRAME_SIZE - output_bounds[2], FRAME_SIZE - output_bounds[3])
    if min(margins) < PADDING:
        raise ValueError(f"Padding violation for {frame_name}: {margins}")
    return frame


def build_spec(atlas_path: str) -> dict[str, object]:
    return {
        "version": 2,
        "petId": "lumi",
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
            "backgroundRemoval": "largest_connected_rgba_component",
            "sourceCellCompositionPreserved": True,
        },
        "clips": [
            {"id": "idle", "frames": [0, 1, 2, 3], "loop": True, "frameDurationMs": 900},
            {"id": "walk", "frames": list(range(4, 12)), "loop": True, "frameDurationMs": 170},
            {"id": "turn", "frames": list(range(12, 16)), "loop": False, "frameDurationMs": 420},
            {"id": "hop_up", "frames": list(range(16, 20)), "loop": False, "frameDurationMs": 260},
            {"id": "hop_down", "frames": list(range(20, 24)), "loop": False, "frameDurationMs": 260},
            {"id": "front_social", "frames": list(range(24, 28)), "loop": False, "frameDurationMs": 900},
            {"id": "pounce", "frames": list(range(28, 32)), "loop": False, "frameDurationMs": 260},
            {"id": "sleep", "frames": list(range(32, 36)), "loop": True, "frameDurationMs": 1600},
            {"id": "magic", "frames": list(range(36, 40)), "loop": False, "frameDurationMs": 620},
        ],
        "frames": [{"index": index, "name": name} for index, name in enumerate(FRAME_NAMES)],
    }


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    DEBUG_DIR.mkdir(parents=True, exist_ok=True)
    for path in FRAME_DIR.glob("lumi_*.png"):
        path.unlink()
    frames: list[Image.Image] = []
    source_details: list[dict[str, object]] = []
    board_names = [f"{index:02d}_{name}" for index, name in enumerate(("idle_breath", "walk_a", "walk_b", "turn_in_place", "hop_up", "hop_down", "front_social", "play_pounce_recover", "sleep", "magic"), start=1)]
    for board_name in board_names:
        path = RAW_DIR / f"{board_name}.png"
        if not path.exists():
            raise FileNotFoundError(path)
        cells = extract_cells(Image.open(path).convert("RGBA"))
        start = len(frames)
        for cell_index, cell in enumerate(cells):
            frame_name = FRAME_NAMES[start + cell_index]
            mirrored_to_right = board_name in MIRROR_TO_RIGHT_FACING
            if mirrored_to_right:
                cell = cell.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            frame = normalize(cell, frame_name)
            frame.save(FRAME_DIR / f"lumi_{start + cell_index:02d}_{frame_name}.png", optimize=True)
            frames.append(frame)
            source_details.append({"index": start + cell_index, "name": frame_name, "source": str(path.relative_to(ROOT)), "sourceCell": cell_index, "mirroredToRightFacing": mirrored_to_right})
    atlas = Image.new("RGBA", (FRAME_SIZE * COLUMNS, FRAME_SIZE * ROWS), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % COLUMNS) * FRAME_SIZE, (index // COLUMNS) * FRAME_SIZE))
    atlas.save(ATLAS_PATH, optimize=True)
    preview = Image.new("RGBA", atlas.size, (31, 42, 52, 255))
    preview.alpha_composite(atlas)
    preview.save(PREVIEW_PATH, optimize=True)
    spec = build_spec("tools/lumi/pipeline/atlas_v2/lumi_motion_v2.png")
    spec["frameDetails"] = source_details
    SPEC_PATH.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
    debug_spec = build_spec("pets/lumi/lumi_motion_v2.png")
    DEBUG_SPEC_PATH.write_text(json.dumps(debug_spec, indent=2) + "\n", encoding="utf-8")
    shutil.copyfile(ATLAS_PATH, DEBUG_ATLAS_PATH)
    print(f"LUMI_V2_BUILT frames={len(frames)} atlas={ATLAS_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
