#!/usr/bin/env python3
"""Downscale oversized pet PNGs to reduce APK/AAB size while preserving quality."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
FRAME_DIR = ROOT / "app/src/main/res/drawable-nodpi"
PREVIEW_DIR = ROOT / "app/src/main/res/drawable-xxhdpi"

FRAME_TARGET = 768
PREVIEW_TARGET = 512

Image.MAX_IMAGE_PIXELS = 1 << 30


def downscale(path: Path, target: int) -> bool:
    image = Image.open(path).convert("RGBA")
    if max(image.size) <= target:
        return False
    scale = target / max(image.size)
    resized = image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.Resampling.LANCZOS,
    )
    resized.save(path, optimize=True)
    return True


def main() -> int:
    changed = 0
    for path in sorted(FRAME_DIR.glob("*.png")):
        if downscale(path, FRAME_TARGET):
            changed += 1
            print(f"frame: {path.name}")
    for path in sorted(PREVIEW_DIR.glob("pet_*.png")):
        if downscale(path, PREVIEW_TARGET):
            changed += 1
            print(f"preview: {path.name}")
    print(f"{changed} files downscaled")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
