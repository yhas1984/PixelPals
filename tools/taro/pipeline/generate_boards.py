#!/usr/bin/env python3
"""Generate Taro V2 sequential animation boards with GPT Image 2."""

from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[3]
REFERENCE_DIR = Path("/home/yhas/Pictures/pixelpals_refs")
RAW_DIR = ROOT / "tools/taro/pipeline/raw"
API_URL = "https://api.openai.com/v1/images/edits"

CANONICAL = REFERENCE_DIR / "taro.png"
MOTION_REFERENCE = REFERENCE_DIR / "taro_atlas_preview.png"

COMMON_PROMPT = """
Create a 2 by 2 animation contact sheet for a game mascot. Use the attached
canonical Taro reference as the strict character identity reference. Taro is
the exact same cheerful baby sea turtle in every cell: light green scaled skin,
large glossy dark eyes with teal and blue highlights, rosy cheeks, a friendly
open smile, a pale yellow segmented plastron on the front, and a green patterned
carapace with a distinct warm orange rim. Preserve the same chibi proportions,
rounded head, flipper shape, shell geometry, claw tips, facial features, camera,
soft studio lighting, and polished 3D animated character finish.

One complete full-body pose per equal square cell, ordered left-to-right and
top-to-bottom. Keep the entire turtle inside every cell, with a stable contact
line and consistent scale. Use a perfectly uniform pure white background. No
labels, text, numbers, borders, separators, grid lines, props, scenery, floor
texture, detached shadows, watermark, motion blur, cropped anatomy, duplicate
limbs, missing flippers, changed shell colors, or redesigned character.
""".strip()


BOARDS: tuple[dict[str, object], ...] = (
    {
        "name": "01_idle_breathe",
        "references": (),
        "prompt": """
        All four cells are one calm grounded idle loop for a patient garden
        turtle, in the same readable right-facing three-quarter view. Cell 1 is
        neutral and relaxed with all feet planted. Cell 2 is a small inhale with
        the chest lifting. Cell 3 is a small exhale with the shell settling and
        eyelids softening. Cell 4 returns to the exact neutral silhouette. Keep
        the head, shell rim, plastron, cheeks and eye highlights stable; this is
        breathing, not four unrelated poses.
        """,
    },
    {
        "name": "02_walk_a",
        "references": (MOTION_REFERENCE,),
        "prompt": """
        All four cells are the first half of one very slow upright two-leg walk
        cycle in a strict right-facing three-quarter view. Taro walks on the two
        rear legs only, with the torso and head held high over the hips. The two
        front flippers are short arms held beside the plastron at chest height;
        they must never touch the ground or stretch into long quadruped forelegs.
        Keep the camera, head height, shell size, hip height and rear-foot ground
        line stable in all cells. Alternate only the two rear legs. No swimming,
        crawling, four-legged gait, sliding, or arm-length changes.
        """,
    },
    {
        "name": "03_walk_b",
        "references": (MOTION_REFERENCE,),
        "depends_on": "02_walk_a",
        "prompt": """
        Continue the exact same upright two-leg walk from the supplied previous
        board and complete the second half. Keep the same camera, head height,
        shell size, torso angle, hip height and rear-foot ground line. Alternate
        only the two rear legs: cell 1 viewer-left rear leg supports while the
        viewer-right rear leg passes; cell 2 swaps support; cells 3 and 4 repeat
        the complementary phase. The two front flippers are compact chest-level
        arms in every cell, never ground-contact forelegs and never elongated.
        No crawling, four-legged gait, extra limbs, swimming, floating, or foot
        sliding.
        """,
    },
    {
        "name": "04_turn_in_place",
        "references": (MOTION_REFERENCE,),
        "prompt": """
        All four cells are one slow in-place direction change on the same ground
        baseline. Cell 1 is relaxed right-facing profile. Cell 2 rotates to a
        three-quarter view with feet close to the same footprint. Cell 3 is a
        friendly centered front view with the plastron readable. Cell 4 follows
        through toward the opposite profile. The turtle rotates in place; do not
        translate, stretch or change shell proportions.
        """,
    },
    {
        "name": "05_hide_shell",
        "references": (MOTION_REFERENCE,),
        "prompt": """
        All four cells are a gentle defensive hide sequence. Cell 1 is neutral
        and alert. Cell 2 lowers the head and draws the flippers inward. Cell 3
        shows the head and flippers mostly retracted while the shell remains the
        same size and shape. Cell 4 is the fully closed, readable shell pose.
        Keep a stable footprint and do not turn Taro into a different shell or a
        flat icon.
        """,
    },
    {
        "name": "06_peek_out",
        "references": (MOTION_REFERENCE,),
        "depends_on": "05_hide_shell",
        "prompt": """
        Continue from the closed shell pose and show Taro cautiously returning.
        Cell 1 is the closed shell. Cell 2 reveals the eyes and forehead. Cell 3
        brings the head and one flipper out with a curious expression. Cell 4 is
        a relaxed neutral stance ready to idle. Preserve the exact shell rim,
        yellow plastron, green skin, eye highlights and ground contact.
        """,
    },
    {
        "name": "07_front_social",
        "references": (MOTION_REFERENCE,),
        "prompt": """
        All four cells are a quiet front-facing greeting. Cell 1 is a friendly
        centered neutral stance. Cell 2 is a tiny blink and cheek lift. Cell 3
        raises one flipper in a small welcoming wave. Cell 4 lowers the flipper
        and settles into the exact front neutral pose. Keep the plastron and both
        shell edges visible, with no exaggerated arm or head motion.
        """,
    },
    {
        "name": "08_touch_recover",
        "references": (MOTION_REFERENCE,),
        "prompt": """
        All four cells are one shy but affectionate touch reaction. Cell 1 is
        calm and neutral. Cell 2 pulls the head and flippers inward with a
        surprised expression. Cell 3 begins to peek out again, eyes curious and
        cheeks warm. Cell 4 is a relaxed recovered stance. This is a small
        emotional reaction, not a jump, attack or shell redesign.
        """,
    },
    {
        "name": "09_sleep",
        "references": (MOTION_REFERENCE,),
        "prompt": """
        All four cells are a peaceful sleep transition for the same turtle. Cell
        1 is drowsy and standing. Cell 2 lowers the head and relaxes the flippers.
        Cell 3 is a compact curled sleeping pose with closed eyes and the shell
        still clearly readable. Cell 4 is the same sleeping pose with a tiny
        breathing change. Keep the sleep grounded and subtle; add no blanket,
        bed, moon, scenery, text or props.
        """,
    },
    {
        "name": "10_garden_curiosity",
        "references": (MOTION_REFERENCE,),
        "prompt": """
        All four cells are a small curious garden-turtle moment without adding
        any prop or scenery. Cell 1 notices something off-screen. Cell 2 leans
        forward slightly with the head extended. Cell 3 lifts one front flipper
        and looks delighted while keeping the feet planted. Cell 4 returns to a
        calm attentive stance. Preserve Taro's shell, plastron, eyes and rounded
        baby proportions; this must remain an in-place character animation.
        """,
    },
)


def load_board(name: str) -> Path:
    path = RAW_DIR / f"{name}.png"
    if not path.exists():
        raise FileNotFoundError(path)
    return path


def generate_board(api_key: str, board: dict[str, object], quality: str, force: bool) -> Path:
    name = str(board["name"])
    output_path = RAW_DIR / f"{name}.png"
    if output_path.exists() and not force:
        print(f"skip existing {output_path.relative_to(ROOT)}")
        return output_path

    reference_paths = [CANONICAL, *tuple(board["references"])]
    dependency = board.get("depends_on")
    if dependency:
        reference_paths.append(load_board(str(dependency)))

    files: list[tuple[str, tuple[str, object, str]]] = []
    handles = []
    try:
        for path in reference_paths:
            handle = path.open("rb")
            handles.append(handle)
            files.append(("image[]", (path.name, handle, "image/png")))
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
    output_path.write_bytes(base64.b64decode(image_data))
    print(f"generated {output_path.relative_to(ROOT)} quality={quality}")
    return output_path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all", action="store_true", help="generate every board in dependency order")
    parser.add_argument("--board", choices=[str(board["name"]) for board in BOARDS])
    parser.add_argument("--quality", choices=("low", "medium", "high"), default="low")
    parser.add_argument("--force", action="store_true", help="overwrite an existing board")
    args = parser.parse_args()
    if not args.all and not args.board:
        parser.error("choose --all or --board")
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        parser.error("OPENAI_API_KEY is not loaded; source ~/.config/pixelpals/load_keys.sh first")
    for board in BOARDS:
        if args.all or str(board["name"]) == args.board:
            generate_board(api_key, board, args.quality, args.force)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
