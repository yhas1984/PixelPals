#!/usr/bin/env python3
"""Build a labeled review board for Lumi's six master-pose candidates."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[4]
FRAME_DIR = ROOT / "tools/lumi/archive/v1/source_atlas/action_trial_v1/frames"
OUTPUT_DIR = ROOT / "tools/lumi/archive/v1/review/master_pose_review_v1"
BOARD_PATH = OUTPUT_DIR / "lumi_master_pose_review_v1.png"
MANIFEST_PATH = OUTPUT_DIR / "lumi_master_pose_review_v1.json"

BOARD_WIDTH = 1280
CARD_WIDTH = 600
CARD_HEIGHT = 610
CARD_GAP = 24
MARGIN = 28
HEADER_HEIGHT = 190
IMAGE_SIZE = 360

CANDIDATES = [
    {
        "id": "idle_neutral",
        "sourceFrame": 0,
        "sourceName": "idle_neutral",
        "status": "candidate_needs_retouch",
        "why": "Best current neutral standing silhouette.",
        "gaps": ["Compare face, tail, orb, and chest star against the canonical key art."],
    },
    {
        "id": "idle_breath_in",
        "sourceFrame": 1,
        "sourceName": "idle_tail_shift",
        "status": "missing_pose",
        "why": "Closest idle variation available in the trial atlas.",
        "gaps": ["This is not a clear breath pose; paint a controlled chest lift."],
    },
    {
        "id": "look_profile",
        "sourceFrame": 8,
        "sourceName": "walk_00",
        "status": "missing_pose",
        "why": "Only current source with a readable side profile.",
        "gaps": ["Redraw as an attentive profile, not a locomotion contact pose."],
    },
    {
        "id": "walk_contact_right",
        "sourceFrame": 8,
        "sourceName": "walk_00",
        "status": "candidate_needs_retouch",
        "why": "Useful contact-pose starting point for the walk cycle.",
        "gaps": ["Canonicalize facing, verify paw weight, and remove any foot-slide risk."],
    },
    {
        "id": "pounce_air",
        "sourceFrame": 19,
        "sourceName": "pounce_air",
        "status": "candidate_needs_retouch",
        "why": "Readable airborne action silhouette.",
        "gaps": ["Verify anatomy, tail continuity, orb attachment, and a stable landing arc."],
    },
    {
        "id": "sleep_curl",
        "sourceFrame": 29,
        "sourceName": "sleep_curl",
        "status": "candidate_needs_retouch",
        "why": "Strongest current comfort/sleep silhouette.",
        "gaps": ["Retouch fur and orb contact, then align the comfort pose to the ground pivot."],
    },
]


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    names = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    for name in names:
        path = Path(name)
        if path.exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, width: int) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if current and draw.textbbox((0, 0), candidate, font=font)[2] > width:
            lines.append(current)
            current = word
        else:
            current = candidate
    if current:
        lines.append(current)
    return lines


def draw_text_block(
    draw: ImageDraw.ImageDraw,
    text: str,
    origin: tuple[int, int],
    font: ImageFont.ImageFont,
    fill: tuple[int, int, int],
    width: int,
    line_gap: int = 5,
) -> int:
    x, y = origin
    for line in wrap_text(draw, text, font, width):
        draw.text((x, y), line, font=font, fill=fill)
        y += font.size + line_gap
    return y


def composite_frame(frame: Image.Image, size: int) -> Image.Image:
    background = Image.new("RGBA", (size, size), (38, 53, 64, 255))
    scaled = frame.resize((size, size), Image.Resampling.LANCZOS)
    background.alpha_composite(scaled)
    return background


def build_manifest() -> dict[str, object]:
    return {
        "version": 1,
        "petId": "lumi",
        "status": "review_pending",
        "sourceAtlas": "tools/lumi/archive/v1/source_atlas/action_trial_v1/lumi_action_trial_v1.png",
        "sourceFrameDirectory": "tools/lumi/archive/v1/source_atlas/action_trial_v1/frames",
        "reviewBoard": "tools/lumi/archive/v1/review/master_pose_review_v1/lumi_master_pose_review_v1.png",
        "promotionAllowed": False,
        "approvalRule": "All six master poses must pass manual retouch and visual QA before production frame generation.",
        "candidates": CANDIDATES,
        "reviewChecklist": {
            "identityMatch": "pending",
            "silhouetteConsistency": "pending",
            "pivotAndGroundLine": "pending",
            "transparentPadding": "pending",
            "noBackgroundOrHalos": "pending",
            "deviceSizeReadability": "pending",
        },
    }


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    font_title = load_font(34, bold=True)
    font_subtitle = load_font(17)
    font_card = load_font(25, bold=True)
    font_small = load_font(16)
    font_status = load_font(14, bold=True)

    rows = 3
    board_height = HEADER_HEIGHT + MARGIN + rows * CARD_HEIGHT + (rows - 1) * CARD_GAP + MARGIN
    board = Image.new("RGB", (BOARD_WIDTH, board_height), (16, 25, 34))
    draw = ImageDraw.Draw(board)
    draw.text((MARGIN, 28), "Lumi Master Pose Review v1", font=font_title, fill=(255, 244, 221))
    draw.text(
        (MARGIN, 82),
        "R&D candidates only. Nothing on this board is production-approved.",
        font=font_subtitle,
        fill=(184, 197, 206),
    )
    draw.text(
        (MARGIN, 116),
        "Approve the six master poses before expanding the final atlas.",
        font=font_subtitle,
        fill=(85, 226, 211),
    )

    for position, candidate in enumerate(CANDIDATES):
        column = position % 2
        row = position // 2
        x = MARGIN + column * (CARD_WIDTH + CARD_GAP)
        y = HEADER_HEIGHT + row * (CARD_HEIGHT + CARD_GAP)
        draw.rounded_rectangle((x, y, x + CARD_WIDTH, y + CARD_HEIGHT), radius=18, fill=(24, 38, 49), outline=(65, 96, 107), width=2)
        draw.text((x + 22, y + 18), candidate["id"], font=font_card, fill=(255, 195, 106))
        status_fill = (255, 173, 158) if candidate["status"] == "missing_pose" else (255, 195, 106)
        draw.rounded_rectangle((x + CARD_WIDTH - 216, y + 20, x + CARD_WIDTH - 22, y + 50), radius=12, fill=(65, 39, 43))
        draw.text((x + CARD_WIDTH - 202, y + 27), candidate["status"].replace("_", " ").upper(), font=font_status, fill=status_fill)

        frame_path = FRAME_DIR / f"lumi_{candidate['sourceFrame']:02d}_{candidate['sourceName']}.png"
        frame = Image.open(frame_path).convert("RGBA")
        image = composite_frame(frame, IMAGE_SIZE)
        image_x = x + (CARD_WIDTH - IMAGE_SIZE) // 2
        image_y = y + 66
        board.paste(image.convert("RGB"), (image_x, image_y))
        draw.line((image_x, image_y + round(368 * IMAGE_SIZE / 384), image_x + IMAGE_SIZE, image_y + round(368 * IMAGE_SIZE / 384)), fill=(255, 195, 106), width=1)
        draw.text((x + 22, y + 444), f"trial frame {candidate['sourceFrame']:02d}  |  {candidate['sourceName']}", font=font_small, fill=(184, 197, 206))
        text_y = draw_text_block(draw, candidate["why"], (x + 22, y + 474), font_small, (255, 244, 221), CARD_WIDTH - 44)
        draw_text_block(draw, "Retouch gate: " + candidate["gaps"][0], (x + 22, text_y + 3), font_small, (184, 197, 206), CARD_WIDTH - 44)

    board.save(BOARD_PATH, optimize=True)
    MANIFEST_PATH.write_text(json.dumps(build_manifest(), indent=2) + "\n", encoding="utf-8")
    print(f"LUMI_MASTER_POSE_REVIEW_BUILT board={BOARD_PATH} candidates={len(CANDIDATES)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
