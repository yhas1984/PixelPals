#!/usr/bin/env python3
"""Import the reviewed screenshot-editor bundle into play-store assets."""

from __future__ import annotations

import argparse
import shutil
import zipfile
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
DECKS = (
    (Path("android/android/1080x1920"), Path("assets/screenshots"), (1080, 1920)),
    (Path("android/android-7/1200x1920"), Path("assets/screenshots-tablet-7"), (1200, 1920)),
    (Path("android/android-10/1600x2560"), Path("assets/screenshots-tablet-10"), (1600, 2560)),
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("bundles", nargs="+", type=Path)
    args = parser.parse_args()
    for bundle in args.bundles:
        if not bundle.is_file():
            parser.error(f"Missing screenshot bundle: {bundle}")

    archives = [zipfile.ZipFile(bundle) for bundle in args.bundles]
    try:
        for source_root, target_root, expected_size in DECKS:
            archive = next(
                (candidate for candidate in archives if any(Path(name).parent == source_root / "es" for name in candidate.namelist())),
                None,
            )
            if archive is None:
                parser.error(f"No bundle contains {source_root}")
            names = set(archive.namelist())
            for locale in ("es", "en"):
                matching = sorted(
                    name
                    for name in names
                    if Path(name).parent == source_root / locale and name.endswith(".png")
                )
                if len(matching) != 8:
                    parser.error(f"Expected 8 {source_root} {locale} screenshots, found {len(matching)}")

                destination = ROOT / "play-store" / target_root / locale
                destination.mkdir(parents=True, exist_ok=True)
                for old in destination.glob("*.png"):
                    old.unlink()

                for index, name in enumerate(matching, start=1):
                    with archive.open(name) as source:
                        output = destination / f"{index:02d}.png"
                        with output.open("wb") as target:
                            shutil.copyfileobj(source, target)
                    with Image.open(output) as image:
                        if image.size != expected_size:
                            parser.error(f"{name} is {image.size}, expected {expected_size}")
    finally:
        for archive in archives:
            archive.close()


if __name__ == "__main__":
    main()
