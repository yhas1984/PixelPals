"""Extract the generated Corgi turn board without changing its frame geometry."""
from __future__ import annotations

from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_sit_frames import clean_sheet


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/corgi/raw/turn-transition-2026-09-12/generated.png"
CLEAN = ROOT / "tools/corgi/clean"
EVIDENCE = ROOT / "docs/companion/evidence/corgi-turn-2026-09-12"
WIDTH = 627
TOP_HEIGHT = 580
SHEET_SIZE = 1254


def frame_boxes() -> tuple[tuple[int, int, int, int], ...]:
    return (
        (0, 0, WIDTH, TOP_HEIGHT),
        (WIDTH, 0, SHEET_SIZE, TOP_HEIGHT),
        (0, TOP_HEIGHT, WIDTH, SHEET_SIZE),
        (WIDTH, TOP_HEIGHT, SHEET_SIZE, SHEET_SIZE),
    )


def main() -> None:
    CLEAN.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    clean = clean_sheet(Image.open(SOURCE))
    for index, box in enumerate(frame_boxes()):
        frame = clean.crop(box)
        frame.save(CLEAN / f"turn-{index}.png", optimize=True)
        print(f"frame={index} size={frame.size} alpha_bbox={frame.getchannel('A').getbbox()}")

    # Preserve the original board geometry while compositing onto a dark review
    # surface, so any checkerboard residue or halo is easy to spot.
    review = Image.new("RGBA", clean.size, "#252238")
    review.alpha_composite(clean)
    draw = ImageDraw.Draw(review)
    for index, (left, top, right, bottom) in enumerate(frame_boxes()):
        draw.rectangle((left, top, right - 1, bottom - 1), outline="#8a6c5c", width=2)
        draw.text((left + 8, top + 8), f"turn {index}", fill="#f4e9df")
    review.convert("RGB").save(EVIDENCE / "extracted.png", optimize=True)

    rgba = np.asarray(clean)
    exterior_border = np.concatenate((rgba[0, :, 3], rgba[-1, :, 3], rgba[:, 0, 3], rgba[:, -1, 3]))
    print(f"sheet_size={clean.size} exterior_border_opaque={int(np.count_nonzero(exterior_border))}")


if __name__ == "__main__":
    main()
