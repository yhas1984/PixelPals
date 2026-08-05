#!/usr/bin/env python3
"""Generate accessory sprite atlases (PNG with transparency) for PixelPals.

Each accessory is drawn as stylized pixel-art shapes in a 384x384 grid and
packed into an atlas PNG. Wing-like accessories (flapping gadgets) produce
multiple frames so the AccessorySpriteRenderer can animate a "flap" clip.

Usage:
    python3 tools/build_accessory_atlas.py
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app/src/main/assets/accessories"

FRAME = 384  # atlas frame size (px)
PALETTE = {
    "gold": (240, 200, 80, 255),
    "gold_dark": (190, 150, 40, 255),
    "white": (245, 245, 255, 255),
    "white_dim": (210, 215, 235, 255),
    "black": (30, 30, 40, 255),
    "red": (230, 60, 50, 255),
    "red_dark": (150, 30, 30, 255),
    "blue": (90, 150, 230, 255),
    "blue_light": (170, 210, 255, 255),
    "green": (80, 200, 120, 255),
    "green_light": (150, 240, 180, 255),
    "purple": (160, 100, 220, 255),
    "pink": (240, 140, 190, 255),
    "orange": (250, 150, 60, 255),
    "brown": (150, 100, 60, 255),
    "cyan": (100, 220, 240, 255),
    "metal": (170, 180, 200, 255),
    "metal_dark": (110, 120, 140, 255),
    "teal": (80, 190, 180, 255),
}


def color(name: str, alpha: int = 255):
    r, g, b, _ = PALETTE[name]
    return (r, g, b, alpha)


def new_canvas() -> Image.Image:
    return Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))


def save_atlas(frames: list[Image.Image], name: str) -> None:
    """Pack frames into a single atlas PNG with 1 row x N columns."""
    atlas = Image.new("RGBA", (FRAME * len(frames), FRAME), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        atlas.paste(frame, (i * FRAME, 0))
    target_dir = OUT_DIR / name
    target_dir.mkdir(parents=True, exist_ok=True)
    atlas.save(target_dir / f"{name}.png", optimize=True)
    print(f"  atlas {name}.png ({len(frames)} frames)")


def spec(name: str, columns: int, rows: int, clips: dict, anchor: dict, z_layer: str, scale: float) -> dict:
    return {
        "atlasPath": f"accessories/{name}/{name}.png",
        "frameWidth": FRAME,
        "frameHeight": FRAME,
        "columns": columns,
        "rows": rows,
        "clips": clips,
        "anchor": anchor,
        "zLayer": z_layer,
        "scale": scale,
    }


# --------------------------------------------------------------------------
# Wings (flapping) — draw a stylized feather wing rotated across 4 frames
# --------------------------------------------------------------------------

def draw_wing(canvas: ImageDraw, cx: float, cy: float, angle_deg: float, main: tuple, dark: tuple) -> None:
    """Draw a feather-like wing centered around (cx, cy), rotated by angle."""
    import math

    # Wing blade: three stacked feathers pointing up-right from the pivot.
    feathers = [
        (140, 26),  # top feather (len, width)
        (170, 34),
        (200, 44),
    ]
    for i, (length, width) in enumerate(feathers):
        # Each feather rotates slightly differently for a layered look.
        a = math.radians(angle_deg + 8 - i * 3)
        dx = math.cos(a) * length
        dy = math.sin(a) * length
        # Feather is a capsule from pivot toward (dx, dy).
        tip = (cx + dx, cy - dy)
        # Draw as an ellipse oriented along the direction.
        mid = ((cx + tip[0]) / 2, (cy + tip[1]) / 2)
        w = width * 0.55
        h = length * 0.42
        # Save/rotate canvas around mid for the ellipse.
        canvas.polygon(
            [
                (cx, cy - h * 0.2),
                (tip[0] - w * 0.4, tip[1] - h * 0.15),
                (tip[0], tip[1] - h * 0.0),
                (tip[0] - w * 0.4, tip[1] + h * 0.15),
                (cx, cy + h * 0.2),
            ],
            fill=main if i % 2 == 0 else dark,
        )
        # Feather spine line
        canvas.line([(cx, cy), tip], fill=dark, width=4)


def wings_frames(main: str, dark: str) -> list[Image.Image]:
    """4 frames of a wing flapping up-down."""
    frames = []
    for angle in (-35, -15, 8, 30):
        img = new_canvas()
        d = ImageDraw.Draw(img)
        # Mirror: wing pivot at the pet's shoulder, extending to the right.
        draw_wing(d, 210, 210, angle, color(main), color(dark))
        frames.append(img)
    return frames


# --------------------------------------------------------------------------
# Single-frame accessories
# --------------------------------------------------------------------------

def glasses_round() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Two round lenses + bridge (shifted left to fit canvas)
    d.ellipse([50, 130, 190, 270], outline=color("black"), width=14)
    d.ellipse([220, 130, 360, 270], outline=color("black"), width=14)
    d.line([190, 200, 220, 200], fill=color("black"), width=10)
    # Lens shine
    d.ellipse([80, 155, 130, 205], fill=color("blue_light", 90))
    d.ellipse([250, 155, 300, 205], fill=color("blue_light", 90))
    return img


def glasses_pilot() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Aviator: wide teardrop lenses (shifted left to fit canvas)
    d.ellipse([30, 150, 210, 300], outline=color("black"), width=14)
    d.ellipse([200, 150, 380, 300], outline=color("black"), width=14)
    d.arc([30, 120, 200, 220], 180, 360, fill=color("black"), width=14)
    d.arc([200, 120, 380, 220], 180, 360, fill=color("black"), width=14)
    d.ellipse([80, 180, 130, 230], fill=color("blue_light", 100))
    d.ellipse([250, 180, 300, 230], fill=color("blue_light", 100))
    return img


def hat_magic() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Pointy wizard hat
    d.polygon([(140, 180), (300, 180), (215, 30)], fill=color("purple"))
    d.polygon([(140, 180), (300, 180), (215, 30)], outline=color("purple", 255), width=0)
    # Brim
    d.ellipse([90, 175, 380, 235], fill=color("purple"))
    # Star
    d.polygon([(215, 80), (230, 115), (265, 115), (240, 140), (250, 175), (215, 150), (180, 175), (190, 140), (165, 115), (200, 115)], fill=color("gold"))
    return img


def crown_royal() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Crown body
    d.polygon([(110, 220), (340, 220), (340, 140), (300, 175), (270, 120), (225, 175), (180, 120), (150, 175), (110, 140)], fill=color("gold"))
    # Band
    d.rectangle([110, 195, 340, 235], fill=color("gold_dark"))
    # Jewels
    for x in (150, 225, 300):
        d.ellipse([x - 10, 200, x + 10, 225], fill=color("red"))
    return img


def tiara() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(130, 230), (320, 230), (320, 170), (290, 190), (270, 140), (225, 185), (180, 140), (160, 190), (130, 170)], fill=color("gold"))
    d.rectangle([130, 205, 320, 240], fill=color("gold_dark"))
    for x in (175, 225, 275):
        d.ellipse([x - 8, 210, x + 8, 232], fill=color("blue_light"))
    return img


def bowtie() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(192, 150), (120, 90), (120, 210), (192, 195)], fill=color("red"))
    d.polygon([(192, 150), (264, 90), (264, 210), (192, 195)], fill=color("red"))
    d.ellipse([182, 140, 202, 160], fill=color("red_dark"))
    return img


def scarf(warm: bool) -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    base = color("orange") if warm else color("pink")
    d.rectangle([150, 140, 270, 190], fill=base)
    d.rectangle([150, 140, 270, 165], fill=color("gold") if warm else color("green_light"))
    # Hanging tail
    d.polygon([(150, 165), (150, 260), (185, 230), (185, 165)], fill=base)
    return img


def horns_devil() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Two curved horns (fitted inside canvas)
    d.arc([40, 100, 200, 260], 180, 320, fill=color("red"), width=22)
    d.arc([184, 100, 344, 260], 220, 360, fill=color("red"), width=22)
    return img


def horn_unicorn() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(170, 250), (225, 60), (250, 250)], fill=color("pink"))
    d.line([(195, 240), (210, 110)], fill=color("white"), width=8)
    d.line([(225, 240), (240, 140)], fill=color("white"), width=8)
    return img


def helmet_viking() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.arc([120, 140, 350, 280], 180, 360, fill=color("metal"), width=30)
    d.rectangle([120, 195, 350, 240], fill=color("metal"))
    # Horns (fitted inside canvas)
    d.arc([0, 100, 160, 220], 100, 200, fill=color("brown"), width=20)
    d.arc([214, 100, 374, 220], -20, 80, fill=color("brown"), width=20)
    return img


def hat_pirate() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(90, 200), (360, 200), (225, 60)], fill=color("black"))
    d.ellipse([70, 190, 380, 240], fill=color("black"))
    # Skull
    d.ellipse([205, 130, 245, 170], fill=color("white"))
    d.rectangle([205, 160, 245, 180], fill=color("white"))
    return img


def hat_chef() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.rectangle([130, 170, 260, 250], fill=color("white"))
    for cx in (150, 185, 220, 255):
        d.ellipse([cx - 28, 120, cx + 28, 185], fill=color("white"))
    d.rectangle([110, 240, 280, 265], fill=color("white_dim"))
    return img


def helmet_astronaut() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.ellipse([110, 120, 340, 300], fill=color("white"))
    d.ellipse([110, 120, 340, 300], outline=color("metal_dark"), width=10)
    d.ellipse([145, 155, 235, 250], fill=color("blue_light", 120))
    d.rectangle([110, 260, 340, 280], fill=color("metal"))
    return img


def bolt_lightning() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.polygon(
        [(225, 40), (140, 190), (200, 190), (150, 340), (290, 170), (215, 170), (265, 40)],
        fill=color("gold"),
    )
    return img


def shield_back() -> Image.Image:
    img = new_canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(192, 40), (310, 90), (310, 220), (192, 340), (74, 220), (74, 90)], fill=color("metal"))
    d.polygon([(192, 80), (270, 115), (270, 200), (192, 300), (114, 200), (114, 115)], fill=color("metal_dark"))
    d.line([(192, 90), (192, 290)], fill=color("gold"), width=8)
    return img


def ufo_jetpack_frames() -> list[Image.Image]:
    """2 frames: UFO bobbing up/down over the pet."""
    frames = []
    for dy in (0, 20):
        img = new_canvas()
        d = ImageDraw.Draw(img)
        # Saucer
        d.ellipse([60, 150 + dy, 380, 230 + dy], fill=color("metal"))
        d.ellipse([110, 130 + dy, 330, 190 + dy], fill=color("cyan"))
        # Dome
        d.arc([150, 90 + dy, 290, 170 + dy], 180, 360, fill=color("blue_light"), width=16)
        # Lights
        for x in (120, 192, 264):
            d.ellipse([x, 190 + dy, x + 20, 215 + dy], fill=color("gold"))
        frames.append(img)
    return frames


def duck_jetpack_frames() -> list[Image.Image]:
    """4 frames: jetpack with flickering flame."""
    frames = []
    flame_lens = (40, 60, 30, 55)
    for i, fl in enumerate(flame_lens):
        img = new_canvas()
        d = ImageDraw.Draw(img)
        # Backpack body
        d.rounded_rectangle([150, 140, 290, 260], radius=20, fill=color("metal_dark"))
        # Nozzles
        d.rectangle([160, 250, 200, 290], fill=color("metal"))
        d.rectangle([240, 250, 280, 290], fill=color("metal"))
        # Flame flicker below nozzles
        for nx in (180, 260):
            d.polygon(
                [(nx - 14, 285), (nx + 14, 285), (nx + (2 if i % 2 else -2), 285 + fl)],
                fill=color("orange"),
            )
        # Center light
        d.ellipse([190, 170, 250, 220], fill=color("red") if i % 2 else color("orange"))
        frames.append(img)
    return frames


def celestial_wings_frames() -> list[Image.Image]:
    return wings_frames("white", "white_dim")


def demonic_wings_frames() -> list[Image.Image]:
    return wings_frames("red_dark", "black")


# --------------------------------------------------------------------------
# Frames para accesorios que antes usaban emojis de CARA (se veían como una
# cabeza encima del pet). Ahora dibujan el accesorio real.
# --------------------------------------------------------------------------

def alien_antennas() -> Image.Image:
    """Dos antenas verdes arqueadas con puntas brillantes (slot HEAD)."""
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Vástagos curvos
    d.arc([80, 100, 250, 240], 200, 320, fill=color("green"), width=16)
    d.arc([250, 100, 420, 240], 220, 340, fill=color("green"), width=16)
    # Puntas brillantes
    d.ellipse([118, 78, 150, 110], fill=color("green_light"))
    d.ellipse([288, 78, 320, 110], fill=color("green_light"))
    # Lucecitas
    d.ellipse([122, 82, 146, 106], fill=color("cyan", 220))
    d.ellipse([292, 82, 316, 106], fill=color("cyan", 220))
    return img


def ninja_mask() -> Image.Image:
    """Máscara ninja negra ancha con rendijas de ojos (slot FACE)."""
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Máscara: rectángulo ancho que cubre los ojos
    d.rounded_rectangle([60, 100, 424, 220], radius=18, fill=color("black"))
    # Brillo superior
    d.rounded_rectangle([70, 105, 414, 130], radius=10, fill=color("metal_dark"))
    # Rendijas de ojos (rojas)
    d.rounded_rectangle([120, 150, 200, 170], radius=6, fill=color("red"))
    d.rounded_rectangle([250, 150, 330, 170], radius=6, fill=color("red"))
    # Cola de la máscara (cinta)
    d.polygon([(60, 190), (30, 240), (70, 230), (50, 260), (90, 220)], fill=color("black"))
    return img


def monocle() -> Image.Image:
    """Monóculo: lente redonda con marco dorado y cadena (slot FACE)."""
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Lente
    d.ellipse([120, 100, 280, 260], outline=color("gold"), width=14)
    d.ellipse([140, 120, 260, 240], fill=color("blue_light", 120))
    # Reflejo
    d.ellipse([150, 130, 190, 170], fill=color("white", 140))
    # Cadena
    d.line([280, 180, 360, 280], fill=color("gold"), width=8)
    return img


def mustache() -> Image.Image:
    """Bigote pixel-art marrón con dos lóbulos (slot FACE)."""
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Lóbulo izquierdo
    d.polygon(
        [(140, 180), (250, 170), (220, 230), (160, 235), (140, 180)],
        fill=color("brown"),
    )
    # Lóbulo derecho (espejo)
    d.polygon(
        [(260, 170), (370, 180), (350, 235), (290, 230), (260, 170)],
        fill=color("brown"),
    )
    # Centro
    d.ellipse([235, 170, 275, 205], fill=color("brown"))
    # Reflejo
    d.line([160, 185, 230, 180], fill=color("brown", 200), width=6)
    d.line([280, 180, 350, 185], fill=color("brown", 200), width=6)
    return img


def eye_patch() -> Image.Image:
    """Parche pirata: disco negro con cuerda (slot FACE)."""
    img = new_canvas()
    d = ImageDraw.Draw(img)
    # Disco
    d.ellipse([140, 110, 300, 270], fill=color("black"))
    d.ellipse([150, 120, 290, 260], fill=color("metal_dark"))
    # Cuerda (horizontal, cruzando la cara)
    d.line([20, 190, 440, 190], fill=color("black"), width=10)
    return img


# --------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------

SINGLE_FRAME = {
    "round_glasses": (glasses_round, {"xRatio": 0.0, "yRatio": -0.05}, "FRONT", 0.5),
    "pilot_glasses": (glasses_pilot, {"xRatio": 0.0, "yRatio": -0.08}, "FRONT", 0.52),
    "magic_hat": (hat_magic, {"xRatio": 0.0, "yRatio": -0.42}, "FRONT", 0.62),
    "royal_crown": (crown_royal, {"xRatio": 0.0, "yRatio": -0.4}, "FRONT", 0.55),
    "tiara": (tiara, {"xRatio": 0.0, "yRatio": -0.4}, "FRONT", 0.55),
    "bowtie": (bowtie, {"xRatio": 0.0, "yRatio": 0.08}, "FRONT", 0.45),
    "cozy_scarf": (lambda: scarf(True), {"xRatio": 0.0, "yRatio": -0.02}, "FRONT", 0.6),
    "rainbow_scarf": (lambda: scarf(False), {"xRatio": 0.0, "yRatio": -0.02}, "FRONT", 0.6),
    "devil_horns": (horns_devil, {"xRatio": 0.0, "yRatio": -0.42}, "FRONT", 0.55),
    "unicorn_horn": (horn_unicorn, {"xRatio": 0.0, "yRatio": -0.42}, "FRONT", 0.45),
    "viking_helmet": (helmet_viking, {"xRatio": 0.0, "yRatio": -0.38}, "FRONT", 0.55),
    "pirate_hat": (hat_pirate, {"xRatio": 0.0, "yRatio": -0.4}, "FRONT", 0.58),
    "chef_hat": (hat_chef, {"xRatio": 0.0, "yRatio": -0.4}, "FRONT", 0.55),
    "astronaut_helmet": (helmet_astronaut, {"xRatio": 0.0, "yRatio": -0.2}, "FRONT", 0.55),
    "lightning_bolt": (bolt_lightning, {"xRatio": 0.3, "yRatio": -0.05}, "FRONT", 0.45),
    "shield_back": (shield_back, {"xRatio": 0.0, "yRatio": 0.05}, "BEHIND", 0.5),
    # Accesorios que antes usaban emojis de cara — ahora con sprite real
    "alien_antennas": (alien_antennas, {"xRatio": 0.0, "yRatio": -0.30}, "FRONT", 0.55),
    "ninja_mask": (ninja_mask, {"xRatio": 0.0, "yRatio": -0.08}, "FRONT", 0.55),
    "monocle": (monocle, {"xRatio": 0.05, "yRatio": -0.05}, "FRONT", 0.50),
    "mustache": (mustache, {"xRatio": 0.0, "yRatio": 0.05}, "FRONT", 0.50),
    "eye_patch": (eye_patch, {"xRatio": 0.0, "yRatio": -0.06}, "FRONT", 0.48),
}


def build() -> None:
    print("Generating accessory atlases…")
    single_specs: dict[str, dict] = {}
    multi_specs: dict[str, dict] = {}

    for name, (draw_fn, anchor, z_layer, scale) in SINGLE_FRAME.items():
        save_atlas([draw_fn()], name)
        single_specs[name] = spec(name, 1, 1, {
            "idle": {"frames": [0], "frameDurationMs": 150, "loop": True},
        }, anchor, z_layer, scale)

    for name, frames, anchor, scale in [
        ("celestial_wings", celestial_wings_frames(), {"xRatio": 0.0, "yRatio": -0.1}, 0.7),
        ("demonic_wings", demonic_wings_frames(), {"xRatio": 0.0, "yRatio": -0.1}, 0.7),
        ("ufo_jetpack", ufo_jetpack_frames(), {"xRatio": 0.0, "yRatio": -0.55}, 0.55),
        ("duck_jetpack", duck_jetpack_frames(), {"xRatio": 0.0, "yRatio": -0.45}, 0.55),
    ]:
        save_atlas(frames, name)
        multi_specs[name] = spec(name, len(frames), 1, {
            "idle": {"frames": [0], "frameDurationMs": 150, "loop": True},
            "flap": {"frames": list(range(len(frames))), "frameDurationMs": 140, "loop": True},
        }, anchor, "BEHIND", scale)

    # Persist a machine-readable map for the catalog update step.
    catalog_path = ROOT / "tools" / "accessory_sprite_specs.json"
    catalog_path.write_text(
        json.dumps({**single_specs, **multi_specs}, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    print(f"Wrote specs to {catalog_path}")


if __name__ == "__main__":
    build()
