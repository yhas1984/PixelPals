#!/usr/bin/env python3
"""Generate Moki animation boards with GPT Image 2."""

from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[2]
REFERENCE_PATH = ROOT / "tools/moki/raw/ref_sheet_v1.png"
OUTPUT_DIR = ROOT / "tools/moki/raw"
API_URL = "https://api.openai.com/v1/images/edits"

COMMON_PROMPT = """
Use the attached Moki reference sheet as the strict character identity reference.
Moki is the exact same cute mint and teal chameleon in every cell: rounded triangular
head, large amber turret eyes, three coral flank spots, four adhesive feet, cream
highlights, warm dark outline, and a clearly curled spiral tail. Preserve the same
proportions, palette, illustration technique, facial structure, and body scale.

Create a precise 2 by 2 animation contact sheet with four equal square cells. One
complete pose per cell, ordered left-to-right and top-to-bottom. Every cell uses the
same camera distance, character scale, light direction, and contact baseline. Use a
perfectly uniform pure white background. No labels, text, numbers, borders, separators, floor,
shadows, props, scenery, watermark, motion blur, duplicate limbs, or cropped anatomy.
Soft polished pastel sticker illustration with clean alpha edges. Keep enough clear
padding around each pose. Do not redesign the character.
""".strip()

BOARDS = (
    (
        "board_01_perch",
        """
Cell 1: neutral front-facing perch, all four adhesive feet planted, relaxed smile,
eyes forward, tail curled beside the body.
Cell 2: breathing in-between, chest subtly raised and body one small amount taller,
feet fixed to exactly the same baseline, relaxed eyes.
Cell 3: alert eye-scan pose, head still, left eye looking up-left and right eye looking
forward, tail curl slightly tighter, body unchanged.
Cell 4: opposite eye-scan pose, left eye forward and right eye looking up-right, tiny
pleased smile, tail curl relaxed, body unchanged.
""",
    ),
    (
        "board_02_crawl",
        """
All four cells are a side-profile crawl cycle facing right. Keep belly height, body
length, tail curl, scale, and foot contact baseline identical across the cycle.
Cell 1: front-right foot reaches forward while rear-left foot remains planted.
Cell 2: reaching front foot sticks down, body shifts forward, opposite front foot lifts.
Cell 3: rear feet advance under the body, tail counterbalances slightly backward.
Cell 4: body settles forward into the neutral passing pose, ready to loop to cell 1.
This is deliberate adhesive chameleon walking, not running, jumping, or sliding.
""",
    ),
    (
        "board_03_corner_camouflage",
        """
All poses use a right-facing three-quarter side view and identical body scale.
Cell 1: approaches a screen corner, front feet reaching around an invisible 90-degree
edge while rear feet remain planted on the original surface, tail counterbalancing.
Cell 2: midpoint of the corner turn, front half rotated upward about 45 degrees, front
feet gripping the new surface, rear half still following naturally, no dislocation.
Cell 3: corner turn completed, body aligned vertically upward, all feet gripping the
new invisible surface, tail settling behind it.
Cell 4: start of camouflage while calmly perched, mint body shifting partially toward
pale sage, coral spots slightly muted, amber eyes fully visible.
        """,
    ),
    (
        "board_03_corner_v2",
        """
All four cells form one continuous, physically natural corner transition. Show the
same right-facing side-profile Moki at exactly the same body scale and with the same
tail curl. The invisible surfaces form an outer 90-degree corner: the old surface is
horizontal below the feet and the new surface is vertical on the right. Feet must
remain anatomically attached and visibly support the body throughout the turn.
Cell 1: approach pose, body horizontal, rear feet firmly planted on the old surface,
front-right foot reaching around the corner while the tail counterbalances backward.
Cell 2: new bridge pose, both front feet now stuck to the vertical surface, shoulders
rotated about 25 degrees upward, belly bending naturally, both rear feet still planted
on the horizontal surface, tail low and extended for balance.
Cell 3: follow-through pose, torso rotated about 60 degrees upward, one rear foot
lifting around the corner while the other rear foot remains on the old surface, front
feet pulling gently, tail sweeping through the curve.
Cell 4: completed transition, body vertical and facing upward, all four feet attached
to the new vertical surface, tail settled below the body. No jumping, floating,
teleporting, duplicated feet, detached limbs, or abrupt change of body size.
""",
    ),
    (
        "board_04_camouflage_tongue",
        """
Use a front three-quarter pose with identical body scale and foot baseline.
Cell 1: full camouflage hold, body pale sage and low saturation but outline, amber eyes,
spiral tail, and anatomy remain readable.
Cell 2: tongue-strike aim pose, normal mint color restored, body lowered, head leaning
forward, both eyes converging on a target directly ahead, mouth just beginning to open.
Cell 3: tongue launches straight forward toward the right, long thin coral tongue fully
extended, body braced with adhesive feet, eyes tracking the tongue tip.
Cell 4: tongue contacts an imaginary tiny point at the far right, tongue still extended
with a small curled sticky tip, body and feet remain fixed, no object or prop visible.
""",
    ),
    (
        "board_05_reactions",
        """
Cell 1: tongue retract pose, front three-quarter view, tongue halfway back into the
mouth, satisfied eyes, feet fixed, normal mint color.
Cell 2: drag resistance pose, side view facing right, adhesive feet stretched backward
as if peeling from a surface, body leaning right, tail extended as counterbalance,
anatomy intact and no external hand.
Cell 3: fling airborne tuck, three-quarter view, feet tucked safely against the body,
spiral tail tightened around the body as a stabilizer, alert eyes, no motion blur.
Cell 4: landing and re-adhesion pose, side view facing right, front feet firmly planted,
rear feet just touching down, body gently compressed, tail uncurling to balance.
""",
    ),
)


def generate_board(api_key: str, board_name: str, board_prompt: str) -> Path:
    output_path = OUTPUT_DIR / f"{board_name}.png"
    with REFERENCE_PATH.open("rb") as reference_file:
        response = requests.post(
            API_URL,
            headers={"Authorization": f"Bearer {api_key}"},
            data={
                "model": "gpt-image-2",
                "prompt": f"{COMMON_PROMPT}\n\n{board_prompt.strip()}",
                "size": "1024x1024",
                "quality": "high",
                "output_format": "png",
                "n": "1",
            },
            files={"image[]": (REFERENCE_PATH.name, reference_file, "image/png")},
            timeout=300,
        )
    if not response.ok:
        raise RuntimeError(f"OpenAI API returned {response.status_code}: {response.text}")
    payload = response.json()
    output_path.write_bytes(base64.b64decode(payload["data"][0]["b64_json"]))
    print(f"generated {output_path.relative_to(ROOT)}")
    return output_path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--board", choices=[name for name, _ in BOARDS])
    args = parser.parse_args()
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        parser.error("OPENAI_API_KEY is not configured")
    if not REFERENCE_PATH.exists():
        parser.error(f"Missing reference sheet: {REFERENCE_PATH}")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    selected_boards = [item for item in BOARDS if args.board in (None, item[0])]
    for board_name, board_prompt in selected_boards:
        generate_board(api_key, board_name, board_prompt)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
