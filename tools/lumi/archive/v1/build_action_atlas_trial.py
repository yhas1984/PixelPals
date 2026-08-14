#!/usr/bin/env python3
"""Build an isolated 32-frame Lumi atlas trial from a checkerboard sheet."""

from __future__ import annotations

import json
import os
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[4]
SOURCE_PATH = Path(
    os.environ.get(
        "LUMI_ACTION_SOURCE",
        "/home/yhas/Pictures/pixelpals_refs/lumi_atlas_preview_image.png",
    )
)
OUTPUT_DIR = ROOT / "tools/lumi/archive/v1/source_atlas/action_trial_v1"
ATLAS_PATH = OUTPUT_DIR / "lumi_action_trial_v1.png"
PREVIEW_PATH = OUTPUT_DIR / "lumi_action_trial_preview_v1.png"
SPEC_PATH = OUTPUT_DIR / "lumi_action_trial_v1.json"
FRAME_DIR = OUTPUT_DIR / "frames"

COLUMNS = 8
ROWS = 4
FRAME_SIZE = 384
CELL_INSET = 16
CONTENT_SIZE = FRAME_SIZE - CELL_INSET * 2
ALPHA_EROSION = 1
BACKGROUND_TOLERANCE = 12
BACKGROUND_MINIMUM = 220

FRAME_NAMES = [
    "idle_neutral",
    "idle_tail_shift",
    "idle_wink",
    "idle_curious",
    "idle_attentive",
    "sit_neutral",
    "wave_standing",
    "wave_sitting",
    "walk_00",
    "walk_01",
    "walk_02",
    "walk_03",
    "walk_04",
    "walk_05",
    "walk_06",
    "walk_07",
    "stalk_00",
    "stalk_01",
    "pounce_launch",
    "pounce_air",
    "pounce_land",
    "recovery",
    "settle_stand",
    "happy_jump",
    "magic_ready",
    "magic_cast",
    "magic_orb",
    "surprised",
    "yawn",
    "sleep_curl",
    "snuggle",
    "idle_return",
]


def is_background(pixel: tuple[int, int, int]) -> bool:
    return max(pixel) - min(pixel) <= BACKGROUND_TOLERANCE and min(pixel) >= BACKGROUND_MINIMUM


def remove_checkerboard(source: Image.Image) -> Image.Image:
    """Make border-connected neutral checkerboard pixels transparent."""

    rgb = source.convert("RGB")
    width, height = rgb.size
    pixels = list(rgb.getdata())
    candidate = bytearray(is_background(pixel) for pixel in pixels)
    visited = bytearray(width * height)
    queue: deque[tuple[int, int]] = deque()

    for x in range(width):
        queue.append((x, 0))
        queue.append((x, height - 1))
    for y in range(height):
        queue.append((0, y))
        queue.append((width - 1, y))

    while queue:
        x, y = queue.popleft()
        index = y * width + x
        if visited[index] or not candidate[index]:
            continue
        visited[index] = 1
        for next_x, next_y in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= next_x < width and 0 <= next_y < height:
                queue.append((next_x, next_y))

    rgba = bytearray()
    for index, pixel in enumerate(pixels):
        rgba.extend((*pixel, 0 if visited[index] else 255))
    return Image.frombytes("RGBA", (width, height), bytes(rgba))


def cell_bounds(source: Image.Image, index: int) -> tuple[int, int, int, int]:
    column, row = index % COLUMNS, index // COLUMNS
    return (
        round(column * source.width / COLUMNS),
        round(row * source.height / ROWS),
        round((column + 1) * source.width / COLUMNS),
        round((row + 1) * source.height / ROWS),
    )


def connected_components(source: Image.Image) -> tuple[np.ndarray, list[dict[str, object]]]:
    alpha = np.asarray(source.getchannel("A")) >= 32
    height, width = alpha.shape
    labels = np.full((height, width), -1, dtype=np.int32)
    components: list[dict[str, object]] = []

    for start_y, start_x in np.argwhere(alpha):
        start_y, start_x = int(start_y), int(start_x)
        if labels[start_y, start_x] >= 0:
            continue
        component_id = len(components)
        queue: deque[tuple[int, int]] = deque([(start_x, start_y)])
        labels[start_y, start_x] = component_id
        area = 0
        min_x, min_y = width, height
        max_x = max_y = 0
        sum_x = sum_y = 0
        while queue:
            x, y = queue.popleft()
            area += 1
            min_x, max_x = min(min_x, x), max(max_x, x)
            min_y, max_y = min(min_y, y), max(max_y, y)
            sum_x += x
            sum_y += y
            for next_x in range(max(0, x - 1), min(width, x + 2)):
                for next_y in range(max(0, y - 1), min(height, y + 2)):
                    if alpha[next_y, next_x] and labels[next_y, next_x] < 0:
                        labels[next_y, next_x] = component_id
                        queue.append((next_x, next_y))
        if area > 100:
            components.append(
                {
                    "id": component_id,
                    "area": area,
                    "bounds": (min_x, min_y, max_x + 1, max_y + 1),
                    "center": (sum_x / area, sum_y / area),
                }
            )
        else:
            labels[labels == component_id] = -2
    return labels, components


def bbox_gap(first: tuple[int, int, int, int], second: tuple[int, int, int, int]) -> int:
    first_left, first_top, first_right, first_bottom = first
    second_left, second_top, second_right, second_bottom = second
    horizontal = max(second_left - first_right, first_left - second_right, 0)
    vertical = max(second_top - first_bottom, first_top - second_bottom, 0)
    return horizontal * horizontal + vertical * vertical


def assign_components(
    source: Image.Image,
) -> tuple[np.ndarray, list[list[int]], list[dict[str, object]]]:
    cleaned = remove_checkerboard(source)
    labels, components = connected_components(cleaned)
    by_cell: dict[int, list[dict[str, object]]] = {index: [] for index in range(len(FRAME_NAMES))}
    for component in components:
        center_x, center_y = component["center"]
        column = min(COLUMNS - 1, int(center_x / (source.width / COLUMNS)))
        row = min(ROWS - 1, int(center_y / (source.height / ROWS)))
        by_cell[row * COLUMNS + column].append(component)

    primary: dict[int, dict[str, object]] = {}
    for index, cell_components in by_cell.items():
        if not cell_components:
            raise ValueError(f"No character component assigned to source frame {index}")
        primary[index] = max(cell_components, key=lambda component: int(component["area"]))

    assignments = [[int(primary[index]["id"])] for index in range(len(FRAME_NAMES))]
    primary_ids = {int(component["id"]) for component in primary.values()}
    extras = [component for component in components if int(component["id"]) not in primary_ids]
    for extra in extras:
        target = min(
            primary.items(),
            key=lambda item: bbox_gap(extra["bounds"], item[1]["bounds"]),
        )
        assignments[target[0]].append(int(extra["id"]))
    return labels, assignments, components


def prepare_frame(
    source: Image.Image,
    labels: np.ndarray,
    component_ids: list[int],
    index: int,
) -> tuple[Image.Image, dict[str, object]]:
    selected = np.isin(labels, component_ids)
    ys, xs = np.where(selected)
    if len(xs) == 0:
        raise ValueError(f"Empty Lumi source frame {index}")
    left, top, right, bottom = int(xs.min()), int(ys.min()), int(xs.max() + 1), int(ys.max() + 1)
    source_rgba = np.asarray(source.convert("RGBA"))[top:bottom, left:right].copy()
    source_rgba[:, :, 3] = selected[top:bottom, left:right] * 255
    subject = Image.fromarray(source_rgba, "RGBA")

    source_cell_width = source.width / COLUMNS
    source_cell_height = source.height / ROWS
    scale = CONTENT_SIZE / max(source_cell_width, source_cell_height)
    if subject.width * scale > CONTENT_SIZE or subject.height * scale > CONTENT_SIZE:
        scale = min(CONTENT_SIZE / subject.width, CONTENT_SIZE / subject.height)
    resized = subject.resize(
        (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
        Image.Resampling.LANCZOS,
    )
    if ALPHA_EROSION:
        resized.putalpha(resized.getchannel("A").filter(ImageFilter.MinFilter(3)))
    cell_left, cell_top, cell_right, cell_bottom = cell_bounds(source, index)
    source_center_x = (left + right) / 2
    cell_center_x = (cell_left + cell_right) / 2
    x = round(FRAME_SIZE / 2 + (source_center_x - cell_center_x) * scale - resized.width / 2)
    bottom_margin = max(CELL_INSET, round((cell_bottom - bottom) * scale) + CELL_INSET)
    y = FRAME_SIZE - bottom_margin - resized.height
    x = max(CELL_INSET, min(FRAME_SIZE - CELL_INSET - resized.width, x))
    y = max(CELL_INSET, min(FRAME_SIZE - CELL_INSET - resized.height, y))

    frame = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
    frame.alpha_composite(resized, (x, y))
    output_bounds = frame.getchannel("A").getbbox()
    assert output_bounds is not None
    return frame, {
        "sourceBounds": [left, top, right, bottom],
        "sourceCell": [cell_left, cell_top, cell_right, cell_bottom],
        "componentIds": component_ids,
        "scale": scale,
        "outputBounds": list(output_bounds),
    }


def build_spec() -> dict[str, object]:
    clips = [
        {"id": "idle", "frames": list(range(0, 8)), "loop": True, "frameDurationMs": 180},
        {"id": "walk", "frames": list(range(8, 16)), "loop": True, "frameDurationMs": 145},
        {"id": "pounce", "frames": list(range(16, 24)), "loop": False, "frameDurationMs": 180},
        {"id": "special", "frames": list(range(24, 32)), "loop": False, "frameDurationMs": 260},
    ]
    return {
        "version": 1,
        "petId": "lumi",
        "source": str(SOURCE_PATH),
        "atlasPath": "tools/lumi/archive/v1/source_atlas/action_trial_v1/lumi_action_trial_v1.png",
        "prototypeAtlasPath": "source_atlas/action_trial_v1/lumi_action_trial_v1.png",
        "frameWidth": FRAME_SIZE,
        "frameHeight": FRAME_SIZE,
        "columns": COLUMNS,
        "rows": ROWS,
        "frameCount": len(FRAME_NAMES),
        "pivot": {"x": FRAME_SIZE // 2, "y": FRAME_SIZE - 16},
        "renderHints": {
            "innerTransparentPaddingPx": 16,
            "backgroundRemoval": "border_connected_neutral_checkerboard",
            "contentInsetPx": CELL_INSET,
            "alphaErosionPx": ALPHA_EROSION,
            "sourceCellCompositionPreserved": True,
        },
        "clips": clips,
        "frames": [{"index": index, "name": name} for index, name in enumerate(FRAME_NAMES)],
    }


def main() -> int:
    source = Image.open(SOURCE_PATH).convert("RGB")
    if source.width < COLUMNS or source.height < ROWS:
        raise ValueError(f"Source is too small for {COLUMNS}x{ROWS}: {source.size}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    atlas = Image.new("RGBA", (FRAME_SIZE * COLUMNS, FRAME_SIZE * ROWS), (0, 0, 0, 0))
    frames: list[Image.Image] = []
    padding_failures: list[tuple[int, tuple[int, int, int, int]]] = []
    cleaned = remove_checkerboard(source)
    labels, assignments, _ = assign_components(source)

    for index, name in enumerate(FRAME_NAMES):
        frame, _ = prepare_frame(cleaned, labels, assignments[index], index)
        frames.append(frame)
        atlas.alpha_composite(frame, ((index % COLUMNS) * FRAME_SIZE, (index // COLUMNS) * FRAME_SIZE))
        frame.save(FRAME_DIR / f"lumi_{index:02d}_{name}.png", optimize=True)
        bounds = frame.getchannel("A").getbbox()
        if bounds is None:
            raise ValueError(f"Frame {index} is empty after background removal")
        margins = (bounds[0], bounds[1], FRAME_SIZE - bounds[2], FRAME_SIZE - bounds[3])
        if min(margins) < 16:
            padding_failures.append((index, margins))

    atlas.save(ATLAS_PATH, optimize=True)
    preview = Image.new("RGB", atlas.size, (31, 42, 52))
    preview.paste(atlas, mask=atlas.getchannel("A"))
    preview.save(PREVIEW_PATH, optimize=True)
    SPEC_PATH.write_text(json.dumps(build_spec(), indent=2) + "\n", encoding="utf-8")

    print(
        f"LUMI_ACTION_TRIAL_BUILT source={source.width}x{source.height} "
        f"atlas={atlas.width}x{atlas.height} frames={len(frames)}"
    )
    print(f"padding_failures={padding_failures}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
