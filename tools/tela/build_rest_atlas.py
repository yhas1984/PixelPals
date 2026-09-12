#!/usr/bin/env python3
"""Build Tela's opt-in rest atlas from the reviewed curl transition sheet.

The 40 production cells are copied byte-for-byte from the production atlas.
The three additional cells use one uniform scale and a shared foot baseline;
no per-pose bounding-box normalization is performed.
"""

from __future__ import annotations

import copy
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
SOURCE_ATLAS = ROOT / "app/src/main/assets/pets/tela/tela_motion_v2.png"
SOURCE_SPEC = ROOT / "app/src/main/assets/pets/tela/tela_motion_v2.json"
SOURCE_SHEET = ROOT / "tools/tela/raw/curl-transition-2026-09-13/cleaned.png"
OUTPUT_DIR = ROOT / "app/src/carePreview/assets/pets/tela"
OUTPUT_ATLAS = OUTPUT_DIR / "tela_rest_v2.png"
OUTPUT_SPEC = OUTPUT_DIR / "tela_rest_v2.json"
REVIEW_PATH = ROOT / "tools/tela/review/rest-contact.png"

CELL_SIZE = 384
COLUMNS = 8
SOURCE_ROWS = 5
OUTPUT_ROWS = 6
NEW_FRAME_COUNT = 43
UNIFORM_SCALE = 0.46
FOOT_BASELINE_Y = 368
ALPHA_THRESHOLD = 8


def detect_groups(image: Image.Image) -> list[tuple[int, int]]:
    """Return three alpha-connected column groups, including the tiny bridge."""
    alpha = np.asarray(image.getchannel("A"), dtype=np.uint8)
    occupied = (alpha >= ALPHA_THRESHOLD).any(axis=0)
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
    # The third pose has a two-pixel antialiased bridge separated from its
    # main run. It is part of that pose, never an independent frame.
    if len(runs) != 4:
        raise ValueError(f"Expected three groups plus one bridge, found {runs}")
    return [runs[0], runs[1], (runs[2][0], runs[3][1])]


def make_cell(sheet: Image.Image, group: tuple[int, int]) -> Image.Image:
    """Scale a pose uniformly, center its face, and align feet to y=368."""
    left, right = group
    rgba = np.asarray(sheet, dtype=np.uint8)
    crop = Image.fromarray(rgba[:, left : right + 1, :], "RGBA")
    alpha = np.asarray(crop.getchannel("A"), dtype=np.uint8)
    ys, xs = np.where(alpha >= ALPHA_THRESHOLD)
    if len(xs) == 0:
        raise ValueError(f"Empty pose group {group}")
    top = int(ys.min())
    bottom = int(ys.max())
    # The face occupies the upper 45% of the visible pose. Centering this
    # stable camera feature avoids horizontal drift as the legs curl inward.
    face = ys <= top + round((bottom - top) * 0.45)
    face_center = float(xs[face].mean()) if np.any(face) else float(xs.mean())
    scaled = crop.resize(
        (max(1, round(crop.width * UNIFORM_SCALE)), max(1, round(crop.height * UNIFORM_SCALE))),
        Image.Resampling.LANCZOS,
    )
    scaled_alpha = np.asarray(scaled.getchannel("A"), dtype=np.uint8)
    scaled_y, scaled_x = np.where(scaled_alpha >= ALPHA_THRESHOLD)
    scaled_face_center = face_center * UNIFORM_SCALE
    scaled_bottom = int(scaled_y.max())
    x = round(CELL_SIZE / 2 - scaled_face_center)
    y = FOOT_BASELINE_Y - scaled_bottom
    cell = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), (0, 0, 0, 0))
    cell.alpha_composite(scaled, (x, y))
    return cell


def add_metadata(spec: dict[str, object], groups: list[tuple[int, int]]) -> dict[str, object]:
    result = copy.deepcopy(spec)
    result["atlasPath"] = "pets/tela/tela_rest_v2.png"
    result["previewPath"] = "pets/tela/tela_rest_v2.png"
    result["rows"] = OUTPUT_ROWS
    result["frameCount"] = NEW_FRAME_COUNT
    result["renderHints"]["preserveFrameAnchors"] = True
    clips = list(result["clips"])
    clips = [clip for clip in clips if clip.get("id") != "sleep"]
    clips.append({"id": "sleep", "frames": [39, 40, 41, 42], "loop": False, "frameDurationMs": 240})
    clips.append({"id": "wake", "frames": [42, 41, 40, 39], "loop": False, "frameDurationMs": 240})
    result["clips"] = clips
    frames = list(result["frames"])
    details = list(result["frameDetails"])
    for offset, group in enumerate(groups):
        index = 40 + offset
        name = f"rest_curl_{offset:02d}"
        frames.append({"index": index, "name": name})
        details.append({
            "index": index,
            "name": name,
            "source": "tools/tela/raw/curl-transition-2026-09-13/cleaned.png",
            "sourceGroup": [group[0], group[1]],
            "uniformScale": UNIFORM_SCALE,
            "pivot": {"x": 192, "y": FOOT_BASELINE_Y},
        })
    result["frames"] = frames
    result["frameDetails"] = details
    return result


def write_review(cells: list[Image.Image]) -> None:
    REVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    panel_w, panel_h = CELL_SIZE, CELL_SIZE + 28
    sheet = Image.new("RGB", (panel_w * 4, panel_h * 2), "white")
    draw = ImageDraw.Draw(sheet)
    for row, background in enumerate(((246, 246, 246, 255), (28, 30, 36, 255))):
        for column, cell in enumerate(cells):
            panel = Image.new("RGBA", (CELL_SIZE, CELL_SIZE), background)
            panel.alpha_composite(cell)
            sheet.paste(panel.convert("RGB"), (column * panel_w, row * panel_h))
            draw.text((column * panel_w + 8, row * panel_h + CELL_SIZE + 6), f"frame {39 + column}", fill="black" if row == 0 else "white")
    sheet.save(REVIEW_PATH)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    source_atlas = Image.open(SOURCE_ATLAS).convert("RGBA")
    source_sheet = Image.open(SOURCE_SHEET).convert("RGBA")
    groups = detect_groups(source_sheet)
    new_cells = [make_cell(source_sheet, group) for group in groups]
    atlas = Image.new("RGBA", (COLUMNS * CELL_SIZE, OUTPUT_ROWS * CELL_SIZE), (0, 0, 0, 0))
    atlas.paste(source_atlas, (0, 0))
    for offset, cell in enumerate(new_cells):
        atlas.alpha_composite(cell, ((40 + offset) % COLUMNS * CELL_SIZE, (40 + offset) // COLUMNS * CELL_SIZE))
    atlas.save(OUTPUT_ATLAS)
    spec = json.load(SOURCE_SPEC.open(encoding="utf-8"))
    OUTPUT_SPEC.write_text(json.dumps(add_metadata(spec, groups), indent=2) + "\n", encoding="utf-8")
    original_sleep = source_atlas.crop((7 * CELL_SIZE, 4 * CELL_SIZE, 8 * CELL_SIZE, 5 * CELL_SIZE))
    write_review([original_sleep, *new_cells])
    original_region = np.asarray(source_atlas)
    output_region = np.asarray(atlas.crop((0, 0, source_atlas.width, source_atlas.height)))
    assert np.array_equal(output_region, original_region), "production atlas cells changed"
    print(json.dumps({"groups": groups, "atlas": str(OUTPUT_ATLAS), "size": atlas.size, "frames": NEW_FRAME_COUNT, "review": str(REVIEW_PATH)}))


if __name__ == "__main__":
    main()
