#!/usr/bin/env python3
"""Normaliza los PREVIEWS de los pets (pet_*.png) para que todos tengan el
mismo contenido visible que pet_moki.png (referencia): contenido al 85.9% del
alto del canvas, centrado vertical y horizontalmente.

Composición de referencia (medida en pet_moki.png, 512x512):
  content bbox = (76, 36, 435, 476) -> top=0.070, bottom=0.930, center_y=0.500
  ocupación alto = 440/512 = 0.859

Uso: python3 tools/normalize_previews.py [--dry-run]
Backup automático en /tmp/pixelpals_previews_backup/
"""
import os
import shutil
import sys
from PIL import Image

BASE = "app/src/main/res"
TARGET_OCC = 0.859  # fracción del alto del canvas ocupada por el contenido
CENTER_Y = 0.5      # centro vertical del contenido
BACKUP = "/tmp/pixelpals_previews_backup"

# Previews por ubicación (los de drawable-nodpi y drawable-xxhdpi)
FILES = [
    "drawable-nodpi/pet_angel.png",
    "drawable-nodpi/pet_ginger.png",
    "drawable-nodpi/pet_moki.png",
    "drawable-xxhdpi/pet_bloop.png",
    "drawable-xxhdpi/pet_corgi.png",
    "drawable-xxhdpi/pet_diablillo.png",
    "drawable-xxhdpi/pet_nube_michi.png",
    "drawable-xxhdpi/pet_patito.png",
]


def normalize(path: str, dry_run: bool) -> None:
    im = Image.open(path)
    if im.mode != "RGBA":
        im = im.convert("RGBA")
    w, h = im.size
    alpha = im.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        print(f"  {path}: SIN contenido, se omite")
        return
    x0, y0, x1, y1 = bbox
    cw, ch = x1 - x0, y1 - y0
    occ = ch / h
    if abs(occ - TARGET_OCC) < 0.005:
        print(f"  {path}: ya normalizado (occ={occ:.3f}), se omite")
        return

    # Escala para que el contenido ocupe TARGET_OCC del alto del canvas
    scale = (h * TARGET_OCC) / ch
    new_w, new_h = max(1, round(cw * scale)), max(1, round(ch * scale))
    content = im.crop(bbox).resize((new_w, new_h), Image.Resampling.NEAREST)

    # Posición: centrado vertical (centro en CENTER_Y) y centrado horizontal
    dst_x = (w - new_w) // 2
    dst_y = round(h * CENTER_Y) - new_h // 2
    # Clamp para que no salga del canvas
    dst_y = max(0, min(dst_y, h - new_h))

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(content, (dst_x, dst_y), content)
    if dry_run:
        print(f"  [dry-run] {path}: occ {occ:.3f} -> {TARGET_OCC:.3f} "
              f"content {cw}x{ch} -> {new_w}x{new_h} at ({dst_x},{dst_y})")
    else:
        out.save(path)
        print(f"  {path}: occ {occ:.3f} -> {TARGET_OCC:.3f} (bbox nuevo "
              f"({dst_x},{dst_y})-({dst_x+new_w},{dst_y+new_h}))")


def main() -> None:
    dry_run = "--dry-run" in sys.argv
    os.makedirs(BACKUP, exist_ok=True)
    changed = 0
    for rel in FILES:
        path = os.path.join(BASE, rel)
        if not os.path.exists(path):
            print(f"  {path}: NO EXISTE")
            continue
        if not dry_run:
            shutil.copy2(path, os.path.join(BACKUP, os.path.basename(rel)))
        normalize(path, dry_run)
        changed += 1
    print(f"\n{changed} previews revisados. Backup en {BACKUP}")
    print("Recordatorio: pet_jelly.xml (layer-list -> jelly_0) se reemplaza "
          "por pet_jelly.png normalizado (borrar el XML).")


if __name__ == "__main__":
    main()
