"""Compose canonical Corgi blink variants from the exact open-eye body.

Only the two eye regions are taken from frames 4 and 5.  This keeps the
sit-3 silhouette, muzzle, ears, paws and all other anatomy stable for later
camera alignment.
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
CLEAN = ROOT / "tools/corgi/clean"
REVIEW = ROOT / "tools/corgi/review"
EVIDENCE = ROOT / "docs/companion/evidence/corgi-sit-2026-09-12"

# Local 512x512 coordinates, reviewed against the clean face crops.
EYE_BOXES = ((305, 156, 349, 194), (378, 148, 405, 180))
# Source coordinates are left of the canonical eye centers in the half-blink.
SOURCE_OFFSETS = {4: ((-12, 0), (-12, 0)), 5: ((-1, 0), (0, 0))}


def eye_mask(size: tuple[int, int], box: tuple[int, int, int, int]) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius=5, fill=255)
    return mask.filter(ImageFilter.GaussianBlur(1.2))


def eye_region(size: tuple[int, int]) -> np.ndarray:
    return np.maximum.reduce([np.asarray(eye_mask(size, box)) for box in EYE_BOXES]) > 0


def compose(index: int) -> Image.Image:
    base = Image.open(CLEAN / "sit-3.png").convert("RGBA")
    source = Image.open(CLEAN / f"sit-{index}.png").convert("RGBA")
    result = base.copy()
    for box, (dx, dy) in zip(EYE_BOXES, SOURCE_OFFSETS[index]):
        # Replace the entire eyelid region, including the skin that closes over
        # the old pupil. Copying only dark ink would leave both eyes superposed.
        aligned = Image.new("RGBA", base.size)
        aligned.paste(source, (-dx, -dy))
        result = Image.composite(aligned, result, eye_mask(base.size, box))
    pixels, reference = np.asarray(result), np.asarray(base)
    if not np.array_equal(pixels[~eye_region(base.size)], reference[~eye_region(base.size)]):
        raise AssertionError(f"sit-{index}: pixels outside eyes changed")
    if not np.array_equal(pixels[:, :, 3], reference[:, :, 3]):
        raise AssertionError(f"sit-{index}: transparency changed")
    return result


def main() -> None:
    REVIEW.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    outputs = {index: compose(index) for index in (4, 5)}
    for index, image in outputs.items():
        image.save(REVIEW / f"sit-{index}.png", optimize=True)

    # Enlarged face review, composited over dark background to expose halos.
    canvas = Image.new("RGBA", (768, 300), "#252238")
    for slot, (index, image) in enumerate(((3, Image.open(CLEAN / "sit-3.png")), *outputs.items())):
        face = image.crop((250, 100, 450, 250)).resize((256, 256))
        canvas.alpha_composite(face, (slot * 256, 0))
    canvas.convert("RGB").save(EVIDENCE / "sit-face-variants-dark.png", optimize=True)

    for index, image in outputs.items():
        base = np.asarray(Image.open(CLEAN / "sit-3.png").convert("RGBA"))
        result = np.asarray(image)
        assert np.array_equal(result[~eye_region((512, 512))], base[~eye_region((512, 512))])
        assert np.array_equal(result[:, :, 3], base[:, :, 3])
        print(f"sit-{index}: eye_region_pixels={int(eye_region((512, 512)).sum())} outside_identical=true transparency_identical=true")


if __name__ == "__main__":
    main()
