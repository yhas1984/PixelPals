"""Build the opt-in Ginger posture supplement; never rewrite the production bank."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw
from tools.corgi.build_sit_frames import clean_sheet

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/ginger/raw/standing-transition-2026-09-12"
OUTPUT = ROOT / "app/src/carePreview/assets/pets/ginger"
EVIDENCE = ROOT / "docs/companion/evidence/ginger-posture-2026-09-12"
CELL = 384
GROUND = 368
# The cut falls in the transparent gap. The right sprite's nose crosses x=887,
# so cutting the generated board into mathematical halves would amputate it.
RISE_CUT = 835
CAMERAS = ((.36, 47, 85), (.36, 10, 85), (.30, -42, 80))


def supplemental_frames() -> list[Image.Image]:
    rising = clean_sheet(Image.open(SOURCE / "rise-generated.png"))
    sources = [rising.crop((0, 0, RISE_CUT, rising.height)),
               rising.crop((RISE_CUT, 0, rising.width, rising.height)),
               clean_sheet(Image.open(SOURCE / "generated.png"))]
    frames = []
    for source, (scale, x, y) in zip(sources, CAMERAS):
        resized = source.resize((round(source.width * scale), round(source.height * scale)), Image.Resampling.LANCZOS)
        frame = Image.new("RGBA", (CELL, CELL))
        frame.alpha_composite(resized, (x, y))
        frames.append(frame)
    return frames


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    original = ROOT / "app/src/main/assets/pets/ginger"
    bank = Image.open(original / "ginger_sheet_v2.png").convert("RGBA")
    assert bank.size == (1536, 1536)
    atlas = Image.new("RGBA", (CELL * 4, CELL * 5))
    # Paste preserves the source RGB even in transparent pixels.
    atlas.paste(bank, (0, 0))
    additions = supplemental_frames()
    for index, frame in enumerate(additions, 16):
        atlas.paste(frame, (index % 4 * CELL, index // 4 * CELL))
    atlas.save(OUTPUT / "ginger_motion_v2.png", optimize=True)
    spec = json.loads((original / "ginger_sheet_v2.json").read_text())
    spec.update(atlasPath="pets/ginger/ginger_motion_v2.png", rows=5, frameCount=19)
    spec["renderHints"].update(preserveFrameAnchors=True, recommendedBleedInsetPx=0, drawScale=1.0)
    spec["clips"] += [
        dict(id="stand_up", frames=[0, 16, 17, 18], loop=False, frameDurationMs=165),
        dict(id="sit_down", frames=[18, 17, 16, 0], loop=False, frameDurationMs=165),
        dict(id="standing", frames=[18], loop=True, frameDurationMs=1000),
    ]
    spec["frames"] += [dict(index=i, name=name, sourceHint="standing-transition-2026-09-12")
                       for i, name in enumerate(("sit_quarter", "rise_half", "stand_planted"), 16)]
    spec["postureCamera"] = dict(
        frameScales={"0": .68, "1": .68, "2": .70},
        ground=GROUND, sourceCell=CELL,
        supplementalTransforms=[dict(scale=s, x=x, y=y) for s, x, y in CAMERAS],
        note="Original frames 0..15 preserved. Camera corrects seated source framing; full-cell transforms, never per-pose bbox fit.",
    )
    (OUTPUT / "ginger_motion_v2.json").write_text(json.dumps(spec, indent=2) + "\n")
    frames = []
    for index in (0, 16, 17, 18, 4, 5, 6, 7):
        frame = atlas.crop((index % 4 * CELL, index // 4 * CELL, (index % 4 + 1) * CELL, (index // 4 + 1) * CELL))
        if index == 0:
            scaled = frame.resize((round(CELL * .68), round(CELL * .68)), Image.Resampling.LANCZOS)
            frame = Image.new("RGBA", (CELL, CELL))
            frame.alpha_composite(scaled, (round(CELL / 2 * .32), round(GROUND * .32)))
        frames.append(frame)
    review = Image.new("RGBA", (CELL * 4, (CELL + 30) * 2), "#292536")
    draw = ImageDraw.Draw(review)
    for i, (index, frame) in enumerate(zip((0, 16, 17, 18, 4, 5, 6, 7), frames)):
        x, y = i % 4 * CELL, i // 4 * (CELL + 30)
        review.alpha_composite(frame, (x, y + 30))
        draw.text((x + 12, y + 8), f"frame {index}", fill="#fff3de")
        draw.line((x, y + 30 + GROUND, x + CELL, y + 30 + GROUND), fill="#a48472")
    review.save(EVIDENCE / "camera-review.png")


if __name__ == "__main__":
    main()
