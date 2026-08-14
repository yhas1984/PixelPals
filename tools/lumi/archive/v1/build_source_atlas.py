#!/usr/bin/env python3
"""Extract the supplied Lumi contact sheet into a PixelPals atlas draft."""

from __future__ import annotations

import json
import os
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[4]
SOURCE_PATH = Path(os.environ.get("LUMI_ATLAS_SOURCE", "/home/yhas/Pictures/pixelpals_refs/lumi_atlas_preview.png"))
OUTPUT_DIR = ROOT / "tools/lumi/archive/v1/source_atlas"
CLEAN_DIR = OUTPUT_DIR / "frames"
ATLAS_PATH = OUTPUT_DIR / "lumi_sheet_source_v1.png"
SPEC_PATH = OUTPUT_DIR / "lumi_sheet_source_v1.json"
PREVIEW_PATH = OUTPUT_DIR / "lumi_preview_source_v1.png"
CONTACT_SHEET_PATH = OUTPUT_DIR / "lumi_frames_source_v1.png"
REPORT_PATH = OUTPUT_DIR / "lumi_source_report.json"

SOURCE_WIDTH = 1536
SOURCE_HEIGHT = 1024
SOURCE_COLUMNS = 4
SOURCE_ROWS = 4
SOURCE_CELL_WIDTH = SOURCE_WIDTH // SOURCE_COLUMNS
SOURCE_CELL_HEIGHT = SOURCE_HEIGHT // SOURCE_ROWS
CELL_SIZE = 384
PADDING = 16
COMPONENT_ALPHA_THRESHOLD = 32
EDGE_EXPANSION = 1

FRAME_DEFINITIONS = (
    ("idle", "idle_neutral"),
    ("idle_wink", "idle_wink"),
    ("happy_jump", "happy_jump"),
    ("curious_tilt", "curious_tilt"),
    ("playful_crouch", "playful_crouch"),
    ("sit_neutral", "sit_neutral"),
    ("wave", "wave"),
    ("jump_cheer", "jump_cheer"),
    ("magic_cast", "magic_cast"),
    ("sit_wave", "sit_wave"),
    ("walk_step", "walk_step"),
    ("pounce", "pounce"),
    ("yawn", "yawn"),
    ("sleep_curl", "sleep_curl"),
    ("surprised", "surprised"),
    ("snuggle", "snuggle"),
)


def remove_low_alpha_background(image: Image.Image, threshold: int = 8) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    rgba[rgba[:, :, 3] < threshold] = 0
    return Image.fromarray(rgba, "RGBA")


def source_cell_bounds(index: int) -> tuple[int, int, int, int]:
    row, column = divmod(index, SOURCE_COLUMNS)
    return (
        column * SOURCE_CELL_WIDTH,
        row * SOURCE_CELL_HEIGHT,
        (column + 1) * SOURCE_CELL_WIDTH,
        (row + 1) * SOURCE_CELL_HEIGHT,
    )


def connected_components(source: Image.Image) -> list[dict[str, object]]:
    """Find characters globally so poses crossing row gutters are not clipped."""
    alpha = np.asarray(source.getchannel("A")) >= COMPONENT_ALPHA_THRESHOLD
    height, width = alpha.shape
    visited = np.zeros_like(alpha, dtype=bool)
    components: list[dict[str, object]] = []
    for start_y, start_x in np.argwhere(alpha):
        start_y = int(start_y)
        start_x = int(start_x)
        if visited[start_y, start_x]:
            continue
        queue: deque[tuple[int, int]] = deque([(start_y, start_x)])
        visited[start_y, start_x] = True
        pixels: list[tuple[int, int]] = []
        while queue:
            y, x = queue.popleft()
            pixels.append((y, x))
            for delta_y in (-1, 0, 1):
                for delta_x in (-1, 0, 1):
                    if delta_y == 0 and delta_x == 0:
                        continue
                    next_y = y + delta_y
                    next_x = x + delta_x
                    if (
                        0 <= next_y < height
                        and 0 <= next_x < width
                        and alpha[next_y, next_x]
                        and not visited[next_y, next_x]
                    ):
                        visited[next_y, next_x] = True
                        queue.append((next_y, next_x))
        if len(pixels) <= 100:
            continue
        coordinates = np.asarray(pixels, dtype=np.int32)
        ys = coordinates[:, 0]
        xs = coordinates[:, 1]
        components.append(
            {
                "area": len(pixels),
                "ys": ys,
                "xs": xs,
                "bounds": (int(xs.min()), int(ys.min()), int(xs.max() + 1), int(ys.max() + 1)),
                "center": (float(xs.mean()), float(ys.mean())),
            }
        )
    return sorted(components, key=lambda component: int(component["area"]), reverse=True)


def components_for_frame(components: list[dict[str, object]], index: int) -> list[dict[str, object]]:
    left, top, right, bottom = source_cell_bounds(index)
    selected = []
    for component in components:
        center_x, center_y = component["center"]
        if left <= center_x < right and top <= center_y < bottom:
            selected.append(component)
    if not selected:
        raise ValueError(f"No connected component assigned to source frame {index}")
    return selected


def prepare_frame(
    source: Image.Image,
    index: int,
    components: list[dict[str, object]],
) -> tuple[Image.Image, dict[str, object]]:
    rgba = np.asarray(source).copy()
    component_mask = Image.new("L", source.size, 0)
    component_pixels = np.zeros((SOURCE_HEIGHT, SOURCE_WIDTH), dtype=np.uint8)
    for component in components:
        component_pixels[component["ys"], component["xs"]] = 255
    component_mask = Image.fromarray(component_pixels, "L")
    component_mask = component_mask.filter(ImageFilter.MaxFilter(EDGE_EXPANSION * 2 + 1))
    keep = (np.asarray(component_mask) > 0) & (rgba[:, :, 3] > 0)
    isolated = np.zeros_like(rgba)
    isolated[keep] = rgba[keep]
    isolated_image = Image.fromarray(isolated, "RGBA")
    bounds = isolated_image.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError(f"Empty Lumi source frame {index}")
    left, top, right, bottom = bounds
    subject = isolated_image.crop(bounds)
    scale = min((CELL_SIZE - PADDING * 2) / subject.width, (CELL_SIZE - PADDING * 2) / subject.height)
    resized = subject.resize(
        (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
        Image.Resampling.LANCZOS,
    )
    frame = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0))
    x = (CELL_SIZE - resized.width) // 2
    y = CELL_SIZE - PADDING - resized.height
    frame.alpha_composite(resized, (x, y))
    output_bounds = frame.getchannel("A").getbbox()
    assert output_bounds is not None
    cell_left, cell_top, cell_right, cell_bottom = source_cell_bounds(index)
    report = {
        "sourceBounds": [left, top, right, bottom],
        "sourceSize": [SOURCE_WIDTH, SOURCE_HEIGHT],
        "nominalCell": [cell_left, cell_top, cell_right, cell_bottom],
        "componentCount": len(components),
        "componentAreas": [int(component["area"]) for component in components],
        "scale": scale,
        "outputBounds": list(output_bounds),
        "outputMargins": [
            output_bounds[0],
            output_bounds[1],
            CELL_SIZE - output_bounds[2],
            CELL_SIZE - output_bounds[3],
        ],
        "crossesNominalCellBounds": {
            "top": top < cell_top,
            "bottom": bottom > cell_bottom,
            "left": left < cell_left,
            "right": right > cell_right,
        },
    }
    return frame, report


def build_spec() -> dict[str, object]:
    clips = [
        {"id": "idle", "frames": [0, 1, 0, 2, 0], "loop": True, "frameDurationMs": 240},
        {"id": "look", "frames": [0, 3, 0], "loop": False, "frameDurationMs": 260},
        {"id": "play", "frames": [4, 5, 6, 7], "loop": False, "frameDurationMs": 180},
        {"id": "magic", "frames": [0, 8, 8, 0], "loop": False, "frameDurationMs": 240},
        {"id": "walk", "frames": [9, 10, 9, 10], "loop": True, "frameDurationMs": 190},
        {"id": "pounce", "frames": [11, 10], "loop": False, "frameDurationMs": 200},
        {"id": "sleep", "frames": [12, 13, 12], "loop": True, "frameDurationMs": 520},
        {"id": "surprise", "frames": [14], "loop": False, "frameDurationMs": 520},
        {"id": "snuggle", "frames": [15], "loop": True, "frameDurationMs": 680},
    ]
    return {
        "version": 1,
        "petId": "lumi",
        "atlasPath": "pets/lumi/lumi_sheet_source_v1.png",
        "prototypeAtlasPath": "source_atlas/lumi_sheet_source_v1.png",
        "previewPath": "res://drawable-nodpi/pet_lumi.png",
        "frameWidth": CELL_SIZE,
        "frameHeight": CELL_SIZE,
        "columns": SOURCE_COLUMNS,
        "rows": SOURCE_ROWS,
        "frameCount": len(FRAME_DEFINITIONS),
        "pivot": {"x": CELL_SIZE // 2, "y": CELL_SIZE - PADDING},
        "renderHints": {
            "innerTransparentPaddingPx": PADDING,
            "recommendedBleedInsetPx": 1,
            "filterBitmap": True,
        },
        "clips": clips,
        "frames": [
            {"index": index, "name": name, "sourceHint": "lumi_atlas_preview.png"}
            for index, (_, name) in enumerate(FRAME_DEFINITIONS)
        ],
    }


def create_preview(frame: Image.Image) -> Image.Image:
    bounds = frame.getchannel("A").getbbox()
    assert bounds is not None
    subject = frame.crop(bounds)
    scale = min(440 / subject.width, 440 / subject.height)
    resized = subject.resize((round(subject.width * scale), round(subject.height * scale)), Image.Resampling.LANCZOS)
    preview = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    preview.alpha_composite(resized, ((512 - resized.width) // 2, (512 - resized.height) // 2))
    return preview


def main() -> int:
    if not SOURCE_PATH.exists():
        raise FileNotFoundError(SOURCE_PATH)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    CLEAN_DIR.mkdir(parents=True, exist_ok=True)
    source = Image.open(SOURCE_PATH).convert("RGBA")
    if source.size != (SOURCE_WIDTH, SOURCE_HEIGHT):
        raise ValueError(f"Expected {SOURCE_WIDTH}x{SOURCE_HEIGHT}, got {source.size}")
    components = connected_components(source)
    if len(components) < len(FRAME_DEFINITIONS):
        raise ValueError(f"Expected at least {len(FRAME_DEFINITIONS)} connected components, found {len(components)}")

    frames: list[Image.Image] = []
    report: dict[str, object] = {
        "source": str(SOURCE_PATH),
        "sourceSize": list(source.size),
        "grid": {"columns": SOURCE_COLUMNS, "rows": SOURCE_ROWS, "cellSize": [SOURCE_CELL_WIDTH, SOURCE_CELL_HEIGHT]},
        "frames": [],
    }
    for index, (_, frame_name) in enumerate(FRAME_DEFINITIONS):
        frame_components = components_for_frame(components, index)
        frame, frame_report = prepare_frame(source, index, frame_components)
        frame_path = CLEAN_DIR / f"lumi_{index:02d}_{frame_name}.png"
        frame.save(frame_path, optimize=True)
        frames.append(frame)
        report["frames"].append({"index": index, "name": frame_name, **frame_report})

    atlas = Image.new("RGBA", (CELL_SIZE * SOURCE_COLUMNS, CELL_SIZE * SOURCE_ROWS), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % SOURCE_COLUMNS) * CELL_SIZE, (index // SOURCE_COLUMNS) * CELL_SIZE))
    atlas.save(ATLAS_PATH, optimize=True)
    create_preview(frames[0]).save(PREVIEW_PATH, optimize=True)

    contact = Image.new("RGBA", (CELL_SIZE * SOURCE_COLUMNS, CELL_SIZE * SOURCE_ROWS), (39, 30, 29, 255))
    for index, frame in enumerate(frames):
        contact.alpha_composite(frame, ((index % SOURCE_COLUMNS) * CELL_SIZE, (index // SOURCE_COLUMNS) * CELL_SIZE))
    contact.save(CONTACT_SHEET_PATH, optimize=True)
    SPEC_PATH.write_text(json.dumps(build_spec(), indent=2) + "\n", encoding="utf-8")
    REPORT_PATH.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"LUMI_SOURCE_ATLAS_READY frames={len(frames)} atlas={atlas.size[0]}x{atlas.size[1]}")
    print(f"atlas={ATLAS_PATH}")
    print(f"spec={SPEC_PATH}")
    print(f"report={REPORT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
