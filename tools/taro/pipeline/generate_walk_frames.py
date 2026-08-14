#!/usr/bin/env python3
"""Generate individual Taro walk poses for a controlled upright biped cycle."""

from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[3]
REFERENCE = Path("/home/yhas/Pictures/pixelpals_refs/taro.png")
OUTPUT_DIR = ROOT / "tools/taro/pipeline/raw/walk_frames_v3"
API_URL = "https://api.openai.com/v1/images/edits"

COMMON_PROMPT = """
Create one single full-body animation pose of the exact Taro character from the
attached canonical reference. Taro is a cheerful baby sea turtle with light
green scaled skin, a pale yellow segmented plastron, a green patterned shell
with a warm orange rim, large glossy blue-green eyes, rosy cheeks, a friendly
smile, and four readable limbs. Preserve identity, shell geometry, proportions,
camera, studio lighting, scale, and polished 3D cartoon finish.

Taro walks upright on the two rear legs only. The two front flippers are short
arms held near the chest; they do not touch the ground, do not support the
body, and must never be stretched into long quadruped forelegs. Render exactly
four limbs total: two compact front flippers and two rear walking legs. Never
invent a third rear leg, a fifth limb, a detached foot, or an extra green
appendage behind the shell.

Use one consistent upright right-facing three-quarter view where the head and
shell stay at the same height and size in every frame. Keep both rear feet on a
single ground line when planted. The two short front flippers stay beside the
plastron at chest height, with only a small arm swing.
Use a pure white background with no shadow, labels, text, grid, props, scenery,
watermark, motion blur, cropped anatomy, duplicated limbs, or extra limbs.
Keep the dark pupils and shell pattern confined to their normal shapes; do not
add black smudges, streaks, or patches on the cheeks, eyelids, or skin.
""".strip()

POSES = (
    "Frame A contact: viewer-left rear leg planted under the hip and viewer-right rear leg lifted slightly forward. Both short front flippers are tucked beside the chest, above the ground.",
    "Frame A passing: weight stays over the viewer-left rear leg while the viewer-right rear leg passes forward. Keep both front flippers short, tucked near the plastron, and off the ground.",
    "Frame A release: viewer-left rear leg begins to push off and viewer-right rear leg reaches toward contact. Do not lengthen either front flipper; they make only a small chest-height swing.",
    "Frame B contact: viewer-right rear leg planted under the hip and viewer-left rear leg lifted slightly forward. This is the opposite hind-leg phase. Both front flippers remain compact arms beside the chest.",
    "Frame B passing: weight stays over the viewer-right rear leg while the viewer-left rear leg passes forward. Only the rear feet contact the ground; front flippers stay short and raised.",
    "Frame B release: viewer-right rear leg begins to push off and viewer-left rear leg reaches toward contact. Keep the torso upright and the front flippers close to the plastron.",
    "Frame B recovery: return toward the original viewer-left rear support phase with a stable head, shell, hip height, and ground line. Front flippers remain short arms, never long forelegs.",
    "Frame A recovery: complete the upright two-leg cycle with the viewer-left rear leg approaching contact and the viewer-right rear leg lifting. Exactly two rear walking legs and two short chest-level front flippers, no extra limb.",
)


def generate_frame(api_key: str, index: int, quality: str, force: bool) -> Path:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output = OUTPUT_DIR / f"walk_{index:02d}.png"
    if output.exists() and not force:
        print(f"skip existing {output.relative_to(ROOT)}")
        return output
    with REFERENCE.open("rb") as handle:
        response = requests.post(
            API_URL,
            headers={"Authorization": f"Bearer {api_key}"},
            data={
                "model": "gpt-image-2",
                "prompt": f"{COMMON_PROMPT}\n\n{POSES[index]}",
                "size": "1024x1024",
                "quality": quality,
                "output_format": "png",
                "n": "1",
            },
            files={"image[]": (REFERENCE.name, handle, "image/png")},
            timeout=600,
        )
    if not response.ok:
        raise RuntimeError(f"OpenAI API returned {response.status_code}: {response.text[:500]}")
    payload = response.json()
    image_data = payload.get("data", [{}])[0].get("b64_json")
    if not image_data:
        raise RuntimeError(f"OpenAI response did not contain b64_json for frame {index}")
    output.write_bytes(base64.b64decode(image_data))
    print(f"generated {output.relative_to(ROOT)} quality={quality}")
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--quality", choices=("low", "medium", "high"), default="high")
    parser.add_argument("--frame", type=int, choices=range(len(POSES)), help="generate only one pose")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        parser.error("OPENAI_API_KEY is not loaded; source ~/.config/pixelpals/load_keys.sh first")
    indexes = (args.frame,) if args.frame is not None else range(len(POSES))
    for index in indexes:
        generate_frame(api_key, index, args.quality, args.force)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
