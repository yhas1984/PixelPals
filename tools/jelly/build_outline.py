"""Reproduce Jelly's outline cleanup from immutable sources.

Run `python3 -m tools.jelly.build_outline` for review, or add --apply to package.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from tools.jelly.cleanup_outline import remove_outline
from tools.jelly.medicine_artwork import apply_medicine
from tools.jelly.rest_artwork import apply_rest

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / 'tools/jelly'
NAMES = [f'jelly_{i}.png' for i in range(8)] + ['pet_jelly.png']


def build(apply: bool = False) -> None:
    review = HERE / 'review'
    review.mkdir(exist_ok=True)
    report = []
    board = Image.new('RGBA', (1536, 5 * 430), '#292536')
    for i, name in enumerate(NAMES):
        source = HERE / 'source' / name
        original = Image.open(source).convert('RGBA')
        clean = remove_outline(original)
        destination = review / name
        clean.save(destination, optimize=True)
        if apply:
            (ROOT / 'app/src/main/res/drawable-nodpi' / name).write_bytes(destination.read_bytes())
        x, y = i % 2 * 768, i // 2 * 430
        ImageDraw.Draw(board).text((x + 10, y + 8), f'{name}: antes / despues', fill='white')
        board.alpha_composite(original.resize((384, 384)), (x, y + 30))
        board.alpha_composite(clean.resize((384, 384)), (x + 384, y + 30))
        before, after = np.array(original), np.array(clean)
        report.append({'file': name, 'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                       'cleanSha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
                       'removedPixels': int(((before[:, :, 3] > 0) & (after[:, :, 3] == 0)).sum()),
                       'bounds': clean.getchannel('A').getbbox(),
                       'rgbIdentical': bool(np.array_equal(before[:, :, :3], after[:, :, :3]))})
    board.save(review / 'legacy-review.png', optimize=True)
    original = Image.open(HERE / 'source/care_v1.png').convert('RGBA')
    clean = Image.new('RGBA', (1024, 2048))
    clean.paste(original, (0, 0))
    for index in range(24):
        box = (index % 4 * 256, index // 4 * 256, (index % 4 + 1) * 256, (index // 4 + 1) * 256)
        clean.paste(remove_outline(original.crop(box), care_index=index), box[:2])
    apply_medicine(clean)
    apply_rest(clean)
    clean.save(review / 'care_v1.png', optimize=True)
    board = Image.new('RGBA', (2048, 2048), '#292536')
    board.alpha_composite(original, (0, 0))
    board.alpha_composite(clean, (1024, 0))
    board.save(review / 'care-review.png', optimize=True)
    if apply:
        from tools.care.build_atlases import build as build_care
        care_root = ROOT / 'tools/care'
        entry = build_care('jelly', json.loads((care_root / 'anchors.json').read_text()))
        packaged = Image.open(ROOT / 'app/src/carePreview/assets/pets/jelly/care_v1.png').convert('RGBA')
        assert np.array_equal(np.array(packaged), np.array(clean)), 'Care source placement changed'
        care_report = json.loads((care_root / 'build_report.json').read_text())
        assert sum(item['pet'] == 'jelly' for item in care_report) == 1
        care_report = [entry if item['pet'] == 'jelly' else item for item in care_report]
        (care_root / 'build_report.json').write_text(json.dumps(care_report, indent=2) + '\n')
    (review / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'Jelly: {len(NAMES)} resources + 30 care frames; apply={apply}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    build(parser.parse_args().apply)
