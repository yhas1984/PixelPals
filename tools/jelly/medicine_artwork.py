"""Place the reviewed medicine expressions without altering other care rows."""
from pathlib import Path
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
MOUTHS = ((133, 185), (131, 181.5), (133, 181), (133, 185))


def apply_medicine(atlas: Image.Image, anchors: list[dict] | None = None) -> None:
    for index, mouth in enumerate(MOUTHS):
        pose = Image.open(HERE / 'clean' / f'medicine_{index}.png').convert('RGBA')
        if pose.size != (256, 256):
            raise ValueError('Jelly medicine requires a fixed 256px camera')
        atlas.paste(pose, (index * 256, 5 * 256))
        if anchors is not None:
            alpha = np.asarray(pose)[:, :, 3]
            rows = np.where((alpha >= 32).any(axis=1))[0]
            anchors[20 + index] = {
                'mouth': [value / 256 for value in mouth],
                'head': [128 / 256, 130 / 256],
                'body': [128 / 256, 180 / 256],
                'ground': [128 / 256, (int(rows[-1]) + 1) / 256],
            }
