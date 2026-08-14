#!/usr/bin/env python3
"""Build normalized Lumi master-pose review assets without inventing art."""

from __future__ import annotations

import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[4]
TRIAL_FRAME_DIR = ROOT / "tools/lumi/archive/v1/source_atlas/action_trial_v1/frames"
TURNAROUND_PATH = Path("/home/yhas/Pictures/pixelpals_refs/lumi_turnaround_sheet.png")
OUTPUT_DIR = ROOT / "tools/lumi/archive/v1/review/master_pose_review_v2"
FRAME_DIR = OUTPUT_DIR / "frames"
BOARD_PATH = OUTPUT_DIR / "lumi_master_pose_review_v2.png"
MANIFEST_PATH = OUTPUT_DIR / "lumi_master_pose_review_v2.json"

FRAME_SIZE = 384
MINIMUM_PADDING = 16
CONTENT_LIMIT = FRAME_SIZE - MINIMUM_PADDING * 2
COLUMNS = 2
CARD_WIDTH = 600
CARD_HEIGHT = 610
CARD_GAP = 24
MARGIN = 28
HEADER_HEIGHT = 190

CANDIDATES = [
    {
        "id": "idle_neutral",
        "source": "action_trial_v1/frames/lumi_00_idle_neutral.png",
        "sourceType": "existing_normalized_frame",
        "flipHorizontal": False,
        "status": "candidate_needs_retouch",
        "note": "Best current neutral standing silhouette; still needs manual identity cleanup.",
    },
    {
        "id": "idle_breath_in",
        "source": "action_trial_v1/frames/lumi_01_idle_tail_shift.png",
        "sourceType": "reference_only_nearest_pose",
        "flipHorizontal": False,
        "status": "manual_paint_required",
        "note": "Nearest idle reference is not a real breath pose. Do not promote it as animation.",
    },
    {
        "id": "look_profile",
        "source": str(TURNAROUND_PATH),
        "sourceType": "turnaround_profile_extract",
        "flipHorizontal": True,
        "status": "manual_paint_required",
        "note": "Extracted side profile is a design reference, not an approved attentive animation pose.",
    },
    {
        "id": "walk_contact_right",
        "source": "action_trial_v1/frames/lumi_08_walk_00.png",
        "sourceType": "existing_normalized_frame",
        "flipHorizontal": True,
        "status": "candidate_needs_retouch",
        "note": "Useful locomotion contact candidate; verify paw weight, facing, and foot-slide continuity.",
    },
    {
        "id": "pounce_air",
        "source": "action_trial_v1/frames/lumi_19_pounce_air.png",
        "sourceType": "existing_normalized_frame",
        "flipHorizontal": True,
        "status": "candidate_needs_retouch",
        "note": "Readable airborne silhouette; verify anatomy, tail continuity, and orb attachment.",
    },
    {
        "id": "sleep_curl",
        "source": "action_trial_v1/frames/lumi_29_sleep_curl.png",
        "sourceType": "existing_normalized_frame",
        "flipHorizontal": False,
        "status": "candidate_needs_retouch",
        "note": "Strong comfort silhouette; retouch fur/orb contact and validate its ground anchor.",
    },
]


def get_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def is_neutral_background(pixel: tuple[int, int, int]) -> bool:
    return max(pixel) - min(pixel) <= 18 and min(pixel) >= 210


def remove_border_background(image: Image.Image) -> Image.Image:
    rgb = image.convert("RGB")
    width, height = rgb.size
    pixels = list(rgb.getdata())
    candidate = bytearray(is_neutral_background(pixel) for pixel in pixels)
    visited = bytearray(width * height)
    queue: deque[tuple[int, int]] = deque()
    for x in range(width):
        queue.append((x, 0))
        queue.append((x, height - 1))
    for y in range(height):
        queue.append((0, y))
        queue.append((width - 1, y))
    while queue:
        x, y = queue.popleft()
        index = y * width + x
        if visited[index] or not candidate[index]:
            continue
        visited[index] = 1
        for next_x, next_y in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= next_x < width and 0 <= next_y < height:
                queue.append((next_x, next_y))
    rgba = bytearray()
    for index, pixel in enumerate(pixels):
        rgba.extend((*pixel, 0 if visited[index] else 255))
    return Image.frombytes("RGBA", (width, height), bytes(rgba))


def get_components(image: Image.Image) -> list[dict[str, object]]:
    alpha = np.asarray(image.getchannel("A")) > 0
    height, width = alpha.shape
    visited = np.zeros((height, width), dtype=bool)
    components: list[dict[str, object]] = []
    for start_y, start_x in np.argwhere(alpha):
        start_y, start_x = int(start_y), int(start_x)
        if visited[start_y, start_x]:
            continue
        queue: deque[tuple[int, int]] = deque([(start_x, start_y)])
        visited[start_y, start_x] = True
        points: list[tuple[int, int]] = []
        while queue:
            x, y = queue.popleft()
            points.append((x, y))
            for next_x in range(max(0, x - 1), min(width, x + 2)):
                for next_y in range(max(0, y - 1), min(height, y + 2)):
                    if alpha[next_y, next_x] and not visited[next_y, next_x]:
                        visited[next_y, next_x] = True
                        queue.append((next_x, next_y))
        if len(points) < 500:
            continue
        xs = [point[0] for point in points]
        ys = [point[1] for point in points]
        components.append({
            "area": len(points),
            "bounds": (min(xs), min(ys), max(xs) + 1, max(ys) + 1),
            "center": (sum(xs) / len(xs), sum(ys) / len(ys)),
        })
    return components


def extract_turnaround_profile() -> Image.Image:
    source = remove_border_background(Image.open(TURNAROUND_PATH).convert("RGB"))
    components = get_components(source)
    width, height = source.size
    candidates = [
        component
        for component in components
        if width * 4 / 8 <= component["center"][0] < width * 6 / 8
        and component["center"][1] < height / 2
    ]
    if not candidates:
        raise ValueError("No turnaround profile component found")
    component = max(candidates, key=lambda item: item["area"])
    left, top, right, bottom = component["bounds"]
    rgba = np.asarray(source).copy()
    alpha = np.zeros(rgba.shape[:2], dtype=np.uint8)
    # Re-select pixels inside the chosen component bounds; the profile cell
    # contains one large character and no other production art.
    alpha[top:bottom, left:right] = np.asarray(source.getchannel("A"))[top:bottom, left:right]
    rgba[:, :, 3] = alpha
    return Image.fromarray(rgba, "RGBA").crop((left, top, right, bottom))


def normalize_existing(frame: Image.Image, flip: bool) -> Image.Image:
    result = frame.convert("RGBA")
    if flip:
        result = result.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    return result


def normalize_subject(subject: Image.Image, flip: bool) -> Image.Image:
    if flip:
        subject = subject.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    subject = subject.crop(subject.getchannel("A").getbbox())
    scale = min(CONTENT_LIMIT / subject.width, CONTENT_LIMIT / subject.height)
    resized = subject.resize((round(subject.width * scale), round(subject.height * scale)), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
    x = (FRAME_SIZE - resized.width) // 2
    y = FRAME_SIZE - MINIMUM_PADDING - resized.height
    frame.alpha_composite(resized, (x, y))
    return frame


def wrap(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, width: int) -> list[str]:
    lines: list[str] = []
    current = ""
    for word in text.split():
        candidate = f"{current} {word}".strip()
        if current and draw.textbbox((0, 0), candidate, font=font)[2] > width:
            lines.append(current)
            current = word
        else:
            current = candidate
    if current:
        lines.append(current)
    return lines


def draw_wrapped(draw: ImageDraw.ImageDraw, text: str, x: int, y: int, font: ImageFont.ImageFont, fill: tuple[int, int, int], width: int) -> int:
    for line in wrap(draw, text, font, width):
        draw.text((x, y), line, font=font, fill=fill)
        y += font.size + 5
    return y


def build_manifest() -> dict[str, object]:
    return {
        "version": 2,
        "petId": "lumi",
        "status": "review_pending",
        "promotionAllowed": False,
        "boardPath": "tools/lumi/archive/v1/review/master_pose_review_v2/lumi_master_pose_review_v2.png",
        "frameDirectory": "tools/lumi/archive/v1/review/master_pose_review_v2/frames",
        "technicalContract": {
            "frameWidth": FRAME_SIZE,
            "frameHeight": FRAME_SIZE,
            "minimumTransparentPaddingPx": MINIMUM_PADDING,
            "pivot": {"x": 192, "y": 368},
            "canonicalFacing": "right",
        },
        "candidates": CANDIDATES,
        "reviewChecklist": {
            "identityMatch": "pending",
            "silhouetteConsistency": "pending",
            "pivotAndGroundLine": "pending",
            "transparentPadding": "pending",
            "noBackgroundOrHalos": "pending",
            "deviceSizeReadability": "pending",
        },
        "blockingGaps": [
            "Paint a real idle_breath_in pose; the nearest trial frame is only a reference.",
            "Paint an attentive look_profile pose; the extracted turnaround profile is only a design reference.",
            "Manually retouch all four usable candidates before approval.",
        ],
    }


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    generated: list[Image.Image] = []
    for index, candidate in enumerate(CANDIDATES):
        if candidate["sourceType"] == "turnaround_profile_extract":
            frame = normalize_subject(extract_turnaround_profile(), bool(candidate["flipHorizontal"]))
        else:
            source = TRIAL_FRAME_DIR / Path(str(candidate["source"])).name
            frame = normalize_existing(Image.open(source), bool(candidate["flipHorizontal"]))
        output = FRAME_DIR / f"lumi_{index:02d}_{candidate['id']}.png"
        frame.save(output, optimize=True)
        generated.append(frame)

    font_title = get_font(34, True)
    font_subtitle = get_font(17)
    font_card = get_font(25, True)
    font_small = get_font(16)
    font_status = get_font(14, True)
    rows = 3
    board_height = HEADER_HEIGHT + MARGIN + rows * CARD_HEIGHT + (rows - 1) * CARD_GAP + MARGIN
    board = Image.new("RGB", (COLUMNS * CARD_WIDTH + (COLUMNS + 1) * MARGIN + CARD_GAP, board_height), (16, 25, 34))
    draw = ImageDraw.Draw(board)
    draw.text((MARGIN, 28), "Lumi Master Pose Review v2", font=font_title, fill=(255, 244, 221))
    draw.text((MARGIN, 82), "Normalized candidates from trial art and turnaround references.", font=font_subtitle, fill=(184, 197, 206))
    draw.text((MARGIN, 116), "No pose is production-approved; missing art is labeled instead of fabricated.", font=font_subtitle, fill=(85, 226, 211))

    for index, candidate in enumerate(CANDIDATES):
        column = index % COLUMNS
        row = index // COLUMNS
        x = MARGIN + column * (CARD_WIDTH + CARD_GAP)
        y = HEADER_HEIGHT + row * (CARD_HEIGHT + CARD_GAP)
        draw.rounded_rectangle((x, y, x + CARD_WIDTH, y + CARD_HEIGHT), radius=18, fill=(24, 38, 49), outline=(65, 96, 107), width=2)
        draw.text((x + 22, y + 18), candidate["id"], font=font_card, fill=(255, 195, 106))
        status_fill = (255, 173, 158) if candidate["status"] == "manual_paint_required" else (255, 195, 106)
        draw.rounded_rectangle((x + CARD_WIDTH - 250, y + 20, x + CARD_WIDTH - 22, y + 50), radius=12, fill=(65, 39, 43))
        draw.text((x + CARD_WIDTH - 236, y + 27), candidate["status"].replace("_", " ").upper(), font=font_status, fill=status_fill)
        image = generated[index].resize((360, 360), Image.Resampling.LANCZOS)
        image_x = x + (CARD_WIDTH - 360) // 2
        image_y = y + 66
        composite = Image.new("RGBA", image.size, (38, 53, 64, 255))
        composite.alpha_composite(image)
        board.paste(composite.convert("RGB"), (image_x, image_y))
        draw.line((image_x, image_y + round(368 * 360 / 384), image_x + 360, image_y + round(368 * 360 / 384)), fill=(255, 195, 106), width=1)
        draw.text((x + 22, y + 444), f"source: {candidate['sourceType']}", font=font_small, fill=(184, 197, 206))
        draw_wrapped(draw, candidate["note"], x + 22, y + 474, font_small, (255, 244, 221), CARD_WIDTH - 44)

    board.save(BOARD_PATH, optimize=True)
    MANIFEST_PATH.write_text(json.dumps(build_manifest(), indent=2) + "\n", encoding="utf-8")
    print(f"LUMI_MASTER_POSE_SET_BUILT board={BOARD_PATH} frames={len(generated)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
