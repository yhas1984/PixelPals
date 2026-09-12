#!/usr/bin/env python3
"""Pack generated reference poses using the existing Corgi extraction pipeline.

One scale for every generated pose preserves the body shrinking when lying down.
These candidates are isolated from desktop/release atlas selection.
"""
import importlib.util
import json
from pathlib import Path
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("corgi_extract", ROOT / "tools/corgi/build_walk_frames.py")
extract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(extract)
OUTPUT = ROOT / "app/src/debug/assets/companion/pets"
SIZE, FLOOR = 384, 368


def cells(path, columns, rows, cuts=None):
    board = Image.open(path).convert("RGBA")
    result = []
    for index in range(columns * rows):
        x, y = index % columns, index // columns
        if cuts:
            cell = board.crop((cuts[0][x], cuts[1][y], cuts[0][x + 1], cuts[1][y + 1]))
        else:
            cell = board.crop((round(x * board.width / columns), round(y * board.height / rows),
                               round((x + 1) * board.width / columns), round((y + 1) * board.height / rows)))
        cleaned = extract.clean(cell)
        cleaned.putalpha(cleaned.getchannel("A").filter(ImageFilter.MinFilter(3)))
        bounds = cleaned.getchannel("A").getbbox()
        if not bounds or min(bounds[0], bounds[1], cell.width - bounds[2], cell.height - bounds[3]) < 3:
            raise ValueError(f"Empty or clipped {path.name} cell {index}: {bounds}")
        result.append(cleaned.crop(bounds))
    return result


def pack(pet, poses, walk, turns=None):
    turns = turns or []
    extent = max(max(p.size) for p in poses)
    scale = 336 / extent
    frames = []
    for pose in poses:
        resized = pose.resize((round(pose.width * scale), round(pose.height * scale)), Image.Resampling.LANCZOS)
        frame = Image.new("RGBA", (SIZE, SIZE))
        frame.alpha_composite(resized, ((SIZE - resized.width) // 2, FLOOR - resized.height))
        frames.append(frame)
    walk_extent = max(max(p.size) for p in walk)
    for pose in walk + turns:
        factor = 336 / walk_extent
        resized = pose.resize((round(pose.width * factor), round(pose.height * factor)), Image.Resampling.LANCZOS)
        frame = Image.new("RGBA", (SIZE, SIZE))
        frame.alpha_composite(resized, ((SIZE - resized.width) // 2, FLOOR - resized.height))
        frames.append(frame)
    atlas = Image.new("RGBA", (SIZE * 4, SIZE * ((len(frames) + 3) // 4)))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % 4) * SIZE, (index // 4) * SIZE))
    def clip(name, indices, duration, loop=True):
        return dict(id=name, frames=indices, frameDurationMs=duration, loop=loop)
    offset = len(poses)
    clips = [clip("walk", list(range(offset, offset + len(walk))), 160)]
    if turns:
        clips.append(clip("turn", list(range(offset + len(walk), len(frames))), 90, False))
    if pet == "corgi":
        clips += [clip("idle", [0, 0, 0, 0, 1, 2, 1, 3, 0, 0], 240),
                  clip("play", [4, 5, 6, 7, 7, 7], 200), clip("sleep", [8, 9, 10, 11], 300, False),
                  clip("wake", [12, 13, 14, 15], 300, False)]
    else:
        clips += [clip("idle", [4], 800), clip("play", [0, 1, 2, 3, 3, 3], 260),
                  clip("sleep", [4, 5, 6, 7], 300, False), clip("wake", [8, 9, 10, 11], 300, False)]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    atlas.save(OUTPUT / f"{pet}.png", optimize=True)
    metadata = dict(version=3, petId=pet, atlasPath=f"companion/pets/{pet}.png", frameWidth=SIZE,
                    frameHeight=SIZE, columns=4, rows=(len(frames) + 3) // 4, frameCount=len(frames),
                    pivot=dict(x=SIZE // 2, y=FLOOR), clips=clips, frames=[dict(index=i, name=f"{pet}_{i:02d}") for i in range(len(frames))],
                    renderHints=dict(preserveFrameAnchors=True))
    (OUTPUT / f"{pet}.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(f"{pet}: {len(poses)} action poses + {len(walk)} gait + {len(turns)} turn frames; debug candidate only")


def visible(path):
    image = extract.largest_component(Image.open(path).convert("RGBA"))
    return image.crop(image.getchannel("A").getbbox())


if __name__ == "__main__":
    corgi_gait = cells(ROOT / "tools/companion/sources/corgi_walk_turn_v3.png", 4, 3, ([0, 330, 625, 925, 1254], [0, 410, 820, 1254]))
    corgi_poses = cells(ROOT / "tools/corgi/raw/corgi_companion_v3.png", 4, 4, ([0, 325, 610, 915, 1254], [0, 335, 625, 925, 1254]))
    planted = cells(ROOT / "tools/companion/sources/corgi_planted_idle_v3.png", 2, 2)
    factor = corgi_poses[0].height / planted[0].height
    planted = [p.resize((round(p.width * factor), round(p.height * factor)), Image.Resampling.LANCZOS) for p in planted]
    corgi_poses[:4] = planted
    for index in (4, 7, 15): corgi_poses[index] = planted[0].copy()
    corgi_gait[8] = planted[0].copy()
    corgi_gait[11] = planted[0].transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    planted_walk = cells(ROOT / "tools/companion/sources/corgi_planted_walk_v3.png", 4, 2)
    gait_scale = corgi_poses[0].height / planted_walk[0].height
    planted_walk = [p.resize((round(p.width * gait_scale), round(p.height * gait_scale)), Image.Resampling.LANCZOS) for p in planted_walk]
    pack("corgi", corgi_poses, planted_walk, corgi_gait[8:])
    pack("taro", cells(ROOT / "tools/companion/sources/taro_v3.png", 4, 3),
         [visible(p) for p in sorted((ROOT / "tools/taro/pipeline/atlas_v2/frames").glob("taro_*_walk_*.png"))],
         [visible(p) for p in sorted((ROOT / "tools/taro/pipeline/atlas_v2/frames").glob("taro_*_turn_*.png"))])
