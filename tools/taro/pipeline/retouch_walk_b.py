#!/usr/bin/env python3
"""Retouch only Taro's hind-leg phase in the V2 walk continuation board."""

from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path

import requests
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[3]
RAW_DIR = ROOT / "tools/taro/pipeline/raw"
INPUT_PATH = RAW_DIR / "03_walk_b.png"
MASK_PATH = ROOT / "tools/taro/pipeline/03_walk_b_hind_legs_mask.png"
API_URL = "https://api.openai.com/v1/images/edits"

PROMPT = """
Edit only the lower hind-leg areas of this four-cell Taro walk continuation
board. Preserve every pixel outside the masked areas: exact Taro identity,
head, eyes, cheeks, smile, shell, orange rim, yellow plastron, lighting, camera,
scale, baseline, and both front flippers. Do not add a shadow, background, text,
or props.

Create a strict alternating hind-leg gait across the four cells. Taro uses four
limbs to walk. In cell 1 the viewer-left rear foot is planted and the viewer-
right rear foot is lifted and passing. In cell 2 swap them: viewer-right rear
foot planted and viewer-left rear foot lifted. In cell 3 repeat the first phase
with a visibly different step distance. In cell 4 repeat the opposite phase.
Both rear feet must be visible and separated below the shell in every cell. The
two front flippers must remain low, unchanged, and never wave. The rear leg
positions must visibly alternate left versus right; do not return the same rear
leg to the same pose in all cells.
""".strip()


def build_mask() -> Image.Image:
    mask = Image.new("RGBA", (1024, 1024), (255, 255, 255, 255))
    draw = ImageDraw.Draw(mask)
    for row in range(2):
        top = row * 512 + 250
        for col in range(2):
            left = col * 512 + 40
            draw.rectangle((left, top, col * 512 + 472, row * 512 + 510), fill=(0, 0, 0, 0))
    return mask


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    if not INPUT_PATH.exists():
        raise FileNotFoundError(INPUT_PATH)
    if not args.force and MASK_PATH.exists():
        print(f"skip existing {MASK_PATH.relative_to(ROOT)}")
        return 0

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        parser.error("OPENAI_API_KEY is not loaded; source ~/.config/pixelpals/load_keys.sh first")

    mask = build_mask()
    mask.save(MASK_PATH)
    with INPUT_PATH.open("rb") as image_handle, MASK_PATH.open("rb") as mask_handle:
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
            files={
                "image[]": (INPUT_PATH.name, image_handle, "image/png"),
                "mask": (MASK_PATH.name, mask_handle, "image/png"),
            },
            timeout=600,
        )
    if not response.ok:
        raise RuntimeError(f"OpenAI API returned {response.status_code}: {response.text[:500]}")
    payload = response.json()
    image_data = payload.get("data", [{}])[0].get("b64_json")
    if not image_data:
        raise RuntimeError("OpenAI response did not contain b64_json")
    INPUT_PATH.write_bytes(base64.b64decode(image_data))
    print(f"retouched {INPUT_PATH.relative_to(ROOT)} using {MASK_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
