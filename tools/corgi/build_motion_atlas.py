"""Build the candidate Corgi V2 motion atlas for carePreview review."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
LEGACY = ROOT / "app/src/main/res/drawable-nodpi"
CLEAN = ROOT / "tools/corgi/clean"
REVIEW = ROOT / "tools/corgi/review"
OUT_DIR = ROOT / "app/src/carePreview/assets/pets/corgi"
EVIDENCE = ROOT / "docs/companion/evidence/corgi-sit-2026-09-12"
CELL = 256
COLS, ROWS = 4, 6


def legacy_frame(index: int) -> Image.Image:
    return Image.open(LEGACY / f"corgi_{index}.png").convert("RGBA").resize((CELL, CELL), Image.Resampling.LANCZOS)


def generated_frame(index: int) -> Image.Image:
    source = (CLEAN if index < 4 else REVIEW) / f"sit-{index}.png"
    sprite = Image.open(source).convert("RGBA").resize((212, 212), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (CELL, CELL))
    frame.paste(sprite, ([23, 27, 27, 28, 28, 28][index], 55))
    return frame


def frame_sources() -> list[Image.Image]:
    return ([legacy_frame(i) for i in range(14)] + [generated_frame(i) for i in range(6)]
            + [turn_frame(i) for i in range(1, 4)])


def turn_frame(index: int) -> Image.Image:
    source = Image.open(CLEAN / f"turn-{index}.png").convert("RGBA")
    # One camera for the entire turn board. Only translation varies with the
    # supporting paws; frontal foreshortening must not trigger bbox fitting.
    source = source.resize((round(source.width * .94), round(source.height * .94)), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (768, 768))
    frame.paste(source, [(112, 211), (61, 179), (93, 179)][index - 1])
    return frame.resize((CELL, CELL), Image.Resampling.LANCZOS)


def build_atlas(frames: list[Image.Image]) -> Image.Image:
    atlas = Image.new("RGBA", (COLS * CELL, ROWS * CELL), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        # Direct placement preserves the source RGBA bytes, including
        # transparent RGB, instead of blending them into the atlas canvas.
        atlas.paste(frame, ((index % COLS) * CELL, (index // COLS) * CELL))
    return atlas


def metadata() -> dict:
    clips = [
        ("idle", [0], True, 500), ("walk", [10, 11, 12, 13], True, 130),
        ("turn", [0, 20, 21, 22, 21, 20, 0], False, 80), ("play", [2, 0], True, 350),
        ("sit_down", [0, 15, 16, 17], False, 160), ("sit", [17, 18, 19, 18, 17], True, 350),
        ("stand_up", [17, 16, 15, 0], False, 160), ("sleep", [17], False, 500),
        ("wake", [17, 16, 15, 0], False, 160),
    ]
    return {
        "version": 2, "petId": "corgi", "atlasPath": "pets/corgi/corgi_motion_v2.png",
        "frameWidth": CELL, "frameHeight": CELL, "columns": COLS, "rows": ROWS, "frameCount": 23,
        "pivot": {"x": 128, "y": 128},
        "renderHints": {"innerTransparentPaddingPx": 4, "recommendedBleedInsetPx": 0,
                         "filterBitmap": True, "preserveFrameAnchors": True, "drawScale": 1.0},
        "anchorTable": {"scale": 212 / 512, "offsetsX": [23, 27, 27, 28, 28, 28],
                        "offsetY": 55, "frontPawContact": 454 / 3, "ground": 740 / 3},
        "turnCamera": {"scale": .94, "canvas": 768, "offsets": [[112, 211], [61, 179], [93, 179]],
                       "mirroredSegments": [False, False, False, False, True, True, True]},
        "clips": [{"id": i, "frames": f, "loop": loop, "frameDurationMs": ms} for i, f, loop, ms in clips],
        "frames": [{"index": i, "name": (f"legacy_{i}" if i < 14 else f"sit_{i - 14}" if i < 20 else f"turn_{i - 19}")} for i in range(23)],
    }


def evidence_sheet(frames: list[Image.Image]) -> Image.Image:
    indices = [0, 15, 16, 17, 18, 19]
    sheet = Image.new("RGBA", (7 * CELL, CELL + 40), "#252238")
    draw = ImageDraw.Draw(sheet)
    for slot, index in enumerate(indices):
        sheet.alpha_composite(frames[index], (slot * CELL, 20))
        draw.text((slot * CELL + 6, 6), f"motion {index}", fill="#f4e9df")
    care = Image.open(OUT_DIR / "care_v1.png").convert("RGBA").crop((0, 1024, 256, 1280))
    care = care.resize((246, 246), Image.Resampling.LANCZOS)
    sheet.alpha_composite(care, (6 * CELL + 5, 20 + round(740 / 3 - 256 * .93 * .96)))
    draw.text((6 * CELL + 6, 6), "care 16", fill="#f4e9df")
    draw.line((0, 20 + 740 / 3, sheet.width, 20 + 740 / 3), fill="#887070")
    return sheet


def main() -> None:
    frames = frame_sources()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    atlas = build_atlas(frames)
    atlas.save(OUT_DIR / "corgi_motion_v2.png", optimize=True)
    spec = metadata()
    spec["assetSha256"] = hashlib.sha256((OUT_DIR / "corgi_motion_v2.png").read_bytes()).hexdigest()
    spec["sourceSha256"] = {f"corgi_{i}.png": hashlib.sha256((LEGACY / f"corgi_{i}.png").read_bytes()).hexdigest() for i in range(14)}
    (OUT_DIR / "corgi_motion_v2.json").write_text(json.dumps(spec, indent=2) + "\n")
    evidence_sheet(frames).convert("RGB").save(EVIDENCE / "corgi-motion-comparison.png", optimize=True)
    print(json.dumps({"atlas": spec["assetSha256"], "legacy": spec["sourceSha256"], "frames": len(frames)}, indent=2))


if __name__ == "__main__":
    main()
