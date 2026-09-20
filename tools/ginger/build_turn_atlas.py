"""Append two planted turning poses; preserve the existing 22-frame rest bank."""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/carePreview/assets/pets/ginger"
SOURCE = ROOT / "tools/ginger/raw/turn-2026-09-20/generated.png"
FRONT_SOURCE = ROOT / "tools/ginger/raw/turn-2026-09-20/front-tail.png"
CELL = 384
BASELINE = 368
# One camera for both drawings; never fit individual body bounds to the cell.
SCALE = 0.365


def build():
    original = Image.open(ASSETS / "ginger_rest_v2.png").convert("RGBA")
    source = Image.open(SOURCE).convert("RGBA")
    front = Image.open(FRONT_SOURCE).convert("RGBA")
    assert front.size == source.size
    # The frontal tail is behind the torso. Remove the isolated tip protruding
    # between the ears; facial, paw and coat pixels below this matte are untouched.
    matte = ImageDraw.Draw(front)
    matte.polygon([(1195, 116), (1251, 110), (1266, 148), (1266, 179),
                   (1194, 179), (1192, 143)], fill=(0, 0, 0, 0))
    assert source.getextrema()[3][0] == 0, "Source must have true transparency"
    atlas = original.copy()
    for index in range(2):
        sheet = source if index == 0 else front
        pose = sheet.crop((index * source.width // 2, 0, (index + 1) * source.width // 2, source.height))
        pose = pose.resize((round(pose.width * SCALE), round(pose.height * SCALE)), Image.Resampling.LANCZOS)
        alpha = np.array(pose.getchannel("A"))
        ys, xs = np.where(alpha >= 8)
        x = round(CELL / 2 - (xs.min() + xs.max()) / 2)
        y = BASELINE - int(ys.max())
        cell = Image.new("RGBA", (CELL, CELL))
        cell.alpha_composite(pose, (x, y))
        atlas.paste(cell, ((22 + index) % 4 * CELL, (22 + index) // 4 * CELL))
    before, after = np.array(original), np.array(atlas)
    for index in range(22):
        x, y = index % 4 * CELL, index // 4 * CELL
        assert np.array_equal(before[y:y+CELL, x:x+CELL], after[y:y+CELL, x:x+CELL]), index
    spec = json.loads((ASSETS / "ginger_rest_v2.json").read_text())
    spec["atlasPath"] = "pets/ginger/ginger_turn_v2.png"
    spec["previewPath"] = spec["atlasPath"]
    spec["frameCount"] = 24
    # Rendering uses GingerTurnMotion for heading changes; preserve every other clip.
    for index in range(22, 24):
        spec["frames"].append({"index": index, "name": "turn_quarter" if index == 22 else "turn_front"})
    atlas.save(ASSETS / "ginger_turn_v2.png")
    (ASSETS / "ginger_turn_v2.json").write_text(json.dumps(spec, indent=2) + "\n")
    sheet = Image.new("RGBA", (CELL * 5, CELL + 28), "#faf7ef")
    for column, (frame, mirrored) in enumerate([(18, False), (22, False), (23, False), (22, True), (18, True)]):
        x, y = frame % 4 * CELL, frame // 4 * CELL
        cell = atlas.crop((x, y, x+CELL, y+CELL))
        if mirrored:
            cell = cell.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        sheet.alpha_composite(cell, (column * CELL, 28))
        ImageDraw.Draw(sheet).text((column * CELL + 8, 8), f"{frame} {'mirror' if mirrored else ''}", fill="#333333")
    sheet.save(ROOT / "tools/ginger/review/turn-contact.png")
    print("Ginger turn bank: 24 frames; first 22 unchanged")


if __name__ == "__main__":
    build()
