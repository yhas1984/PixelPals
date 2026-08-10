#!/usr/bin/env python3
"""
Normaliza frames sueltos de PixelPals (drawable-nodpi).

Problema detectado: los frames de un mismo pet tienen el sprite descentrado
(dx != 0) y bases verticales distintas (flotan/crecen al animar), lo que
hace que la mascota "baile" al cambiar de frame.

Solución:
  1. Bbox del contenido no transparente (alpha > umbral).
  2. Centrar horizontalmente el contenido en el frame (dx -> 0).
  3. Alinear la base (borde inferior del bbox) a la base MÁXIMA del set,
     manteniendo la escala relativa de cada frame (plumas/saltos intactos).
  4. Escribir PNG 768x768 RGBA (mismo formato que el original).

Uso: python3 tools/normalize_frames.py [--dry-run] [--sets corgi,patito]
"""
import argparse
import glob
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FRAME_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
BACKUP_DIR = "/tmp/pixelpals_frames_backup"

SETS = {
    "corgi": "corgi_*.png",
    "patito": "patito_*.png",
    "diablillo": "diablillo_*.png",
    "jelly": "jelly_*.png",
    "gato": "gato_*.png",
    "fantasma": "fantasma_*.png",
}

# Los pets flotantes (nube, fantasma) no se apoyan en un suelo: su contenido
# se CENTRA verticalmente. Los terrestres se anclan a la base máxima del set.
FLOAT_SETS = {"gato", "fantasma"}

ALPHA_THRESHOLD = 8


def bbox_of(im: Image.Image):
    alpha = im.convert("RGBA").getchannel("A")
    return alpha.point(lambda a: 255 if a > ALPHA_THRESHOLD else 0).getbbox()


def normalize_set(prefix: str, dry_run: bool = False) -> dict:
    pattern = os.path.join(FRAME_DIR, SETS[prefix])
    files = sorted(glob.glob(pattern))
    if not files:
        return {"set": prefix, "error": "sin archivos"}

    # 1) Bboxes actuales
    boxes = {}
    for f in files:
        with Image.open(f) as im:
            boxes[f] = bbox_of(im)

    # 2) Posición vertical objetivo.
    #    - Terrestres: base = base MÁXIMA del set (el frame más "bajo").
    #    - Flotantes: contenido centrado verticalmente (sin suelo).
    float_set = prefix in FLOAT_SETS
    bases = [b[3] for b in boxes.values() if b is not None]
    if not bases:
        return {"set": prefix, "error": "todos vacíos"}
    target_base = max(bases)

    report = {"set": prefix, "files": []}
    for f in files:
        b = boxes[f]
        if b is None:
            report["files"].append({"file": os.path.basename(f), "note": "vacío, sin tocar"})
            continue
        x0, y0, x1, y1 = b
        bw, bh = x1 - x0, y1 - y0

        with Image.open(f) as im:
            w, h = im.size
            rgba = im.convert("RGBA")
            content = rgba.crop((x0, y0, x1, y1))

            # Nuevo bbox: centrado en X; vertical según el tipo de set.
            new_x0 = (w - bw) // 2
            if float_set:
                new_y0 = (h - bh) // 2
                new_y1 = new_y0 + bh
            else:
                new_y1 = target_base
                new_y0 = new_y1 - bh

            canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            canvas.paste(content, (new_x0, new_y0))
            report["files"].append({
                "file": os.path.basename(f),
                "old_bbox": (x0, y0, x1, y1),
                "new_bbox": (new_x0, new_y0, new_x0 + bw, new_y1),
                "dx": round((x0 + x1) / 2 - w / 2, 1),
                "base_delta": y1 - target_base,
            })
            if dry_run:
                continue
            # Backup solo la primera vez
            os.makedirs(BACKUP_DIR, exist_ok=True)
            bak = os.path.join(BACKUP_DIR, os.path.basename(f))
            if not os.path.exists(bak):
                shutil.copy2(f, bak)
            canvas.save(f, "PNG")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="solo informar, no escribir")
    parser.add_argument("--sets", default=",".join(SETS), help="sets a procesar (por defecto todos)")
    args = parser.parse_args()

    selected = [s.strip() for s in args.sets.split(",") if s.strip() in SETS]
    ok, errors = 0, 0
    for prefix in selected:
        report = normalize_set(prefix, dry_run=args.dry_run)
        print(f"\n=== {report['set']} ===")
        if "error" in report:
            print(f"  ERROR: {report['error']}")
            errors += 1
            continue
        for entry in report["files"]:
            if "note" in entry:
                print(f"  {entry['file']}: {entry['note']}")
            else:
                print(f"  {entry['file']}: dx {entry['dx']:+.0f}px  base_delta {entry['base_delta']:+d}px")
        ok += 1
    print(f"\n{len(selected)} sets procesados ({ok} ok, {errors} error). Backup en {BACKUP_DIR}")
    if args.dry_run:
        print("MODO DRY-RUN: no se escribió nada.")


if __name__ == "__main__":
    sys.exit(main())
