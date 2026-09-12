"""Compose generated sleepy expressions on Jelly's unchanged, transparent body.

The generated sheet's opaque green backdrop and body are deliberately unused.
Only its three facial features are extracted. Camera, alpha, reflections and
material come from the reviewed medicine attention pose, shared by every cell.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw
from tools.pet_pipeline import _components

HERE = Path(__file__).resolve().parent
RAW = HERE / 'raw/rest-expressions-2026-09-12'
FACE_SCALE = .36
CENTERS = ((97.5, 170), (167, 170), (132.5, 183))


def clear_face(original: Image.Image) -> Image.Image:
    pixels = np.asarray(original).copy()
    mask = np.zeros((256, 256), dtype=bool)
    for left, top, right, bottom in ((85, 157, 110, 182), (154, 157, 180, 182), (113, 175, 152, 191)):
        mask[top:bottom, left:right] = True
    # Harmonic interpolation from the unchanged green material at each local
    # boundary. The small facial regions are opaque and far from the silhouette.
    rgb = pixels[:, :, :3].astype(np.float64)
    rgb[mask] = (125, 214, 59)
    for _ in range(1200):
        average = (np.roll(rgb, 1, 0) + np.roll(rgb, -1, 0) +
                   np.roll(rgb, 1, 1) + np.roll(rgb, -1, 1)) * .25
        rgb[mask] = average[mask]
    pixels[mask, :3] = np.clip(np.rint(rgb[mask]), 0, 255).astype(np.uint8)
    return Image.fromarray(pixels)


def facial_features(cell: Image.Image) -> list[Image.Image]:
    rgba = np.asarray(cell)
    dark = (rgba[:, :, :3] < 110).all(axis=2)
    dark[:180] = False
    dark[365:] = False
    dark[:, :120] = False
    dark[:, 405:] = False
    components = sorted(_components(dark), key=len, reverse=True)[:3]
    assert len(components) == 3 and all(len(points) > 300 for points in components)
    # Mouth is below both eyes, whose horizontal positions identify left/right.
    components.sort(key=lambda points: np.asarray(points)[:, 0].mean())
    eyes = sorted(components[:2], key=lambda points: np.asarray(points)[:, 1].mean())
    result = []
    for points in eyes + [components[2]]:
        y, x = np.asarray(points).T
        bounds = (int(x.min()) - 2, int(y.min()) - 2, int(x.max()) + 3, int(y.max()) + 3)
        feature = np.array(cell.crop(bounds))
        mask = np.zeros(feature.shape[:2], dtype=np.uint8)
        mask[y - bounds[1], x - bounds[0]] = 255
        # Keep enclosed eye reflections, but discard the opaque source backdrop.
        filled = Image.fromarray(mask).copy()
        ImageDraw.floodfill(filled, (0, 0), 128)
        mask = np.where(np.asarray(filled) == 128, 0, 255).astype(np.uint8)
        feature[:, :, 3] = mask
        size = tuple(round(value * FACE_SCALE) for value in (feature.shape[1], feature.shape[0]))
        result.append(Image.fromarray(feature).resize(size, Image.Resampling.LANCZOS))
    return result


def prepare() -> None:
    generated = Image.open(RAW / 'generated.png').convert('RGBA')
    assert generated.size == (1024, 1536)
    original = Image.open(HERE / 'clean/medicine_0.png').convert('RGBA')
    base = clear_face(original)
    base.save(RAW / 'body-without-face.png', optimize=True)
    review = Image.new('RGBA', (7 * 256, 256), '#292536')
    review.alpha_composite(original)
    report = []
    for index in range(6):
        box = (index % 2 * 512, index // 2 * 512, (index % 2 + 1) * 512, (index // 2 + 1) * 512)
        pose = base.copy()
        for feature, center in zip(facial_features(generated.crop(box)), CENTERS):
            pose.alpha_composite(feature, (round(center[0] - feature.width / 2), round(center[1] - feature.height / 2)))
        pose.putalpha(original.getchannel('A'))
        assert np.array_equal(np.asarray(pose)[:, :, 3], np.asarray(original)[:, :, 3])
        pose.save(HERE / 'clean' / f'rest_{index}.png', optimize=True)
        review.alpha_composite(pose, ((index + 1) * 256, 0))
        report.append({'frame': index + 24, 'featureScale': FACE_SCALE, 'centers': CENTERS,
                       'alphaIdenticalTo': 'medicine_0.png'})
    review.save(RAW / 'prepared-review.png', optimize=True)
    (RAW / 'prepared.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    prepare()
