#!/usr/bin/env python3
"""Genera iconos 512x512 de los packs de monedas para Play Console (in-app products)."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).parent / "store_icons"
SIZE = 512

PACKS = [
    ("coins_small", "100"),
    ("coins_medium", "350"),
    ("coins_large", "1000"),
    ("coins_mega", "2500"),
]

GOLD_TOP = (255, 214, 90)
GOLD_BOTTOM = (196, 130, 20)
RIM = (122, 70, 8)
TEXT = (255, 255, 255)


def make_icon(amount: str) -> Image.Image:
    scale = 4
    s = SIZE * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx = cy = s // 2
    radius = s // 2 - 24 * scale
    draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=RIM + (255,))
    inner = radius - 22 * scale
    draw.ellipse([cx - inner, cy - inner, cx + inner, cy + inner], fill=GOLD_TOP + (255,))

    # Degradado vertical dorado sobre la moneda.
    grad = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    for y in range(s):
        t = y / s
        r = int(GOLD_TOP[0] + (GOLD_BOTTOM[0] - GOLD_TOP[0]) * t)
        g = int(GOLD_TOP[1] + (GOLD_BOTTOM[1] - GOLD_TOP[1]) * t)
        b = int(GOLD_TOP[2] + (GOLD_BOTTOM[2] - GOLD_TOP[2]) * t)
        gd.line([(0, y), (s, y)], fill=(r, g, b, 255))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse([cx - inner, cy - inner, cx + inner, cy + inner], fill=255)
    img.paste(grad, (0, 0), mask)

    # Brillos superiores.
    hi = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hi)
    hd.ellipse(
        [cx - int(inner * 0.7), cy - int(inner * 0.75), cx + int(inner * 0.7), cy - int(inner * 0.2)],
        fill=(255, 255, 255, 46),
    )
    img.alpha_composite(hi)
    img = img.filter(ImageFilter.GaussianBlur(scale))

    # Texto del importe.
    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 92)
        small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 30)
    except OSError:
        font = ImageFont.load_default()
        small = ImageFont.load_default()

    text = f"{amount}" if len(amount) <= 3 else f"{int(amount):,}"
    bbox = draw.textbbox((0, 0), text, font=font)
    w = bbox[2] - bbox[0]
    draw.text(
        ((SIZE - w) / 2 - bbox[0], 190 - bbox[1]),
        text,
        font=font,
        fill=TEXT + (255,),
        stroke_width=6,
        stroke_fill=(90, 50, 0, 255),
    )
    label = "COINS"
    lbox = draw.textbbox((0, 0), label, font=small)
    lw = lbox[2] - lbox[0]
    draw.text(
        ((SIZE - lw) / 2 - lbox[0], 330 - lbox[1]),
        label,
        font=small,
        fill=TEXT + (255,),
        stroke_width=3,
        stroke_fill=(90, 50, 0, 255),
    )
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for sku, amount in PACKS:
        path = OUT / f"{sku}.png"
        make_icon(amount).save(path)
        print(f"{path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
