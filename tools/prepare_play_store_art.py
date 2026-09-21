#!/usr/bin/env python3
"""Prepare the approved store artwork and Android launcher icon variants."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "play-store"
SOURCE = STORE / "source"
CORAL = (239, 102, 88, 255)
CREAM = (255, 247, 229, 255)
INK = (76, 43, 56, 255)


def cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    scale = max(size[0] / image.width, size[1] / image.height)
    resized = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
    left = (resized.width - size[0]) // 2
    top = (resized.height - size[1]) // 2
    return resized.crop((left, top, left + size[0], top + size[1]))


def fit_font(draw: ImageDraw.ImageDraw, text: str, max_width: int, start: int, path: str) -> ImageFont.FreeTypeFont:
    for size in range(start, 15, -1):
        font = ImageFont.truetype(path, size)
        if draw.textbbox((0, 0), text, font=font)[2] <= max_width:
            return font
    return ImageFont.truetype(path, 16)


def prepare_icon(source: Image.Image) -> None:
    source = cover(source.convert("RGBA"), (512, 512))
    solid = Image.new("RGBA", source.size, CORAL)
    solid.alpha_composite(source)
    store_icon = solid.convert("RGB")
    icon_dir = STORE / "assets" / "icon"
    icon_dir.mkdir(parents=True, exist_ok=True)
    store_icon.save(icon_dir / "pixelpals-icon-512.png", quality=95)
    store_icon.save(ROOT / "screenshots-editor" / "public" / "app-icon.png", quality=95)

    adaptive = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    foreground = source.resize((356, 356), Image.Resampling.LANCZOS)
    adaptive.alpha_composite(foreground, ((432 - 356) // 2, (432 - 356) // 2))
    adaptive.save(ROOT / "app" / "src" / "main" / "res" / "drawable" / "ic_launcher_foreground.png")

    density_sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, pixels in density_sizes.items():
        directory = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        resized = store_icon.resize((pixels, pixels), Image.Resampling.LANCZOS)
        resized.save(directory / "ic_launcher.png")
        resized.save(directory / "ic_launcher_round.png")


def draw_feature(source: Image.Image, locale: str) -> None:
    canvas = cover(source.convert("RGB"), (1024, 500)).convert("RGBA")
    shade = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shade_pixels = shade.load()
    for x in range(590):
        alpha = round(150 * max(0.0, 1.0 - x / 590) ** 1.35)
        for y in range(500):
            shade_pixels[x, y] = (50, 24, 49, alpha)
    shade = shade.filter(ImageFilter.GaussianBlur(4))
    canvas.alpha_composite(shade)
    draw = ImageDraw.Draw(canvas)
    bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    regular = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    logo_font = ImageFont.truetype(bold, 66)
    headline = {
        "es": ("UN COMPAÑERO", "QUE VIVE CONTIGO"),
        "en": ("A COMPANION", "THAT LIVES WITH YOU"),
    }[locale]
    headline_font = fit_font(draw, max(headline, key=len), 33, 450, bold)
    support_font = ImageFont.truetype(regular, 21)

    draw.rounded_rectangle((52, 47, 137, 56), radius=5, fill=CORAL)
    draw.text((48, 78), "PixelPals", font=logo_font, fill=CREAM, stroke_width=2, stroke_fill=INK)
    y = 177
    for line in headline:
        draw.text((52, y), line, font=headline_font, fill=CREAM, stroke_width=1, stroke_fill=INK)
        y += headline_font.size + 10
    support = "15 mascotas · hogar · aventuras" if locale == "es" else "15 pets · home · adventures"
    draw.rounded_rectangle((48, 347, 463, 397), radius=25, fill=(255, 247, 229, 225))
    draw.text((72, 359), support, font=support_font, fill=INK)

    target = STORE / "assets" / "feature-graphic" / locale / "pixelpals-feature-graphic.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(target, quality=95)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--icon-source", type=Path)
    parser.add_argument("--feature-source", type=Path)
    args = parser.parse_args()
    SOURCE.mkdir(parents=True, exist_ok=True)
    icon_source = SOURCE / "aso-icon-source.png"
    feature_source = SOURCE / "aso-feature-base-source.png"
    if args.icon_source:
        shutil.copy2(args.icon_source, icon_source)
    if args.feature_source:
        shutil.copy2(args.feature_source, feature_source)
    if not icon_source.is_file() or not feature_source.is_file():
        parser.error("Provide both generated source images on the first run")
    prepare_icon(Image.open(icon_source))
    feature = Image.open(feature_source)
    draw_feature(feature, "es")
    draw_feature(feature, "en")


if __name__ == "__main__":
    main()
