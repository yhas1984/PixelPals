"""Deterministic, locally authorized extraction of generated care pose boards.

Original boards are immutable. Only border-connected near-neutral background is
removed, preserving enclosed white fur. Contact coordinates are calibrated in
source-board coordinates and transformed alongside the sprite, never guessed at
runtime. Run from any directory with Python 3, Pillow and numpy.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT))
from tools.pet_pipeline import _components
CELL = 256
PADDING = 18
ACTIONS = ("feed", "play", "pet", "clean", "rest", "medicine")
SEQUENCES = ([0, 0, 1, 1, 1, 2, 2, 3], [0, 1, 1, 1, 2, 2, 2, 2, 3, 3],
             [0, 1, 2, 1, 2, 3], [0, 1, 1, 1, 2, 2, 2, 3],
             [0, 1, 2, 2, 3, 3, 3, 3, 3, 3], [0, 1, 1, 2, 2, 3])
MARKERS = (3500, 4000, 2500, 3500, 4000, 2500)


def clip_frames(pet: str, row: int) -> list[int]:
    """Reviewed pose selection; never restore the imp's malformed three-eye pose.

    The imp eats standing and plays with its hands, not on all fours. Keep the
    original board immutable, but exclude unsuitable poses from playback.
    """
    frames = [row * 4 + value for value in SEQUENCES[row]]
    if pet == "diablillo":
        frames = [3 if frame in (0, 4, 8) else frame for frame in frames]
        if row == 0:
            # Preserve the quick bites, then open the mouth for one short fire burp.
            frames = [3, 3, 1, 1, 1, 2, 2, 6, 6, 6, 3]
        if row == 1:
            # Keep both reaching hands on the separately rendered trident.
            frames = [5] * 14
        if row == 2:
            frames = [3, 9, 9, 10, 10, 11]
        if row == 4:
            # Upright, eyes closed, wrapped in its own wings rather than a bed.
            frames = [16, 17, 9, 9, 9, 9, 9, 9, 9, 9]
    if pet == "moki" and row == 1:
        # Peek around a leaf; tongue-catching belongs to feeding, not playing.
        frames = [4, 4, 8, 8, 9, 9, 8, 8, 11, 11]
    if pet == "nube_michi":
        if row == 0:
            frames = [3, 3, 9, 9, 9, 10, 10, 3]
        if row == 1:
            # Airy cloud poses, never Ginger's crouch/paw/pounce sequence.
            frames = [3, 3, 8, 8, 9, 9, 11, 11, 3, 3]
    return frames


def clip_frame_ms(pet: str, row: int) -> int:
    if pet == "diablillo":
        return (300, 300, 700, 400, 500, 400)[row]
    return 500


def completion_ms(pet: str, row: int) -> int:
    if pet == "diablillo" and row == 1:
        return 3600
    return MARKERS[row] * clip_frame_ms(pet, row) // 500


def extract_background(image: Image.Image) -> Image.Image:
    """Flood only the exterior; white body interiors remain opaque."""
    rgb = np.asarray(image.convert("RGB"))
    neutral = rgb.max(axis=2).astype(int) - rgb.min(axis=2).astype(int) < 6
    candidate = neutral & (rgb.min(axis=2) >= 228)
    padded = np.pad(candidate.astype(np.uint8) * 255, 1, constant_values=255)
    # Pillow floodfill uses PixelAccess; copy the read-only numpy-backed image.
    flood = Image.fromarray(padded).copy()
    ImageDraw.floodfill(flood, (0, 0), 128)
    exterior = np.asarray(flood)[1:-1, 1:-1] == 128
    rgba = np.dstack((rgb, np.where(exterior, 0, 255).astype(np.uint8)))
    rgba[exterior, :3] = 0
    return Image.fromarray(rgba)


def find_cuts(occupancy: np.ndarray, count: int) -> list[int]:
    """Choose actual whitespace valleys, not fixed cuts through paws or ears."""
    extent = len(occupancy)
    cuts = [0]
    for index in range(1, count):
        center = round(extent * index / count)
        radius = round(extent / count * 0.40)
        candidates = np.arange(center - radius, center + radius + 1)
        # Prefer empty valleys nearest the expected grid boundary.
        scores = occupancy[candidates] * 1000 + abs(candidates - center)
        cuts.append(int(candidates[np.argmin(scores)]))
    return cuts + [extent]


def extract_cells(image: Image.Image) -> tuple[list[Image.Image], list[tuple[int, int, int, int]]]:
    mask = np.asarray(image.getchannel("A")) > 0
    rows = find_cuts(mask.sum(axis=1), 6)
    cells, bounds = [], []
    for top, bottom in zip(rows, rows[1:]):
        columns = find_cuts(mask[top:bottom].sum(axis=0), 4)
        for left, right in zip(columns, columns[1:]):
            cell = image.crop((left, top, right, bottom))
            rgba = np.array(cell)
            components = sorted(_components(rgba[:, :, 3] > 0), key=len, reverse=True)
            if components:
                cutoff = max(20, len(components[0]) * .008)
                for component in components[1:]:
                    if len(component) < cutoff:
                        points = np.asarray(component)
                        rgba[points[:, 0], points[:, 1]] = 0
                cell = Image.fromarray(rgba)
            bbox = cell.getbbox()
            if bbox is None:
                raise ValueError("Empty care frame")
            cells.append(cell.crop(bbox))
            bounds.append((left + bbox[0], top + bbox[1], left + bbox[2], top + bbox[3]))
    return cells, bounds


def build(pet: str, calibration: dict) -> dict:
    source = HERE / "source" / f"{pet}.png"
    original = Image.open(source).convert("RGB")
    clean = extract_background(original)
    cells, bounds = extract_cells(clean)
    scale = (CELL - 2 * PADDING) / max(max(cell.size) for cell in cells)
    atlas = Image.new("RGBA", (CELL * 4, CELL * 6))
    anchors, transforms = [], []
    for index, (cell, bbox) in enumerate(zip(cells, bounds)):
        size = tuple(round(dimension * scale) for dimension in cell.size)
        sprite = cell.resize(size, Image.Resampling.NEAREST if pet == "nube_michi" else Image.Resampling.LANCZOS)
        offset = ((CELL - size[0]) // 2, CELL - PADDING - size[1])
        atlas.alpha_composite(sprite, (index % 4 * CELL + offset[0], index // 4 * CELL + offset[1]))
        transform = {"sourceBounds": bbox, "offset": offset, "scale": scale}
        transforms.append(transform)
        points = calibration.get(pet, {}).get(str(index))
        if points is None:
            # Draft anchors must be reviewed and replaced in anchors.json.
            points = {"mouth": [bbox[0] + cell.width * .6, bbox[1] + cell.height * .56],
                      "head": [bbox[0] + cell.width * .52, bbox[1] + cell.height * .25],
                      "body": [bbox[0] + cell.width * .5, bbox[1] + cell.height * .65],
                      "ground": [(bbox[0] + bbox[2]) / 2, bbox[3]]}
        frame_anchors = {name: [round((offset[axis] + (point[axis] - bbox[axis]) * scale) / CELL, 5)
                               for axis in (0, 1)] for name, point in points.items()}
        measured = calibration.get(pet, {})
        if "mouth" in measured:
            frame_anchors["mouth"] = [value / CELL for value in measured["mouth"][index]]
            mouth_x, mouth_y = measured["mouth"][index]
            head = measured.get("head", {}).get(str(index), [mouth_x, max(offset[1] + 12, mouth_y - measured.get("foreheadOffset", 46))])
            frame_anchors["head"] = [value / CELL for value in head]
        anchors.append(frame_anchors)
    directory = ROOT / "app/src/debug/assets/pets" / pet
    directory.mkdir(parents=True, exist_ok=True)
    atlas.save(directory / "care_v1.png", optimize=True)
    spec = {"version": 1, "petId": pet, "atlasPath": f"pets/{pet}/care_v1.png",
            "frameWidth": CELL, "frameHeight": CELL, "columns": 4, "rows": 6, "frameCount": 24,
            "pivot": {"x": CELL // 2, "y": CELL - PADDING},
            "renderHints": {"innerTransparentPaddingPx": 16, "filterBitmap": pet != "nube_michi",
                            "useFrameOccupancyNormalization": False},
            "clips": [{"id": action, "frames": clip_frames(pet, row),
                       "loop": False, "frameDurationMs": clip_frame_ms(pet, row)} for row, action in enumerate(ACTIONS)],
            "frames": [{"index": index, "name": f"{ACTIONS[index // 4]}_{index % 4}"} for index in range(24)],
            "careActions": {action: {"completionMs": completion_ms(pet, row)} for row, action in enumerate(ACTIONS)},
            "anchors": anchors}
    (directory / "care_v1.json").write_text(json.dumps(spec, indent=2) + "\n")
    review = Image.new("RGB", atlas.size, "#d8d0e8")
    review.paste(atlas, mask=atlas.getchannel("A"))
    drawing = ImageDraw.Draw(review)
    for index, points in enumerate(anchors):
        x, y = index % 4 * CELL, index // 4 * CELL
        drawing.rectangle((x, y, x + CELL - 1, y + CELL - 1), outline="#998aaa")
        drawing.text((x + 5, y + 5), f"{index}: {ACTIONS[index // 4]}", fill="#312640")
        for name, color in (("mouth", "red"), ("head", "cyan"), ("body", "blue"), ("ground", "green")):
            px, py = x + points[name][0] * CELL, y + points[name][1] * CELL
            drawing.ellipse((px - 3, py - 3, px + 3, py + 3), fill=color)
    review_dir = HERE / "review"
    review_dir.mkdir(exist_ok=True)
    review.save(review_dir / f"{pet}.png")
    return {"pet": pet, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "atlasSha256": hashlib.sha256((directory / "care_v1.png").read_bytes()).hexdigest(),
            "decodedBytes": atlas.width * atlas.height * 4, "frames": transforms,
            "anchorsCalibrated": len(calibration.get(pet, {}).get("mouth", [])) == 24}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pet", action="append")
    args = parser.parse_args()
    anchors_path = HERE / "anchors.json"
    calibration = json.loads(anchors_path.read_text()) if anchors_path.exists() else {}
    pets = args.pet or sorted(path.stem for path in (HERE / "source").glob("*.png"))
    report = [build(pet, calibration) for pet in pets]
    (HERE / "build_report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps([{key: value for key, value in item.items() if key != "frames"} for item in report], indent=2))


if __name__ == "__main__":
    main()
