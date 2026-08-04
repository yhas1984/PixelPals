#!/usr/bin/env python3
"""Generate the redesigned Querubin reference and animation boards with GPT Image 2."""

from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "tools/angel/raw"
REFERENCE_PATH = RAW_DIR / "querubin_reference_v4.png"
GENERATIONS_URL = "https://api.openai.com/v1/images/generations"
EDITS_URL = "https://api.openai.com/v1/images/edits"

IDENTITY = """
Querubin is the exact same gentle child angel in every pose: round kind face, warm blush
cheeks, large calm hazel eyes, short honey-gold curls, two large pearl-white feathered
wings, a thin floating soft-gold halo, a simple ivory knee-length tunic with one narrow
gold sash, and tiny bare feet. The silhouette is light, soft, youthful, and unmistakably
angelic. Preserve the exact face, curls, wing shape, feather pattern, halo, tunic, sash,
body proportions, palette, and scale in every cell.

Use a premium soft pastel storybook mascot illustration with smooth warm outlines,
delicate feather texture, pearl-white highlights, and a subtle celestial glow. Not pixel
art, not photorealistic, not anime, not a glossy 3D render, and not ornate baroque art.
Keep the body upright and readable at small Android overlay size.
""".strip()

BOARD_RULES = """
Use the attached reference as the strict identity sheet. Create a precise 2 by 2
animation contact sheet with four equal square cells ordered left-to-right, top-to-bottom.
Same camera distance, body scale, center point, light direction, and upright orientation
in all cells. Use a uniform pure white background. No text, labels, numbers, borders,
separators, scenery, clouds, floor, cast shadow, props, watermark, motion blur, duplicate
limbs, cropped anatomy, upside-down pose, flip, somersault, or dramatic body rotation.
Leave generous white padding around each complete angel.
""".strip()

BOARDS = (
    (
        "board_01_hover",
        """
All four poses are a continuous front three-quarter hover cycle with the torso upright.
Cell 1: neutral hover, wings half open, hands relaxed, serene smile.
Cell 2: wing upstroke, wings raised softly, body rises only slightly.
Cell 3: wing downstroke, wings press downward with natural feather overlap, body remains upright.
Cell 4: wings open wide in a stable buoyant hover, gentle welcoming expression.
""",
    ),
    (
        "board_02_flight",
        """
All poses travel toward the RIGHT while the torso remains nearly upright with at most a
small forward lean. Cell 1: powered flight, wings beginning downstroke, hands near chest.
Cell 2: powered flight passing beat, wings sweeping back, feet trailing gently.
Cell 3: level glide, wings extended wide and steady, calm face, no body rotation.
Cell 4: gentle rightward reach, one hand leading while wings stabilize the glide.
""",
    ),
    (
        "board_03_grace",
        """
All poses remain suspended upright. Cell 1: halo brightens with a soft pearl-gold aura,
hands lifting toward the heart. Cell 2: blessing gesture, one open palm forward and a
small glow from the palm and chest, no object. Cell 3: hands folded in peaceful prayer,
wings resting half open. Cell 4: restful suspended prayer, eyes softly closed, wings
relaxed but still supporting the hover.
""",
    ),
    (
        "board_04_reactions",
        """
Cell 1: affectionate touch response toward the viewer, one hand reaching forward, bright
kind smile, wings open. Cell 2: gentle drag resistance, body stretched slightly backward
while wings brace, still upright and unharmed. Cell 3: fling tuck, body compact with knees
slightly tucked and wings partly folded for safety, head still upright, no spin. Cell 4:
recovery hover, wings spread to brake, feet below, body returning to a stable upright pose.
""",
    ),
)


def decode(response: requests.Response, output_path: Path) -> None:
    if not response.ok:
        raise RuntimeError(f"OpenAI API returned {response.status_code}: {response.text}")
    output_path.write_bytes(base64.b64decode(response.json()["data"][0]["b64_json"]))
    print(f"generated {output_path.relative_to(ROOT)}")


def generate_reference(api_key: str) -> None:
    prompt = f"""
{IDENTITY}

Create a clean 2 by 2 character identity sheet on a perfectly uniform pure white
background. Cell 1: frontal neutral hover. Cell 2: right-facing three-quarter hover.
Cell 3: back three-quarter hover showing both complete wings and halo. Cell 4: friendly
right-facing flight-ready pose. Same character and scale in all cells. No text, grid
lines, floor, cast shadow, props, scenery, cropped wings, or cropped halo.
""".strip()
    response = requests.post(
        GENERATIONS_URL,
        headers={"Authorization": f"Bearer {api_key}"},
        json={
            "model": "gpt-image-2",
            "prompt": prompt,
            "size": "1024x1024",
            "quality": "high",
            "output_format": "png",
            "n": 1,
        },
        timeout=300,
    )
    decode(response, REFERENCE_PATH)


def generate_board(api_key: str, name: str, prompt: str) -> None:
    output_path = RAW_DIR / f"{name}.png"
    with REFERENCE_PATH.open("rb") as reference:
        response = requests.post(
            EDITS_URL,
            headers={"Authorization": f"Bearer {api_key}"},
            data={
                "model": "gpt-image-2",
                "prompt": f"{IDENTITY}\n\n{BOARD_RULES}\n\n{prompt.strip()}",
                "size": "1024x1024",
                "quality": "high",
                "output_format": "png",
                "n": "1",
            },
            files={"image[]": (REFERENCE_PATH.name, reference, "image/png")},
            timeout=300,
        )
    decode(response, output_path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-only", action="store_true")
    parser.add_argument("--board", choices=[name for name, _ in BOARDS])
    args = parser.parse_args()
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        parser.error("OPENAI_API_KEY is not configured")
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    if not REFERENCE_PATH.exists():
        generate_reference(api_key)
    if args.reference_only:
        return 0
    for name, prompt in BOARDS:
        if args.board in (None, name):
            generate_board(api_key, name, prompt)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
