#!/usr/bin/env python3
"""Generate the redesigned Ginger reference and animation boards with GPT Image 2."""

from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "tools/ginger/raw"
REFERENCE_PATH = RAW_DIR / "ginger_reference_v2.png"
GENERATIONS_URL = "https://api.openai.com/v1/images/generations"
EDITS_URL = "https://api.openai.com/v1/images/edits"

IDENTITY = """
Ginger is a completely redesigned young female orange mackerel tabby cat. She has a
supple athletic feline body, a rounded but not chibi head, large sea-green almond eyes,
an apricot-orange coat with six consistent dark cinnamon stripes, a cream muzzle,
cream chest, four cream sock paws, and a cream tail tip. She wears a narrow teal collar
with one tiny round gold tag. No bow and no other clothes. Her proportions, markings,
collar, eye color, and tail length must remain identical in every pose.

Use a soft polished pastel storybook illustration, smooth warm outlines, subtle fur,
gentle studio lighting, and clean readable anatomy. This is not pixel art, not anime,
not a photo, and not a 3D render. Keep the design legible at small Android overlay size.
""".strip()

BOARD_RULES = """
Use the attached reference as a strict identity sheet. Create a precise 2 by 2 animation
contact sheet with four equal square cells ordered left-to-right, top-to-bottom. Use the
same camera distance, character scale, light direction, and ground-contact baseline in
all four cells. Canonical locomotion direction is LEFT. Use a uniform pure white
background. Do not draw text, labels, numbers, borders, separators, scenery, floor,
cast shadows, props, watermark, motion blur, duplicate limbs, or cropped anatomy.
Leave generous clear padding around every pose.
""".strip()

BOARDS = (
    (
        "board_01_idle",
        """
Cell 1: calm seated neutral, alert ears, tail curled around the front cream paws.
Cell 2: seated grooming, naturally licking one raised front paw, balanced on three paws.
Cell 3: sleeping in a compact curled ball, nose near tail, eyes closed, peaceful breathing.
Cell 4: waking feline stretch, front paws extended left, chest low, hindquarters raised,
back and tail forming one smooth natural curve.
""",
    ),
    (
        "board_02_walk",
        """
All four cells are one seamless LEFT-facing feline walk cycle at a relaxed trot. Keep
head height, torso length, baseline, tail curve, and body scale consistent.
Cell 1: left front paw at forward contact, opposite hind paw pushing back.
Cell 2: first passing pose, weight over planted paws, free limbs crossing naturally.
Cell 3: opposite front paw at forward contact, shoulders and hips counter-rotated.
Cell 4: second passing pose returning naturally to cell 1. Show four distinct gait beats,
not four unrelated poses. No running leap and no sliding feet.
""",
    ),
    (
        "board_03_stalk",
        """
All poses face LEFT and progress continuously into a pounce.
Cell 1: low stalking crouch, belly close to the baseline, focused eyes, shoulders forward.
Cell 2: silent stalking step, one front paw slowly lifted, hips level, tail counterbalancing.
Cell 3: stalking freeze, body compressed lower, pupils focused, hind legs gathering.
Cell 4: launch coil, hind legs fully loaded beneath the body, forequarters slightly raised,
ready to spring left. Preserve contact with the baseline until the final launch.
""",
    ),
    (
        "board_04_action",
        """
All poses face LEFT and form one physically continuous pounce and recovery.
Cell 1: airborne pounce, torso long and elegant, front paws reaching left, hind legs trailing.
Cell 2: landing impact, front cream paws contact the baseline, elbows and spine compress,
hind paws about to settle; no painful pose.
Cell 3: landing recovery, all four paws grounded, balance returning, tail raised for control.
Cell 4: touch and drag reaction, body gently scrunched with ears slightly back, one paw
braced and a cute surprised expression. It must read as resistance without fear or injury.
""",
    ),
)


def decode_image(response: requests.Response, output_path: Path) -> None:
    if not response.ok:
        raise RuntimeError(f"OpenAI API returned {response.status_code}: {response.text}")
    output_path.write_bytes(base64.b64decode(response.json()["data"][0]["b64_json"]))
    print(f"generated {output_path.relative_to(ROOT)}")


def generate_reference(api_key: str) -> None:
    prompt = f"""
{IDENTITY}

Create a clean 2 by 2 character identity sheet on a perfectly uniform pure white
background. Cell 1: front seated view. Cell 2: left-facing standing side profile.
Cell 3: rear three-quarter view showing the exact stripe pattern and cream tail tip.
Cell 4: expressive left-facing head and body three-quarter view. Same character and scale
in all cells. No text, grid lines, floor, cast shadow, props, scenery, or cropped anatomy.
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
    decode_image(response, REFERENCE_PATH)


def generate_board(api_key: str, board_name: str, board_prompt: str) -> None:
    output_path = RAW_DIR / f"{board_name}.png"
    with REFERENCE_PATH.open("rb") as reference_file:
        response = requests.post(
            EDITS_URL,
            headers={"Authorization": f"Bearer {api_key}"},
            data={
                "model": "gpt-image-2",
                "prompt": f"{IDENTITY}\n\n{BOARD_RULES}\n\n{board_prompt.strip()}",
                "size": "1024x1024",
                "quality": "high",
                "output_format": "png",
                "n": "1",
            },
            files={"image[]": (REFERENCE_PATH.name, reference_file, "image/png")},
            timeout=300,
        )
    decode_image(response, output_path)


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
    selected = [item for item in BOARDS if args.board in (None, item[0])]
    for board_name, board_prompt in selected:
        generate_board(api_key, board_name, board_prompt)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
