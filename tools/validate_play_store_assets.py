#!/usr/bin/env python3
"""Validate the versioned PixelPals Google Play listing and artwork."""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "play-store"
FORBIDDEN = ("ocho mascotas", "eight pets", "tamagotchi", "correcto. aquí", "2.5.0")
EXPECTED_SCREENSHOT_HEADLINES = {
    "es": (
        "Un compañero que se siente vivo",
        "Vive contigo, también en pantalla",
        "15 personalidades únicas",
        "Cuidados para cada mascota",
        "Crea un hogar único",
        "Salid juntos de aventura",
        "Guarda cada recuerdo",
        "Haz crecer vuestro vínculo",
    ),
    "en": (
        "A companion that feels alive",
        "Always with you, right on screen",
        "15 unique personalities",
        "Care that fits every pet",
        "Create a home of your own",
        "Go on adventures together",
        "Keep every memory",
        "Grow a bond that lasts",
    ),
}


def dimensions(path: Path, expected: tuple[int, int], errors: list[str]) -> None:
    if not path.is_file():
        errors.append(f"Missing image: {path.relative_to(ROOT)}")
        return
    with Image.open(path) as image:
        if image.size != expected:
            errors.append(f"{path.relative_to(ROOT)} is {image.size}, expected {expected}")


def main() -> int:
    errors: list[str] = []
    metadata = json.loads((STORE / "metadata.json").read_text(encoding="utf-8"))
    locales = metadata.get("locales", {})
    if set(locales) != {"es", "en"}:
        errors.append("metadata.json must provide exactly es and en")

    all_copy: list[str] = []
    for locale in ("es", "en"):
        listing = locales.get(locale, {})
        title = listing.get("title", "")
        short = listing.get("shortDescription", "")
        description_path = STORE / listing.get("fullDescriptionFile", "missing")
        description = description_path.read_text(encoding="utf-8").strip() if description_path.is_file() else ""
        release_notes_path = STORE / listing.get("releaseNotesFile", "missing")
        release_notes = release_notes_path.read_text(encoding="utf-8").strip() if release_notes_path.is_file() else ""
        if not 1 <= len(title) <= 30:
            errors.append(f"{locale} title has {len(title)} characters; limit is 30")
        if not 1 <= len(short) <= 80:
            errors.append(f"{locale} short description has {len(short)} characters; limit is 80")
        if not 1 <= len(description) <= 4000:
            errors.append(f"{locale} full description has {len(description)} characters; limit is 4000")
        if "15" not in description:
            errors.append(f"{locale} full description must mention all 15 pets")
        if not 1 <= len(release_notes) <= 500:
            errors.append(f"{locale} release notes have {len(release_notes)} characters; limit is 500")
        all_copy.extend((title, short, description, release_notes))

        screenshot_dir = STORE / metadata["assets"]["screenshots"][locale]
        shots = sorted(screenshot_dir.glob("*.png")) if screenshot_dir.is_dir() else []
        if len(shots) != 8:
            errors.append(f"{locale} must contain 8 screenshots, found {len(shots)}")
        for shot in shots:
            dimensions(shot, (1080, 1920), errors)

    lowered = "\n".join(all_copy).lower()
    for phrase in FORBIDDEN:
        if phrase in lowered:
            errors.append(f"Obsolete or prohibited text found: {phrase}")

    dimensions(STORE / metadata["assets"]["icon"], (512, 512), errors)
    for locale, asset in metadata["assets"]["featureGraphics"].items():
        dimensions(STORE / asset, (1024, 500), errors)

    tablet_sizes = {"android7": (1200, 1920), "android10": (1600, 2560)}
    for tablet, expected_size in tablet_sizes.items():
        for locale in ("es", "en"):
            screenshot_dir = STORE / metadata["assets"]["tabletScreenshots"][tablet][locale]
            shots = sorted(screenshot_dir.glob("*.png")) if screenshot_dir.is_dir() else []
            if len(shots) != 8:
                errors.append(f"{tablet} {locale} must contain 8 screenshots, found {len(shots)}")
            for shot in shots:
                dimensions(shot, expected_size, errors)

    for locale in ("es", "en"):
        raw_tablet_dir = ROOT / "screenshots-editor" / "public" / "screenshots" / "android" / "tablet" / locale
        raw_shots = sorted(raw_tablet_dir.glob("*.png")) if raw_tablet_dir.is_dir() else []
        if len(raw_shots) != 8:
            errors.append(f"tablet source {locale} must contain 8 screenshots, found {len(raw_shots)}")
        for shot in raw_shots:
            dimensions(shot, (1600, 2560), errors)

    editor = json.loads((ROOT / "screenshots-editor" / "app-store-screenshots.json").read_text(encoding="utf-8"))
    if editor.get("locales") != ["es", "en"]:
        errors.append("screenshot editor locales must be [es, en]")
    slides = editor.get("slidesByDevice", {}).get("android", [])
    if len(slides) != 8:
        errors.append(f"screenshot editor must define 8 Android phone slides, found {len(slides)}")
    for index, slide in enumerate(slides):
        for locale in ("es", "en"):
            expected = EXPECTED_SCREENSHOT_HEADLINES[locale][index]
            actual = slide.get("headline", {}).get(locale, "").replace("\n", " ")
            if actual != expected:
                errors.append(f"slide {index + 1} {locale} headline is {actual!r}, expected {expected!r}")
    for device in ("android-7", "android-10"):
        tablet_slides = editor.get("slidesByDevice", {}).get(device, [])
        if len(tablet_slides) != 8:
            errors.append(f"screenshot editor must define 8 {device} slides, found {len(tablet_slides)}")
        for index, slide in enumerate(tablet_slides):
            if "/screenshots/android/tablet/" not in slide.get("screenshot", ""):
                errors.append(f"{device} slide {index + 1} must use a real tablet capture")
            for locale in ("es", "en"):
                expected = EXPECTED_SCREENSHOT_HEADLINES[locale][index]
                actual = slide.get("headline", {}).get(locale, "").replace("\n", " ")
                if actual != expected:
                    errors.append(f"{device} slide {index + 1} {locale} headline is {actual!r}, expected {expected!r}")

    if errors:
        print("Play Store validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("Play Store listing and assets are valid for es/en.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
