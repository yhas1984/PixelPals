"""Prepare four reviewed medicine poses with one shared source camera."""
from pathlib import Path
import json

import numpy as np
from PIL import Image, ImageDraw
from tools.pet_pipeline import _components

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / 'tools/jelly'
RAW = HERE / 'raw/medicine-expressions-2026-09-12'
CELL = 256


def prepare():
    image = Image.open(RAW / 'generated.png').convert('RGBA')
    half = image.width // 2
    assert image.size == (1254, 1254)
    clean = HERE / 'clean'
    clean.mkdir(exist_ok=True)
    report = []
    sheet = Image.new('RGBA', (CELL * 5, CELL), '#292536')
    original = Image.open(ROOT / 'app/src/main/res/drawable-nodpi/jelly_0.png').convert('RGBA')
    original = original.resize((239, 239), Image.Resampling.LANCZOS)
    sheet.alpha_composite(original, (8, 7))
    for index in range(4):
        cell = image.crop((index % 2 * half, index // 2 * half,
                           (index % 2 + 1) * half, (index // 2 + 1) * half))
        rgba = np.array(cell)
        components = _components(rgba[:, :, 3] >= 128)
        y, x = np.asarray(max(components, key=len)).T
        mask = np.zeros(rgba.shape[:2], dtype=bool)
        mask[y, x] = True
        # Keep the authored antialiasing immediately around the body; discard
        # detached generated flecks outside it, never recrop a frame to fit.
        padded = np.pad(mask, 1)
        edge = np.zeros_like(mask)
        for dy in range(3):
            for dx in range(3):
                edge |= padded[dy:dy + half, dx:dx + half]
        rgba[~edge, 3] = 0
        body_bottom = int(y.max()) + 1
        # One scale for all four cells. Translation only plants each authored
        # base on the same ground and cancels the sheet's row gutters.
        scaled = Image.fromarray(rgba).resize((202, 202), Image.Resampling.LANCZOS)
        center_x = (int(x.min()) + int(x.max()) + 1) / 2
        offset = (round(128 - center_x * 202 / half), 230 - round(body_bottom * 202 / half))
        posed = Image.new('RGBA', (CELL, CELL))
        posed.alpha_composite(scaled, offset)
        posed.save(clean / f'medicine_{index}.png', optimize=True)
        sheet.alpha_composite(posed, ((index + 1) * CELL, 0))
        visible = np.array(posed)[:, :, 3] >= 32
        yy, xx = np.where(visible)
        report.append({'index': index, 'scale': 202 / half, 'offset': offset,
                       'bounds': [int(xx.min()), int(yy.min()), int(xx.max()) + 1, int(yy.max()) + 1],
                       'area': int(visible.sum())})
    ImageDraw.Draw(sheet).text((8, 8), 'Original / medicina: preparada, acepta, traga, aliviada', fill='white')
    sheet.save(RAW / 'prepared-review.png', optimize=True)
    (RAW / 'prepared.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    prepare()
