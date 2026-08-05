#!/usr/bin/env python3
"""Trim accessory atlas frames to their real content (centered).

Many atlases were drawn with content off-center in the 384x384 frame
(e.g. viking_helmet touches the left edge). This script crops each
atlas to the union bbox of all frames (with a small margin) and updates
frameWidth/frameHeight in the catalog so the sprite renders centered
both in the store preview and on the pet.

Usage:
    python3 tools/trim_accessory_atlases.py
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/accessories_catalog.json"
MARGIN = 8  # px de margen alrededor del contenido


def frame_content_bbox(frame: Image.Image, alpha_threshold: int = 40) -> tuple | None:
    """BBox del contenido visible (ignora bordes suaves con alpha bajo)."""
    px = frame.load()
    w, h = frame.size
    x0, y0, x1, y1 = w, h, 0, 0
    step = 2
    for y in range(0, h, step):
        for x in range(0, w, step):
            if px[x, y][3] > alpha_threshold:
                if x < x0: x0 = x
                if y < y0: y0 = y
                if x > x1: x1 = x
                if y > y1: y1 = y
    if x1 <= x0 or y1 <= y0:
        return None
    # Expandir el bbox para cubrir los pasos saltados
    return max(x0 - step, 0), max(y0 - step, 0), min(x1 + step, w), min(y1 + step, h)


def content_bbox(img: Image.Image, frame_w: int, frame_h: int, cols: int) -> tuple:
    """BBox unión del contenido de todos los frames del atlas."""
    ux0, uy0, ux1, uy1 = frame_w, frame_h, 0, 0
    rows = img.height // frame_h
    for c in range(cols):
        for r in range(rows):
            frame = img.crop((c * frame_w, r * frame_h, (c + 1) * frame_w, (r + 1) * frame_h))
            bbox = frame_content_bbox(frame)
            if bbox:
                ux0 = min(ux0, bbox[0])
                uy0 = min(uy0, bbox[1])
                ux1 = max(ux1, bbox[2])
                uy1 = max(uy1, bbox[3])
    if ux1 <= ux0 or uy1 <= uy0:
        return None
    return ux0, uy0, ux1, uy1


def main() -> None:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    updated = 0

    for item in catalog["accessories"]:
        s = item.get("sprite")
        if not s:
            continue
        path = ROOT / "app/src/main/assets" / s["atlasPath"]
        if not path.exists():
            print(f"  SKIP {item['id']} (no existe)")
            continue

        img = Image.open(path).convert("RGBA")
        fw, fh = s["frameWidth"], s["frameHeight"]
        cols = s["columns"]
        bbox = content_bbox(img, fw, fh, cols)
        if not bbox:
            print(f"  SKIP {item['id']} (vacío)")
            continue

        ux0, uy0, ux1, uy1 = bbox
        # Margen
        x0 = max(ux0 - MARGIN, 0)
        y0 = max(uy0 - MARGIN, 0)
        x1 = min(ux1 + MARGIN, img.width)
        y1 = min(uy1 + MARGIN, img.height)
        nw = x1 - x0
        nh = y1 - y0
        if nw <= 0 or nh <= 0:
            continue

        cropped = img.crop((x0, y0, x1, y1))
        cropped.save(path, optimize=True)

        s["frameWidth"] = nw
        s["frameHeight"] = nh
        print(f"  {item['id']:20s} {fw}x{fh} -> {nw}x{nh} (bbox {bbox})")
        updated += 1

    CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"\nRecortados {updated} atlas")


if __name__ == "__main__":
    main()
