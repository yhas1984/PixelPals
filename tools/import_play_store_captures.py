#!/usr/bin/env python3
"""Import eight reviewed phone or tablet captures into the screenshot editor."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--form-factor", choices=("phone", "tablet"), default="phone")
    parser.add_argument("locale", choices=("es", "en"))
    parser.add_argument("images", nargs=8, type=Path)
    args = parser.parse_args()
    target = ROOT / "screenshots-editor" / "public" / "screenshots" / "android" / args.form_factor / args.locale
    target.mkdir(parents=True, exist_ok=True)
    for index, source in enumerate(args.images, start=1):
        if not source.is_file():
            parser.error(f"Missing capture: {source}")
        with Image.open(source) as image:
            minimum = (1200, 1920) if args.form_factor == "tablet" else (720, 1280)
            if image.width < minimum[0] or image.height < minimum[1]:
                parser.error(f"Capture is too small: {source} is {image.size}")
        shutil.copy2(source, target / f"{index:02d}.png")


if __name__ == "__main__":
    main()
