#!/usr/bin/env python3
"""Generate Lumi V2 sequential animation boards with GPT Image 2."""

from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[3]
REFERENCE_DIR = Path("/home/yhas/Pictures/pixelpals_refs")
RAW_DIR = ROOT / "tools/lumi/pipeline/raw"
API_URL = "https://api.openai.com/v1/images/edits"

CANONICAL = REFERENCE_DIR / "lumi.png"
TURNAROUND = REFERENCE_DIR / "lumi_turnaround_sheet.png"
ACTION = REFERENCE_DIR / "lumi_action_sheet.png"
EXPRESSIONS = REFERENCE_DIR / "lumi_expressions_sheet.png"

COMMON_PROMPT = """
Create a 2 by 2 animation contact sheet for a game sprite. Use the attached
canonical Lumi reference as the strict character identity reference. Lumi is
the exact same cute golden-orange baby fox in every cell: cream muzzle, chest,
paws, ear interiors and tail tip; large cyan eyes with painted highlights;
cream forehead tuft and cheek fur; cyan star on the chest; oversized expressive
tail with a cyan glowing orb attached to its tip; warm painted 2D illustration
with soft warm contours and consistent light direction.

One complete full-body pose per equal square cell, ordered left-to-right and
top-to-bottom. Keep the same camera distance, body scale, proportions, lighting,
ground/contact baseline, tail volume, orb attachment, eye style and outline
language in all four cells. Make the sequence physically continuous and useful
as frame-by-frame animation. Use a perfectly uniform pure white background.
No labels, text, numbers, borders, separators, grid lines, props, scenery,
floor texture, detached shadows, watermark, motion blur, cropped anatomy,
duplicate limbs, extra tails, missing paws or redesigned character.
""".strip()


BOARDS: tuple[dict[str, object], ...] = (
    {
        "name": "01_idle_breath",
        "references": (EXPRESSIONS,),
        "prompt": """
        All four cells are a calm grounded idle loop in a readable right-facing
        three-quarter view. Cell 1: neutral relaxed standing pose, all paws
        weight-bearing. Cell 2: gentle inhale, chest and shoulders lift only a
        little while paws and ground line stay fixed. Cell 3: gentle exhale,
        chest settles and eyelids soften, no head bob. Cell 4: return to the
        exact neutral silhouette ready to loop to cell 1. This is breathing,
        not four different poses or a walk cycle.
        """,
    },
    {
        "name": "02_walk_a",
        "references": (ACTION,),
        "prompt": """
        All four cells are the first half of one slow baby-fox walk cycle,
        strict right-facing side profile. Keep the belly and paws on one fixed
        horizontal contact line and keep the tail/orb relationship stable.
        Cell 1: right forepaw contact and opposite rear paw contact. Cell 2:
        body passes forward as the rear pair alternates. Cell 3: opposite
        forepaw contact with a clear weight shift through the shoulders. Cell 4:
        passing pose with the lifted paws changing sides. Walk calmly with four
        readable paws and no running, floating or sliding.
        """,
    },
    {
        "name": "03_walk_b",
        "references": (ACTION,),
        "depends_on": "02_walk_a",
        "prompt": """
        Continue the exact same right-facing slow walk cycle from the supplied
        previous walk board. Produce the second half of the same eight-frame
        cycle. Cell 1: next opposite contact pose, matching the previous board's
        last passing pose. Cell 2: body passes over the planted paws. Cell 3:
        return toward the original contact arrangement. Cell 4: the exact same
        neutral contact arrangement as the first cell of the previous board so
        the complete eight-frame loop closes cleanly. Keep identical scale,
        baseline, tail arc, orb location and body length. No foot sliding.
        """,
    },
    {
        "name": "04_turn_in_place",
        "references": (TURNAROUND, ACTION),
        "prompt": """
        All four cells are one slow in-place direction change on the same ground
        baseline. Cell 1: relaxed right-facing side profile. Cell 2: three-quarter
        view while the torso and tail begin rotating, paws remain planted close
        to the same footprint. Cell 3: clear front-facing neutral view looking
        directly at the viewer, centered and balanced. Cell 4: left-facing
        three-quarter follow-through with the tail trailing naturally. The fox
        rotates in place rather than translating across the sheet. Preserve the
        orb on the tail tip and do not stretch or shrink the body.
        """,
    },
    {
        "name": "05_hop_up",
        "references": (ACTION,),
        "prompt": """
        All four cells are one compact diagonal baby-fox hop that moves upward.
        Cell 1: crouched anticipation on the ground, knees and body compressed.
        Cell 2: takeoff, front paws lifting and hind paws pushing, body rising.
        Cell 3: airborne at the top of the hop with paws tucked naturally and
        tail/orb counterbalancing, clearly no contact with the ground. Cell 4:
        landing on a higher path, paws reaching down and body beginning to
        settle. Keep the hop playful and compact, not a pounce or attack.
        """,
    },
    {
        "name": "06_hop_down",
        "references": (ACTION,),
        "depends_on": "05_hop_up",
        "prompt": """
        Continue the same compact hop from the supplied upward-hop board, now
        showing the downward phase. Cell 1: airborne descending with paws reaching
        for the lower landing. Cell 2: front paws make contact and the body
        compresses. Cell 3: hind paws land and the tail/orb counterbalances.
        Cell 4: relaxed recovery pose ready to resume the same side-facing walk.
        Keep the fox identity, scale and light unchanged; this is a landing, not
        a pounce, fall, or teleport.
        """,
    },
    {
        "name": "07_front_social",
        "references": (EXPRESSIONS, TURNAROUND),
        "prompt": """
        All four cells are a quiet front-facing social sequence, centered on the
        same ground baseline. Cell 1: relaxed front-facing neutral stance looking
        directly at the viewer. Cell 2: tiny friendly blink with the body held
        still. Cell 3: one small front paw lifts in a gentle greeting while the
        other paws remain planted and the face stays front-facing. Cell 4: paw
        returns and the fox settles into the exact neutral front-facing stance.
        Keep the tail and cyan orb visible behind the body and avoid head bobbing,
        rapid nodding or exaggerated waving.
        """,
    },
    {
        "name": "08_play_pounce_recover",
        "references": (ACTION, EXPRESSIONS),
        "prompt": """
        All four cells are one short playful burst from the same right-facing
        three-quarter view. Cell 1: low curious crouch with weight forward.
        Cell 2: small joyful hop forward, paws lifted but body still compact.
        Cell 3: soft landing with paws reaching the ground and tail balancing.
        Cell 4: affectionate recovery, relaxed eyes and stable standing posture.
        It must read as a baby fox playing, not attacking, flying or changing
        into a different character.
        """,
    },
    {
        "name": "09_sleep",
        "references": (EXPRESSIONS,),
        "prompt": """
        All four cells are one quiet sleep transition for the same baby fox,
        grounded on a shared baseline. Cell 1: drowsy standing pose with heavy
        eyelids and relaxed ears. Cell 2: the fox gently lowers its chest and
        tucks its paws, beginning to curl without changing its proportions. Cell
        3: fully curled sleeping pose viewed from a readable three-quarter side,
        eyes closed, tail wrapped naturally near the body and the cyan orb still
        attached to the tail tip. Cell 4: the same curled pose with a tiny calm
        breathing change, ready to loop between cells 3 and 4. Keep the sleep
        motion subtle and peaceful; no new blanket, bed, scenery, text, or
        dramatic pose change.
        """,
    },
    {
        "name": "10_magic",
        "references": (ACTION, EXPRESSIONS),
        "prompt": """
        All four cells are one small magical wonder sequence in the same grounded
        right-facing three-quarter view. Cell 1: Lumi notices the cyan orb on
        the tail and becomes attentive, ears raised and eyes focused. Cell 2:
        one front paw lifts gently toward the orb while the other paws stay
        grounded. Cell 3: the orb gives off a restrained cyan glow and a tiny
        soft ring of light close to the orb, with Lumi delighted but stable. Cell
        4: the glow settles and Lumi returns to the exact relaxed attentive pose
        ready to recover to idle. The orb stays attached to the tail; do not add
        a wand, projectile, separate orb, particles across the background, or a
        different character.
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
