#!/usr/bin/env python3
"""Build Ginger's opt-in rest atlas from the reviewed transition sheet."""

from __future__ import annotations

import copy
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ATLAS = ROOT / "app/src/carePreview/assets/pets/ginger/ginger_motion_v2.png"
SOURCE_SPEC = ROOT / "app/src/carePreview/assets/pets/ginger/ginger_motion_v2.json"
SOURCE_SHEET = ROOT / "tools/ginger/raw/rest-transition-2026-09-13/cleaned.png"
OUTPUT_DIR = ROOT / "app/src/carePreview/assets/pets/ginger"
OUTPUT_ATLAS = OUTPUT_DIR / "ginger_rest_v2.png"
OUTPUT_SPEC = OUTPUT_DIR / "ginger_rest_v2.json"
REVIEW_PATH = ROOT / "tools/ginger/review/rest-contact.png"

CELL_SIZE = 384
COLUMNS = 4
SOURCE_ROWS = 5
OUTPUT_ROWS = 6
FRAME_COUNT = 22
SCALE = 0.46
BASELINE_Y = 368
# Frame 18's standing camera places Ginger's face near x=150; keeping this
# target leaves the wide crouch pose fully inside the 384px cell.
CAMERA_HEAD_X = 150.0
ALPHA_THRESHOLD = 8


def detect_groups(image: Image.Image) -> list[tuple[int, int]]:
    occupied = (np.asarray(image.getchannel("A"), dtype=np.uint8) >= ALPHA_THRESHOLD).any(axis=0)
    runs: list[tuple[int, int]] = []
    start = -1
    for index, present in enumerate(occupied):
        if present and start < 0:
            start = index
        elif not present and start >= 0:
            runs.append((start, index - 1))
            start = -1
    if start >= 0:
        runs.append((start, len(occupied) - 1))
    if len(runs) != 3:
        raise ValueError(f"Expected three pose groups, found {runs}")
    return runs


def make_cell(sheet: Image.Image, group: tuple[int, int]) -> Image.Image:
    left, right = group
    crop = sheet.crop((left, 0, right + 1, sheet.height))
    crop = remove_distant_specks(crop)
    alpha = np.asarray(crop.getchannel("A"), dtype=np.uint8)
    ys, xs = np.where(alpha >= ALPHA_THRESHOLD)
    if len(xs) == 0:
        raise ValueError(f"Empty pose group {group}")
    top, bottom = int(ys.min()), int(ys.max())
    face = ys <= top + round((bottom - top) * 0.45)
    face_center = float(xs[face].mean()) if np.any(face) else float(xs.mean())
    scaled = crop.resize((max(1, round(crop.width * SCALE)), max(1, round(crop.height * SCALE))), Image.Resampling.LANCZOS)
    scaled_alpha = np.asarray(scaled.getchannel("A"), dtype=np.uint8)
    scaled_y, _ = np.where(scaled_alpha >= ALPHA_THRESHOLD)
    visible_y, visible_x = np.where(scaled_alpha > 0)
    visible_left, visible_right = int(visible_x.min()), int(visible_x.max())
    # Center the complete scaled silhouette; this is a translation only and
    # avoids clipping as the pose contracts toward its body.
    x = round((CELL_SIZE - (visible_right - visible_left + 1)) / 2 - visible_left)
    y = BASELINE_Y - int(scaled_y.max())
    cell = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0))
    cell.alpha_composite(scaled, (x, y))
    return cell


def remove_distant_specks(image: Image.Image) -> Image.Image:
    """Drop tiny disconnected matte specks while retaining nearby fur details."""
    rgba = np.asarray(image, dtype=np.uint8).copy()
    mask = rgba[:, :, 3] >= ALPHA_THRESHOLD
    height, width = mask.shape
    seen = np.zeros_like(mask)
    components: list[list[tuple[int, int]]] = []
    for seed_y, seed_x in zip(*np.where(mask)):
        if seen[seed_y, seed_x]:
            continue
        stack = [(int(seed_y), int(seed_x))]
        seen[seed_y, seed_x] = True
        component: list[tuple[int, int]] = []
        while stack:
            y, x = stack.pop()
            component.append((y, x))
            for next_y, next_x in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= next_y < height and 0 <= next_x < width and mask[next_y, next_x] and not seen[next_y, next_x]:
                    seen[next_y, next_x] = True
                    stack.append((next_y, next_x))
        components.append(component)
    if not components:
        return image
    main = max(components, key=len)
    main_mask = np.zeros_like(mask, dtype=np.uint8)
    main_y, main_x = zip(*main)
    main_mask[main_y, main_x] = 255
    nearby = np.asarray(Image.fromarray(main_mask, "L").filter(ImageFilter.MaxFilter(37))) > 0
    for component in components:
        if len(component) < 16 and not any(nearby[y, x] for y, x in component):
            ys, xs = zip(*component)
            rgba[ys, xs, :] = 0
    return Image.fromarray(rgba, "RGBA")


def update_spec(source: dict[str, object], groups: list[tuple[int, int]]) -> dict[str, object]:
    spec = copy.deepcopy(source)
    spec["atlasPath"] = "pets/ginger/ginger_rest_v2.png"
    spec["previewPath"] = "pets/ginger/ginger_rest_v2.png"
    spec["rows"] = OUTPUT_ROWS
    spec["frameCount"] = FRAME_COUNT
    clips = [clip for clip in spec["clips"] if clip.get("id") not in {"sleep", "wake"}]
    clips.extend([
        {"id": "sleep", "frames": [18, 19, 20, 21], "loop": False, "frameDurationMs": 180},
        {"id": "wake", "frames": [21, 20, 19, 18], "loop": False, "frameDurationMs": 180},
    ])
    spec["clips"] = clips
    frames = list(spec["frames"])
    for offset, group in enumerate(groups):
        frames.append({"index": 19 + offset, "name": ("rest_crouch", "rest_sternal", "rest_curl")[offset], "sourceHint": "rest-transition-2026-09-13", "sourceGroup": list(group), "uniformScale": SCALE})
    spec["frames"] = frames
    return spec


def write_review(cells: list[tuple[int, Image.Image, str]]) -> None:
    width, height = CELL_SIZE + 8, CELL_SIZE + 30
    sheet = Image.new("RGB", (width * len(cells), height), (244, 244, 244))
    draw = ImageDraw.Draw(sheet)
    for column, (index, cell, label) in enumerate(cells):
        panel = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (244, 244, 244, 255))
        panel.alpha_composite(cell)
        sheet.paste(panel.convert("RGB"), (column * width, 0))
        draw.text((column * width + 4, CELL_SIZE + 7), f"frame {index} {label} c{index % 4} r{index // 4}", fill="black")
    REVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(REVIEW_PATH)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    source_atlas = Image.open(SOURCE_ATLAS).convert("RGBA")
    sheet = Image.open(SOURCE_SHEET).convert("RGBA")
    groups = detect_groups(sheet)
    new_cells = [make_cell(sheet, group) for group in groups]
    atlas = Image.new("RGBA", (COLUMNS * CELL_SIZE, OUTPUT_ROWS * CELL_SIZE), (0, 0, 0, 0))
    atlas.paste(source_atlas, (0, 0))
    for offset, cell in enumerate(new_cells):
        index = 19 + offset
        atlas.alpha_composite(cell, ((index % COLUMNS) * CELL_SIZE, (index // COLUMNS) * CELL_SIZE))
        cell_alpha = np.asarray(cell.getchannel("A"))
        visible_y, visible_x = np.where(cell_alpha > 0)
        assert int(visible_x.min()) >= 8 and int(visible_x.max()) <= CELL_SIZE - 9, f"frame {index} touches horizontal border"
    for original_index in range(19):
        left = (original_index % COLUMNS) * CELL_SIZE
        top = (original_index // COLUMNS) * CELL_SIZE
        assert np.array_equal(
            np.asarray(atlas.crop((left, top, left + CELL_SIZE, top + CELL_SIZE))),
            np.asarray(source_atlas.crop((left, top, left + CELL_SIZE, top + CELL_SIZE))),
        ), f"production atlas cell {original_index} changed"
    atlas.save(OUTPUT_ATLAS)
    spec = json.load(SOURCE_SPEC.open(encoding="utf-8"))
    OUTPUT_SPEC.write_text(json.dumps(update_spec(spec, groups), indent=2) + "\n", encoding="utf-8")
    base18 = source_atlas.crop((18 % 4 * CELL_SIZE, 18 // 4 * CELL_SIZE, (18 % 4 + 1) * CELL_SIZE, (18 // 4 + 1) * CELL_SIZE))
    base2 = source_atlas.crop((2 % 4 * CELL_SIZE, 2 // 4 * CELL_SIZE, (2 % 4 + 1) * CELL_SIZE, (2 // 4 + 1) * CELL_SIZE)).resize((round(CELL_SIZE * .70), round(CELL_SIZE * .70)), Image.Resampling.LANCZOS)
    base2_cell = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0)); base2_cell.alpha_composite(base2, ((CELL_SIZE - base2.width) // 2, (CELL_SIZE - base2.height) // 2))
    write_review([(18, base18, "base"), (19, new_cells[0], "crouch"), (20, new_cells[1], "sternal"), (21, new_cells[2], "curl"), (2, base2_cell, "base×.70")])
    print(json.dumps({"groups": groups, "atlas": str(OUTPUT_ATLAS), "size": atlas.size, "frames": FRAME_COUNT, "review": str(REVIEW_PATH)}))


if __name__ == "__main__":
    main()
