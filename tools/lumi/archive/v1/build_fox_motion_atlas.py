#!/usr/bin/env python3
"""Build Lumi's fox-like R&D motion atlas from GPT-image contact sheets."""

from __future__ import annotations

import json
import shutil
from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[4]
SOURCE_DIR = Path("/home/yhas/Pictures/pixelpals_refs/Lumi")
LEGACY_ATLAS_PATH = ROOT / "tools/lumi/archive/v1/source_atlas/action_trial_v1/lumi_action_trial_v1.png"
OUTPUT_DIR = ROOT / "tools/lumi/archive/v1/source_atlas/fox_motion_rnd_v1"
DEBUG_ASSET_DIR = ROOT / "app/src/debug/assets/pets/lumi"
ATLAS_PATH = OUTPUT_DIR / "lumi_fox_motion_rnd_v1.png"
PREVIEW_PATH = OUTPUT_DIR / "lumi_fox_motion_rnd_preview_v1.png"
SPEC_PATH = OUTPUT_DIR / "lumi_fox_motion_rnd_v1.json"
DEBUG_ATLAS_PATH = DEBUG_ASSET_DIR / ATLAS_PATH.name
DEBUG_SPEC_PATH = DEBUG_ASSET_DIR / SPEC_PATH.name
FRAME_DIR = OUTPUT_DIR / "frames"

FRAME_SIZE = 384
PADDING = 16
CONTENT_SIZE = FRAME_SIZE - PADDING * 2
TARGET_VISIBLE_SIZE = 320
COLUMNS = 7
ROWS = 4
SOURCE_COLUMNS = 2
SOURCE_ROWS = 2
ALPHA_THRESHOLD = 16

STATIC_FRAMES = (
    ("lumi_fox_alert_stretch.png", 0, "idle_alert"),
    ("lumi_fox_alert_stretch.png", 1, "idle_attentive"),
    ("lumi_fox_alert_stretch.png", 2, "play_bow_stretch"),
    ("lumi_fox_alert_stretch.png", 3, "happy_recovery"),
    ("lumi_fox_investigate.png", 0, "investigate_alert"),
    ("lumi_fox_investigate.png", 1, "sniff_ground"),
    ("lumi_fox_investigate.png", 2, "listen_freeze"),
    ("lumi_fox_investigate.png", 3, "investigate_step"),
)

WALK_FRAMES = (
    (8, "walk_00"),
    (9, "walk_01"),
    (10, "walk_02"),
    (11, "walk_03"),
    (12, "walk_04"),
    (13, "walk_05"),
    (14, "walk_06"),
    (15, "walk_07"),
)

POUNCE_FRAMES = (
    ("lumi_fox_pounce.png", 0, "pounce_anticipation"),
    ("lumi_fox_pounce.png", 1, "pounce_air"),
    ("lumi_fox_pounce.png", 2, "pounce_land"),
    ("lumi_fox_pounce.png", 3, "pounce_recovery"),
)

SPECIAL_FRAMES = (
    "magic_ready",
    "magic_cast",
    "magic_orb",
    "surprised",
    "yawn",
    "sleep_curl",
    "snuggle",
    "idle_return",
)


def cell_bounds(image: Image.Image, index: int) -> tuple[int, int, int, int]:
    column = index % SOURCE_COLUMNS
    row = index // SOURCE_COLUMNS
    return (
        round(column * image.width / SOURCE_COLUMNS),
        round(row * image.height / SOURCE_ROWS),
        round((column + 1) * image.width / SOURCE_COLUMNS),
        round((row + 1) * image.height / SOURCE_ROWS),
    )


def largest_component_mask(image: Image.Image) -> tuple[bytearray, tuple[int, int, int, int]]:
    alpha = image.getchannel("A")
    width, height = image.size
    source = alpha.load()
    visited = bytearray(width * height)
    largest: list[tuple[int, int]] = []

    for y in range(height):
        for x in range(width):
            offset = y * width + x
            if visited[offset] or source[x, y] <= ALPHA_THRESHOLD:
                continue
            visited[offset] = 1
            queue: deque[tuple[int, int]] = deque([(x, y)])
            component: list[tuple[int, int]] = []
            while queue:
                current_x, current_y = queue.popleft()
                component.append((current_x, current_y))
                for next_y in range(max(0, current_y - 1), min(height, current_y + 2)):
                    for next_x in range(max(0, current_x - 1), min(width, current_x + 2)):
                        next_offset = next_y * width + next_x
                        if not visited[next_offset] and source[next_x, next_y] > ALPHA_THRESHOLD:
                            visited[next_offset] = 1
                            queue.append((next_x, next_y))
            if len(component) > len(largest):
                largest = component

    if not largest:
        raise ValueError("No character pixels found in source cell")

    mask = bytearray(width * height)
    min_x = min(x for x, _ in largest)
    min_y = min(y for _, y in largest)
    max_x = max(x for x, _ in largest) + 1
    max_y = max(y for _, y in largest) + 1
    for x, y in largest:
        mask[y * width + x] = 1
    return mask, (min_x, min_y, max_x, max_y)


def prepare_new_frame(board: Image.Image, source_index: int) -> tuple[Image.Image, dict[str, object]]:
    source_cell = board.crop(cell_bounds(board, source_index)).convert("RGBA")
    mask, bounds = largest_component_mask(source_cell)
    left, top, right, bottom = bounds
    source_rgba = source_cell.crop(bounds)
    cropped_mask = Image.new("L", (right - left, bottom - top), 0)
    mask_pixels = cropped_mask.load()
    for y in range(bottom - top):
        for x in range(right - left):
            mask_pixels[x, y] = source_rgba.getpixel((x, y))[3] if mask[(top + y) * source_cell.width + left + x] else 0
    source_rgba.putalpha(cropped_mask)

    scale = TARGET_VISIBLE_SIZE / max(source_rgba.width, source_rgba.height)
    resized = source_rgba.resize(
        (max(1, round(source_rgba.width * scale)), max(1, round(source_rgba.height * scale))),
        Image.Resampling.LANCZOS,
    )
    resized.putalpha(resized.getchannel("A").filter(ImageFilter.MinFilter(3)))

    frame = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
    x = (FRAME_SIZE - resized.width) // 2
    y = FRAME_SIZE - PADDING - resized.height
    frame.alpha_composite(resized, (x, y))
    output_bounds = frame.getchannel("A").getbbox()
    if output_bounds is None:
        raise ValueError(f"Source frame {source_index} became empty")
    margins = (
        output_bounds[0],
        output_bounds[1],
        FRAME_SIZE - output_bounds[2],
        FRAME_SIZE - output_bounds[3],
    )
    if min(margins) < PADDING:
        raise ValueError(f"Source frame {source_index} violates padding: {margins}")
    return frame, {
        "sourceBounds": list(bounds),
        "sourceCell": list(cell_bounds(board, source_index)),
        "scale": scale,
        "outputBounds": list(output_bounds),
        "margins": list(margins),
        "targetVisibleSize": TARGET_VISIBLE_SIZE,
    }


def extract_legacy_frame(atlas: Image.Image, source_index: int, flip_horizontal: bool = False) -> Image.Image:
    left = (source_index % 8) * FRAME_SIZE
    top = (source_index // 8) * FRAME_SIZE
    frame = atlas.crop((left, top, left + FRAME_SIZE, top + FRAME_SIZE)).convert("RGBA")
    if flip_horizontal:
        frame = frame.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    bounds = frame.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError(f"Legacy frame {source_index} is empty")
    subject = frame.crop(bounds)
    scale = TARGET_VISIBLE_SIZE / max(subject.width, subject.height)
    resized = subject.resize(
        (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
        Image.Resampling.LANCZOS,
    )
    resized.putalpha(resized.getchannel("A").filter(ImageFilter.MinFilter(3)))
    normalized = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
    normalized.alpha_composite(
        resized,
        ((FRAME_SIZE - resized.width) // 2, FRAME_SIZE - PADDING - resized.height),
    )
    return normalized


def build_spec(atlas_path: str) -> dict[str, object]:
    frame_names = (
        [name for _, _, name in STATIC_FRAMES]
        + [name for _, name in WALK_FRAMES]
        + [name for _, _, name in POUNCE_FRAMES]
        + list(SPECIAL_FRAMES)
    )
    clips = [
        {"id": "idle", "frames": [0, 1, 0, 3], "loop": True, "frameDurationMs": 820},
        {"id": "walk", "frames": list(range(8, 16)), "loop": True, "frameDurationMs": 180},
        {"id": "investigate", "frames": [4, 5, 6, 7], "loop": False, "frameDurationMs": 760},
        {"id": "stalk", "frames": [5, 6, 5, 7], "loop": False, "frameDurationMs": 760},
        {"id": "pounce", "frames": [16, 17, 18, 19], "loop": False, "frameDurationMs": 520},
        {"id": "recovery", "frames": [18, 19, 3, 0], "loop": False, "frameDurationMs": 680},
        {"id": "happy", "frames": [3, 2, 3, 27], "loop": False, "frameDurationMs": 700},
        {"id": "magic", "frames": [20, 21, 22, 27], "loop": False, "frameDurationMs": 620},
        {"id": "startle", "frames": [23, 1, 0], "loop": False, "frameDurationMs": 520},
        {"id": "sleep", "frames": [24, 25], "loop": True, "frameDurationMs": 1600},
        {"id": "snuggle", "frames": [26, 26, 27], "loop": False, "frameDurationMs": 900},
    ]
    return {
        "version": 1,
        "petId": "lumi",
        "atlasPath": atlas_path,
        "previewPath": "tools/lumi/archive/v1/source_atlas/fox_motion_rnd_v1/lumi_fox_motion_rnd_preview_v1.png",
        "frameWidth": FRAME_SIZE,
        "frameHeight": FRAME_SIZE,
        "columns": COLUMNS,
        "rows": ROWS,
        "frameCount": len(frame_names),
        "pivot": {"x": FRAME_SIZE // 2, "y": FRAME_SIZE - PADDING},
        "renderHints": {
            "innerTransparentPaddingPx": PADDING,
            "recommendedBleedInsetPx": 1,
            "filterBitmap": True,
            "backgroundRemoval": "largest_connected_rgba_component",
            "sourceCellCompositionPreserved": True,
        },
        "clips": clips,
        "frames": [
            {"index": index, "name": name}
            for index, name in enumerate(frame_names)
        ],
    }


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    DEBUG_ASSET_DIR.mkdir(parents=True, exist_ok=True)
    for stale_frame in FRAME_DIR.glob("lumi_*.png"):
        stale_frame.unlink()

    legacy_atlas = Image.open(LEGACY_ATLAS_PATH).convert("RGBA")
    frames: list[Image.Image] = []
    details: list[dict[str, object]] = []

    for source_name, source_index, frame_name in STATIC_FRAMES:
        source_path = SOURCE_DIR / source_name
        if not source_path.exists():
            raise FileNotFoundError(source_path)
        board = Image.open(source_path).convert("RGBA")
        frame, detail = prepare_new_frame(board, source_index)
        frame.save(FRAME_DIR / f"lumi_{len(frames):02d}_{frame_name}.png", optimize=True)
        frames.append(frame)
        details.append({"name": frame_name, "source": str(source_path), **detail})

    for output_index, (source_index, frame_name) in enumerate(WALK_FRAMES, start=8):
        frame = extract_legacy_frame(legacy_atlas, source_index, flip_horizontal=True)
        frame.save(FRAME_DIR / f"lumi_{output_index:02d}_{frame_name}.png", optimize=True)
        frames.append(frame)
        details.append({
            "name": frame_name,
            "sourceFrame": source_index,
            "outputIndex": output_index,
            "flippedToRight": True,
            "targetVisibleSize": TARGET_VISIBLE_SIZE,
        })

    for output_index, (source_name, source_index, frame_name) in enumerate(POUNCE_FRAMES, start=16):
        source_path = SOURCE_DIR / source_name
        if not source_path.exists():
            raise FileNotFoundError(source_path)
        board = Image.open(source_path).convert("RGBA")
        frame, detail = prepare_new_frame(board, source_index)
        frame.save(FRAME_DIR / f"lumi_{output_index:02d}_{frame_name}.png", optimize=True)
        frames.append(frame)
        details.append({"name": frame_name, "source": str(source_path), **detail})

    for output_index, (source_index, frame_name) in enumerate(zip(range(24, 32), SPECIAL_FRAMES), start=20):
        frame = extract_legacy_frame(legacy_atlas, source_index)
        frame.save(FRAME_DIR / f"lumi_{output_index:02d}_{frame_name}.png", optimize=True)
        frames.append(frame)
        details.append({
            "name": frame_name,
            "sourceFrame": source_index,
            "outputIndex": output_index,
            "targetVisibleSize": TARGET_VISIBLE_SIZE,
        })

    atlas = Image.new("RGBA", (FRAME_SIZE * COLUMNS, FRAME_SIZE * ROWS), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % COLUMNS) * FRAME_SIZE, (index // COLUMNS) * FRAME_SIZE))
    atlas.save(ATLAS_PATH, optimize=True)
    preview = Image.new("RGBA", atlas.size, (31, 42, 52, 255))
    preview.alpha_composite(atlas)
    preview.save(PREVIEW_PATH, optimize=True)

    tool_spec = build_spec("tools/lumi/archive/v1/source_atlas/fox_motion_rnd_v1/lumi_fox_motion_rnd_v1.png")
    tool_spec["frameDetails"] = details
    SPEC_PATH.write_text(json.dumps(tool_spec, indent=2) + "\n", encoding="utf-8")

    debug_spec = build_spec("pets/lumi/lumi_fox_motion_rnd_v1.png")
    debug_spec["previewPath"] = ""
    debug_spec.pop("frameDetails", None)
    DEBUG_SPEC_PATH.write_text(json.dumps(debug_spec, indent=2) + "\n", encoding="utf-8")
    shutil.copyfile(ATLAS_PATH, DEBUG_ATLAS_PATH)

    print(f"LUMI_FOX_MOTION_BUILT atlas={ATLAS_PATH} frames={len(frames)}")
    print(f"debugAtlas={DEBUG_ATLAS_PATH}")
    print(f"metadata={SPEC_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
