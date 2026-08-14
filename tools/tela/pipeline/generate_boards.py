#!/usr/bin/env python3
"""Generate Tela V2 animation boards with GPT Image 2."""

from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[3]
REFERENCE_DIR = ROOT / "tools/tela"
RAW_DIR = ROOT / "tools/tela/pipeline/raw"
API_URL = "https://api.openai.com/v1/images/edits"

CANONICAL = REFERENCE_DIR / "tela.png"
MOTION_REFERENCE = REFERENCE_DIR / "tela_atlas_preview.jpg"

COMMON_PROMPT = """
Create a 2 by 2 animation contact sheet for the exact Tela character from the
attached references. Tela is a cute mint-lavender fuzzy spider with purple
heart markings, several small glossy eyes, a soft rounded body, eight short
striped legs, purple heart-shaped foot pads, and a friendly premium 3D cartoon
finish. Preserve the same face, eye count, fur pattern, heart markings, body
volume, leg count, colors, camera, scale, lighting, and silhouette in every
cell.

Make the result unmistakably an arthropod spider, not a humanoid, mammal,
devil, monster, or upright biped. Use one compact round body with no neck,
torso, arms, hands, horns, ears, tail, wings, or separate humanoid head.
Attach exactly four pairs of short legs to the sides of the same body, with
eight readable legs total in every cell. The eyes must stay grouped on the
front of the spider body, never become horns or antennae. Even in hanging
poses, keep a spider silhouette with eight curled or tucked legs rather than
an upright person silhouette.

Use a clean transparent or pure-white background with no cast shadow, contact
shadow, ground shadow, reflection, glow blob, or dark patch beneath Tela. The
game renders the silk separately, so do not add a large web or environmental
shadow to the character frames.

One complete full-body pose per equal square cell, ordered left-to-right and
top-to-bottom. Keep the whole spider inside every cell with clean transparent
or pure-white background, no labels, text, grid, props, scenery, watermark,
motion blur, cropped anatomy, duplicate legs, missing legs, extra legs, or
changed character design.
""".strip()

BOARDS: tuple[dict[str, object], ...] = (
    {
        "name": "01_idle_grounded",
        "prompt": """
        Four calm grounded idle poses. Tela stays low and centered with all eight
        legs readable. Use tiny breathing, blink, cheek and fur movements only;
        no travel and no web in this clip.
        """,
    },
    {
        "name": "02_floor_walk_a",
        "prompt": """
        Four connected floor-walk poses. Tela moves sideways on all eight legs
        with a believable alternating spider gait, stable body height and stable
        camera and no shadow under the feet. Each cell changes the leg contacts slightly; do not turn the
        spider into a mammal or add a fifth pair of legs.
        """,
    },
    {
        "name": "03_floor_walk_b",
        "prompt": """
        Complete the same floor-walk cycle with four connected continuation
        poses. Keep body size, camera and ground line identical to the previous
        board. Alternate the leg contacts naturally and preserve exactly eight
        legs.
        """,
    },
    {
        "name": "04_wall_climb",
        "prompt": """
        Four connected vertical wall-climb poses in a right-facing side view.
        Tela is attached to an invisible wall at the side, body stable while
        legs reach upward in alternating pairs. All eight legs remain visible;
        no web line, cast shadow, or dark patch in this clip.
        """,
    },
    {
        "name": "05_ceiling_crawl",
        "prompt": """
        Four connected upside-down ceiling-crawl poses. Rotate the same Tela
        character naturally so the eight legs grip an invisible ceiling. Keep
        the body and face readable, with no duplicated or missing legs and no
        shadow under the spider.
        """,
    },
    {
        "name": "06_web_descend",
        "prompt": """
        Four connected poses of Tela descending vertically while hanging from a
        single silk thread attached to the top center of the abdomen. The silk
        itself may be a very subtle short white-lavender thread inside the cell,
        but the game will draw the full thread separately. Tela's round spider
        body hangs vertically, with all eight legs tucked and slightly curled,
        not walking on air and not shaped like a person. No cast shadow beneath
        the hanging spider.
        """,
    },
    {
        "name": "07_web_hang",
        "prompt": """
        Four connected suspended poses while Tela hangs from the abdomen on one
        invisible vertical silk thread. Show a gentle side-to-side sway through
        body tilt and compact curled legs. Keep the exact same character and
        eight legs; no ground, no shadow, no extra web, no floating pose without
        an anchor.
        """,
    },
    {
        "name": "08_web_ascend",
        "prompt": """
        Four connected poses of Tela rising vertically back toward the ceiling
        while suspended from the same abdomen silk point. Compact curled legs,
        round spider body hanging vertically, consistent camera and scale, no
        crawling, ground contact, or shadow, and no humanoid silhouette.
        """,
    },
    {
        "name": "09_land_touch",
        "prompt": """
        Four connected landing and friendly reaction poses after descending.
        Tela settles onto all eight legs, gives a tiny happy bounce and returns
        to a calm grounded spider stance. Preserve the body and leg count.
        """,
    },
    {
        "name": "10_sleep",
        "prompt": """
        Four peaceful sleepy spider poses: drowsy blink, compact curl, tiny
        breathing change and relaxed return. Keep all eight short legs readable
        and the same lavender heart-marked character.
        """,
    },
)


def generate_board(api_key: str, board: dict[str, object], quality: str, force: bool) -> Path:
    name = str(board["name"])
    output = RAW_DIR / f"{name}.png"
    if output.exists() and not force:
        print(f"skip existing {output.relative_to(ROOT)}")
        return output

    files = []
    handles = []
    try:
        for path in (CANONICAL, MOTION_REFERENCE):
            handle = path.open("rb")
            handles.append(handle)
            mime = "image/jpeg" if path.suffix.lower() in {".jpg", ".jpeg"} else "image/png"
            files.append(("image[]", (path.name, handle, mime)))
        response = requests.post(
            API_URL,
            headers={"Authorization": f"Bearer {api_key}"},
            data={
                "model": "gpt-image-2",
                "prompt": f"{COMMON_PROMPT}\n\n{str(board['prompt']).strip()}",
                "size": "1024x1024",
                "quality": quality,
                "output_format": "png",
                "n": "1",
            },
            files=files,
            timeout=600,
        )
    finally:
        for handle in handles:
            handle.close()

    if not response.ok:
        raise RuntimeError(f"OpenAI API returned {response.status_code}: {response.text[:500]}")
    payload = response.json()
    image_data = payload.get("data", [{}])[0].get("b64_json")
    if not image_data:
        raise RuntimeError(f"OpenAI response did not contain b64_json for {name}")
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    output.write_bytes(base64.b64decode(image_data))
    print(f"generated {output.relative_to(ROOT)} quality={quality}")
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all", action="store_true", help="generate every board")
    parser.add_argument("--board", choices=[str(board["name"]) for board in BOARDS])
    parser.add_argument("--quality", choices=("low", "medium", "high"), default="high")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    if not args.all and not args.board:
        parser.error("use --all or --board")
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        parser.error("OPENAI_API_KEY is not loaded; source ~/.config/pixelpals/load_keys.sh first")
    boards = BOARDS if args.all else tuple(board for board in BOARDS if board["name"] == args.board)
    for board in boards:
        generate_board(api_key, board, args.quality, args.force)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
