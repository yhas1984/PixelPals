#!/usr/bin/env python3
"""Generate a physically continuous Corgi walk cycle with GPT Image 2."""

from __future__ import annotations

import base64
import os
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[2]
REFERENCE_PATH = ROOT / "app/src/main/res/drawable-nodpi/corgi_1.png"
OUTPUT_PATH = ROOT / "tools/corgi/raw/corgi_walk_board.png"
API_URL = "https://api.openai.com/v1/images/edits"

PROMPT = """
Use the attached image as the strict identity reference for the exact same cheerful
orange-and-cream corgi: identical head, ears, markings, body proportions, warm outline,
soft polished storybook illustration style, and color palette.

Create a precise 2 by 2 animation contact sheet containing one seamless RIGHT-facing
corgi walk cycle. Four equal square cells, ordered left-to-right and top-to-bottom.
Keep the same camera distance, body scale, head height, torso length, tail shape, and
ground-contact baseline in every cell.

Cell 1: first contact beat, right front paw and left rear paw reaching forward while the
opposite pair pushes back.
Cell 2: first passing beat, weight centered over planted paws, free legs crossing under
the torso naturally.
Cell 3: opposite contact beat, left front paw and right rear paw reaching forward.
Cell 4: opposite passing beat, returning naturally into cell 1.

This is a relaxed grounded walk, not a run, jump, play bow, sit, or slide. The paws must
visibly change position in all four cells while the body remains stable. Use a perfectly
uniform pure white background. No text, labels, numbers, borders, separators, ground
line, cast shadow, dirt, props, scenery, watermark, motion blur, duplicate limbs, or
cropped anatomy. Leave generous white padding around each complete corgi.
""".strip()


def main() -> int:
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise SystemExit("OPENAI_API_KEY is not configured")
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with REFERENCE_PATH.open("rb") as reference:
        response = requests.post(
            API_URL,
            headers={"Authorization": f"Bearer {api_key}"},
            data={
                "model": "gpt-image-2",
                "prompt": PROMPT,
                "size": "1024x1024",
                "quality": "high",
                "output_format": "png",
                "n": "1",
            },
            files={"image[]": (REFERENCE_PATH.name, reference, "image/png")},
            timeout=300,
        )
    if not response.ok:
        raise RuntimeError(f"OpenAI API returned {response.status_code}: {response.text}")
    OUTPUT_PATH.write_bytes(base64.b64decode(response.json()["data"][0]["b64_json"]))
    print(f"generated {OUTPUT_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
