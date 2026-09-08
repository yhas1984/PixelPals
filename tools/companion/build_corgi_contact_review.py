"""Pack the contact-study board with one scale and registered ground contacts.

Uses the existing project extraction pipeline. Never writes production images.
"""
import importlib.util
import json
from pathlib import Path
from PIL import Image
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
module = importlib.util.spec_from_file_location("extract", ROOT / "tools/corgi/build_walk_frames.py")
extract = importlib.util.module_from_spec(module)
module.loader.exec_module(extract)
SOURCE = ROOT / "tools/companion/sources/corgi_walk_contacts_v4.png"
OUTPUT = ROOT / "app/src/debug/assets/companion/review"
SIZE, FLOOR = 384, 368


def build():
    board = Image.open(SOURCE).convert("RGBA")
    cells = []
    for index in range(8):
        x, y = index % 4, index // 4
        cell = board.crop((round(x * board.width / 4), round(y * board.height / 2),
                           round((x + 1) * board.width / 4), round((y + 1) * board.height / 2)))
        cleaned = extract.clean(cell)
        # Remove cyan matte contamination only from the outlined silhouette edge.
        rgba = np.array(cleaned)
        rgb = rgba[:, :, :3].astype(float)
        excess = np.minimum(rgb[:, :, 1] - rgb[:, :, 0], rgb[:, :, 2] - rgb[:, :, 0])
        fringe = (excess > 10) & (rgba[:, :, 3] > 0)
        rgba[fringe, 1] = rgba[fringe, 0]
        rgba[fringe, 2] = rgba[fringe, 0]
        rgba[fringe, 3] = (rgba[fringe, 3] * (1 - excess[fringe] / 255)).astype(np.uint8)
        cells.append(Image.fromarray(rgba))
    bounds = [cell.getbbox() for cell in cells]
    assert all(bounds), "Empty contact pose"
    scale = (SIZE - 28) / max(bound[2] - bound[0] for bound in bounds)
    atlas = Image.new("RGBA", (SIZE * 4, SIZE * 2))
    for index, (cell, bound) in enumerate(zip(cells, bounds)):
        sprite = cell.crop(bound).resize((round((bound[2] - bound[0]) * scale),
                                         round((bound[3] - bound[1]) * scale)), Image.Resampling.LANCZOS)
        left = round(SIZE / 2 + (bound[0] - cell.width / 2) * scale)
        top = FLOOR - sprite.height
        assert left >= 0 and top >= 0 and left + sprite.width <= SIZE and top + sprite.height <= SIZE
        atlas.alpha_composite(sprite, (index % 4 * SIZE + left, index // 4 * SIZE + top))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    # Lanczos can reintroduce a few tinted edge pixels; remove that residual matte.
    packed = np.array(atlas)
    rgb = packed[:, :, :3].astype(int)
    fringe = (rgb[:, :, 1] > rgb[:, :, 0] + 10) & (rgb[:, :, 2] > rgb[:, :, 0] + 10)
    packed[fringe, 1] = packed[fringe, 0]
    packed[fringe, 2] = packed[fringe, 0]
    atlas = Image.fromarray(packed)
    signatures = set()
    for index in range(8):
        frame = atlas.crop((index % 4 * SIZE, index // 4 * SIZE,
                            (index % 4 + 1) * SIZE, (index // 4 + 1) * SIZE))
        bound = frame.getbbox()
        assert bound and bound[3] == FLOOR, f"Incorrect ground contact in frame {index}"
        assert bound[0] > 0 and bound[1] > 0 and bound[2] < SIZE, f"Clipped frame {index}"
        signatures.add(frame.tobytes())
    assert len(signatures) == 8, "Duplicate contact poses"
    rgb = packed[:, :, :3].astype(int)
    assert not np.any((rgb[:, :, 1] > rgb[:, :, 0] + 20)
                      & (rgb[:, :, 2] > rgb[:, :, 0] + 20)
                      & (packed[:, :, 3] > 64)), "Residual cyan matte"
    atlas.save(OUTPUT / "corgi_walk_v4.png", optimize=True)
    metadata = dict(version=4, petId="corgi", atlasPath="companion/review/corgi_walk_v4.png",
                    frameWidth=SIZE, frameHeight=SIZE, columns=4, rows=2, frameCount=8,
                    pivot=dict(x=SIZE // 2, y=FLOOR),
                    renderHints=dict(filterBitmap=True, useFrameOccupancyNormalization=False),
                    clips=[dict(id="walk_contact_study", frames=list(range(8)), loop=True, frameDurationMs=160)],
                    frames=[dict(index=i, name=f"contact_{i:02d}") for i in range(8)])
    (OUTPUT / "corgi_walk_v4.json").write_text(json.dumps(metadata, indent=2) + "\n")
    original = Image.new("RGBA", (SIZE * 4, SIZE))
    for index in range(4):
        frame = Image.open(ROOT / f"app/src/main/res/drawable-nodpi/corgi_{10 + index}.png").convert("RGBA")
        original.alpha_composite(frame.resize((SIZE, SIZE), Image.Resampling.LANCZOS), (index * SIZE, 0))
    original.save(OUTPUT / "corgi_walk_original.png", optimize=True)
    original_metadata = dict(metadata, atlasPath="companion/review/corgi_walk_original.png", rows=1, frameCount=4,
                             clips=[dict(id="original_walk", frames=list(range(4)), loop=True, frameDurationMs=320)],
                             frames=[dict(index=i, name=f"original_{10 + i}") for i in range(4)])
    (OUTPUT / "corgi_walk_original.json").write_text(json.dumps(original_metadata, indent=2) + "\n")
    print(f"Packed eight review frames, common scale {scale:.5f}, ground {FLOOR}; production untouched")


if __name__ == "__main__":
    build()
