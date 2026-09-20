"""Place Ginger's reviewed native rest poses into the care atlas.

The care atlas is 256 px per cell while the native Ginger supplement is 384
px.  These four cells are projected around the authored ground line rather
than fitted to their visible bounds, so the same anatomy and camera survive
the home, desktop, and care paths.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CELL = 256
SOURCE_CELL = 384
SOURCE_GROUND = 368
# Care keeps a 18 px bottom safety margin; the authored source ground is
# therefore projected to the care baseline rather than clipped at 245 px.
TARGET_GROUND = 238.0
# Shared inset for this bank, compensated by CARE_REST_CELL in the renderer.
# Keeps the widest native crouch inside the care atlas's transparent gutter.
CAMERA_INSET = 15 / 16
REST_FRAME_MAP = {16: 0, 17: 19, 18: 20, 19: 21}
FRAME_SCALE = {16: .68, 17: 1.0, 18: 1.0, 19: 1.0}

# Reviewed native landmarks, in the 384 px source cells.  They describe the
# head, mouth, and torso centre used by care contact choreography; ground is
# deliberately the shared authored baseline, never the visible bbox.
SOURCE_ANCHORS = {
    0: ((192, 105), (192, 151), (192, 235)),
    19: ((100, 190), (72, 211), (190, 257)),
    20: ((107, 220), (80, 242), (190, 278)),
    21: ((111, 255), (72, 275), (210, 285)),
}


def _project(point: tuple[int, int], scale: float) -> list[float]:
    """Project a source point around (192, 368) into a care cell."""
    # Native Ginger faces left; the shared care player uses right-facing art
    # and mirrors it once when the pet's live heading is left.
    x, y = SOURCE_CELL - point[0], point[1]
    factor = CELL / SOURCE_CELL * scale * CAMERA_INSET
    return [round((x - SOURCE_CELL / 2) * factor + CELL / 2, 5),
            round((y - SOURCE_GROUND) * factor + TARGET_GROUND, 5)]


def _native_cell(native: Image.Image, frame: int, scale: float) -> Image.Image:
    source = native.crop((frame % 4 * SOURCE_CELL, frame // 4 * SOURCE_CELL,
                          (frame % 4 + 1) * SOURCE_CELL,
                          (frame // 4 + 1) * SOURCE_CELL))
    source = source.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    factor = CELL / SOURCE_CELL * scale * CAMERA_INSET
    size = max(1, round(SOURCE_CELL * factor))
    sprite = source.resize((size, size), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (CELL, CELL))
    left = round(CELL / 2 - SOURCE_CELL / 2 * factor)
    top = round(TARGET_GROUND - SOURCE_GROUND * factor)
    out.alpha_composite(sprite, (left, top))
    return out


def apply_rest_care(atlas: Image.Image, anchors: list[dict], transforms: list[dict],
                    native_path: Path | None = None) -> Image.Image:
    """Replace care REST frames 16..19, leaving the other 20 cells byte exact."""
    path = native_path or ROOT / "app/src/carePreview/assets/pets/ginger/ginger_turn_v2.png"
    native = Image.open(path).convert("RGBA")
    result = atlas.copy()
    for care_frame, native_frame in REST_FRAME_MAP.items():
        scale = FRAME_SCALE[care_frame]
        cell = _native_cell(native, native_frame, scale)
        box = (care_frame % 4 * CELL, care_frame // 4 * CELL,
               (care_frame % 4 + 1) * CELL, (care_frame // 4 + 1) * CELL)
        result.paste(cell, box[:2])
        head, mouth, body = SOURCE_ANCHORS[native_frame]
        anchors[care_frame] = {
            "mouth": [value / CELL for value in _project(mouth, scale)],
            "head": [value / CELL for value in _project(head, scale)],
            "body": [value / CELL for value in _project(body, scale)],
            "ground": [0.5, round(TARGET_GROUND / CELL, 5)],
        }
        transforms[care_frame] = {
            "source": str(path.relative_to(ROOT)),
            "sourceFrame": native_frame,
            "sourceCell": SOURCE_CELL,
            "targetCell": CELL,
            "cameraScale": scale,
            "cameraInset": CAMERA_INSET,
            "mirroredForCare": True,
            "ground": [SOURCE_GROUND, round(TARGET_GROUND, 5)],
        }
    return result
