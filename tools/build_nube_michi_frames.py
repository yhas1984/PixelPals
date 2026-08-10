#!/usr/bin/env python3
"""Generate 3 new Nube-Michi cloud frames (gato_8..gato_10) matching the
original pixel-art aesthetic (soft cloud-white + blue-grey outline).

Frames:
  gato_8  puff: cat nestled in a horizontal elongated cloud (free float)
  gato_9  comet: cat in a cloud with a trail of puffs below (rising)
  gato_10 wind: cat in a cloud with swirls blown sideways (gust)

Palette (sampled from gato_0/gato_2):
  cloud bright : #f0fafc
  cloud soft   : #e4eef0
  outline      : #bddae5
  outline deep : #aed3e5
  eye line     : #2e516c
  ear pink     : #e5c5ca

Usage:
    python3 tools/build_nube_michi_frames.py [--preview]
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app/src/main/res/drawable-nodpi"

SIZE = 768
# Coordenadas de diseño en unidades 0..12 (centro del canvas ≈ 6).
# px() mapea esa escala al canvas de 768px.

CLOUD_BRIGHT = (240, 250, 252, 255)
CLOUD_SOFT = (228, 238, 240, 255)
OUTLINE = (189, 218, 229, 255)
OUTLINE_DEEP = (174, 211, 229, 255)
EYE = (46, 81, 108, 255)
PINK = (229, 197, 202, 255)


def px(x: float) -> int:
    return int(x / 12.0 * SIZE)


def new_canvas() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def draw_circle(d: ImageDraw.ImageDraw, cx: float, cy: float, r: float, fill, outline=None, width=0):
    x0, y0 = px(cx - r), px(cy - r)
    x1, y1 = px(cx + r), px(cy + r)
    d.ellipse([x0, y0, x1, y1], fill=fill, outline=outline, width=width)


def draw_eye_closed(d: ImageDraw.ImageDraw, cx: float, cy: float, len_g: float = 0.6):
    """A sleepy closed eye: a short curved line."""
    x0, y0 = px(cx - len_g / 2), px(cy)
    x1, y1 = px(cx + len_g / 2), px(cy)
    d.line([x0, y0, x1, y1], fill=EYE, width=px(0.12))


def draw_cat_face(d: ImageDraw.ImageDraw, cx: float, cy: float, scale: float = 1.0, wake: bool = False):
    """The cloud-cat head: rounded head + ears + closed eyes, all cloud-toned."""
    r_head = 1.55 * scale
    # Ears (two rounded triangles)
    for side in (-1, 1):
        ex = cx + side * 0.95 * scale
        ear_top_y = cy - 1.35 * scale
        ear_base_y = cy - 0.45 * scale
        d.polygon(
            [
                (px(ex - 0.55 * scale), px(ear_base_y)),
                (px(ex + 0.55 * scale), px(ear_base_y)),
                (px(ex + side * 0.15 * scale), px(ear_top_y)),
            ],
            fill=CLOUD_BRIGHT,
            outline=OUTLINE,
            width=px(0.14),
        )
        # inner ear pink
        d.polygon(
            [
                (px(ex - 0.28 * scale), px(ear_base_y - 0.08 * scale)),
                (px(ex + 0.28 * scale), px(ear_base_y - 0.08 * scale)),
                (px(ex + side * 0.10 * scale), px(ear_top_y + 0.25 * scale)),
            ],
            fill=PINK,
        )
    # Head
    draw_circle(d, cx, cy, r_head, CLOUD_BRIGHT, OUTLINE, px(0.16))
    # Cheeks softness (cloud puffs on the sides)
    draw_circle(d, cx - 1.35 * scale, cy + 0.45 * scale, 0.55 * scale, CLOUD_SOFT)
    draw_circle(d, cx + 1.35 * scale, cy + 0.45 * scale, 0.55 * scale, CLOUD_SOFT)
    # Eyes (closed/sleepy)
    draw_eye_closed(d, cx - 0.62 * scale, cy - 0.05 * scale)
    draw_eye_closed(d, cx + 0.62 * scale, cy - 0.05 * scale)
    # Tiny nose
    d.rectangle(
        [px(cx - 0.10 * scale), px(cy + 0.42 * scale), px(cx + 0.10 * scale), px(cy + 0.55 * scale)],
        fill=EYE,
    )
    # Mouth
    d.arc([px(cx - 0.30 * scale), px(cy + 0.42 * scale), px(cx + 0.30 * scale), px(cy + 0.85 * scale)],
          0, 180, fill=EYE, width=px(0.08))


def draw_cat_body(d: ImageDraw.ImageDraw, cx: float, cy: float, scale: float = 1.0):
    """Cloud-cat body: a stack of puffy circles, cloud-toned with outline."""
    for i, (dy, r) in enumerate([(0.0, 1.35), (0.85, 1.55), (1.75, 1.35)]):
        draw_circle(d, cx, cy + dy * scale, r * scale,
                    CLOUD_BRIGHT if i % 2 == 0 else CLOUD_SOFT,
                    OUTLINE, px(0.14))
    # paws hint
    draw_circle(d, cx - 0.8 * scale, cy + 2.55 * scale, 0.35 * scale, CLOUD_BRIGHT, OUTLINE, px(0.10))
    draw_circle(d, cx + 0.8 * scale, cy + 2.55 * scale, 0.35 * scale, CLOUD_BRIGHT, OUTLINE, px(0.10))


def draw_puff_swirl(d: ImageDraw.ImageDraw, cx: float, cy: float, r: float, n: int = 4, spread: float = 1.0):
    """Cloud swirls around a center (like gato_2's rizos)."""
    for i in range(n):
        ang = i * (360 / n)
        import math
        ox = math.cos(math.radians(ang)) * r * 0.65 * spread
        oy = math.sin(math.radians(ang)) * r * 0.55 * spread
        draw_circle(d, cx + ox, cy + oy, r * 0.55, CLOUD_BRIGHT, OUTLINE, px(0.10))


def save(img: Image.Image, name: str) -> None:
    img.save(OUT_DIR / f"{name}.png", optimize=True)
    print(f"  wrote {name}.png")


def build_puff() -> Image.Image:
    """gato_8: cat nestled in a wide horizontal cloud."""
    img = new_canvas()
    d = ImageDraw.Draw(img)
    cx, cy = 3.2, 5.4  # in grid units (0..12)
    # Horizontal elongated cloud behind the cat
    draw_circle(d, cx - 2.6, cy + 0.9, 1.5, CLOUD_BRIGHT, OUTLINE, px(0.14))
    draw_circle(d, cx, cy + 1.1, 1.8, CLOUD_BRIGHT, OUTLINE, px(0.14))
    draw_circle(d, cx + 2.6, cy + 0.9, 1.5, CLOUD_BRIGHT, OUTLINE, px(0.14))
    draw_circle(d, cx - 1.3, cy + 2.0, 1.2, CLOUD_SOFT)
    draw_circle(d, cx + 1.3, cy + 2.0, 1.2, CLOUD_SOFT)
    # Cat head on top
    draw_cat_face(d, cx, cy - 0.6, scale=1.15)
    # Small paws resting on the cloud
    draw_circle(d, cx - 1.0, cy + 0.9, 0.4, CLOUD_BRIGHT, OUTLINE, px(0.10))
    draw_circle(d, cx + 1.0, cy + 0.9, 0.4, CLOUD_BRIGHT, OUTLINE, px(0.10))
    return img


def build_comet() -> Image.Image:
    """gato_9: cat rising inside a cloud with a puff trail below."""
    img = new_canvas()
    d = ImageDraw.Draw(img)
    cx, cy = 3.2, 4.2
    # Swirl halo around the cat (rizos like gato_2)
    draw_puff_swirl(d, cx, cy, r=2.4, n=5, spread=1.05)
    # Cat body + head (stretched upward pose = head higher)
    draw_cat_body(d, cx, cy + 0.7, scale=1.0)
    draw_cat_face(d, cx, cy - 0.4, scale=1.1)
    # Rising trail of small puffs below
    for i, dy in enumerate([1.6, 2.6, 3.5]):
        r = max(1.1 - i * 0.25, 0.45)
        draw_circle(d, cx + (0.15 * i), cy + dy, r, CLOUD_SOFT, OUTLINE, px(0.10))
    return img


def build_wind() -> Image.Image:
    """gato_10: cat in a cloud with swirls blown to the right (gust)."""
    img = new_canvas()
    d = ImageDraw.Draw(img)
    cx, cy = 3.0, 5.0
    # Cat body tilted slightly
    draw_cat_body(d, cx, cy + 0.6, scale=0.95)
    draw_cat_face(d, cx - 0.1, cy - 0.7, scale=1.05)
    # Wind swirls blowing right
    for i, dx in enumerate([1.7, 2.6, 3.4]):
        draw_circle(d, cx + dx, cy + 0.2 + (0.35 if i % 2 else -0.35), 0.85 - i * 0.15, CLOUD_BRIGHT, OUTLINE, px(0.10))
    draw_circle(d, cx + 4.1, cy + 0.4, 0.45, CLOUD_SOFT, OUTLINE, px(0.08))
    # A few floating droplets left behind
    draw_circle(d, cx - 2.3, cy + 1.2, 0.3, CLOUD_SOFT)
    draw_circle(d, cx - 1.9, cy + 1.9, 0.22, CLOUD_SOFT)
    return img


def main() -> None:
    import sys
    print("Generating Nube-Michi cloud frames…")
    frames = {
        "gato_8": build_puff(),
        "gato_9": build_comet(),
        "gato_10": build_wind(),
    }
    if "--preview" in sys.argv:
        # Compose a 3x1 contact sheet for review
        sheet = Image.new("RGBA", (SIZE * 3, SIZE), (50, 60, 80, 255))
        for i, (name, img) in enumerate(frames.items()):
            sheet.paste(img, (i * SIZE, 0), img)
        preview = ROOT / "tools" / "nube_michi_preview.png"
        sheet.save(preview, optimize=True)
        print(f"  preview -> {preview}")
    for name, img in frames.items():
        save(img, name)


if __name__ == "__main__":
    main()
