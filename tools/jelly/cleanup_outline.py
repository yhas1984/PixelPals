"""Alpha-only removal of Jelly's exterior white sticker outline."""
from __future__ import annotations
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from tools.pet_pipeline import _components


def remove_outline(image: Image.Image, *, care_index: int | None = None) -> Image.Image:
    if image.mode != 'RGBA':
        raise ValueError('Jelly outline cleanup requires RGBA')
    if care_index is not None and (image.size != (256, 256) or not 0 <= care_index < 24):
        raise ValueError('Jelly care cleanup requires a placed 256px cell and index 0..23')
    rgba = np.array(image)
    rgb = rgba[:, :, :3].astype(int)
    # Enclosed specular reflections remain untouched: only pale pixels reached
    # from transparent exterior are eligible. Green gel is the boundary.
    neutral = (rgb.max(axis=2) - rgb.min(axis=2) <= 35) & (rgb.min(axis=2) >= 155)
    if care_index == 19:
        # Dream bubbles are separate authored objects, not the body's border.
        neutral[104:157, 204:231] = False
    candidate = neutral | (rgba[:, :, 3] == 0)
    padded = np.pad(candidate.astype(np.uint8) * 255, 1, constant_values=255)
    mask = Image.fromarray(padded).copy()
    ImageDraw.floodfill(mask, (0, 0), 128)
    exterior = np.asarray(mask)[1:-1, 1:-1] == 128
    rgba[exterior, 3] = 0
    # Some green-tinted antialiasing from the white stroke remains connected
    # to the body by a one-pixel bridge. A one-pixel silhouette opening removes
    # these hairlines without widening the color threshold into gel highlights.
    solid = Image.fromarray((rgba[:, :, 3] > 0).astype(np.uint8) * 255)
    opened = np.asarray(solid.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))) > 0
    rgba[~opened, 3] = 0
    # The old stroke has a second antialiased fringe, separated from the gel
    # by the pixels removed above. Keep the connected body, not that fringe.
    components = _components(rgba[:, :, 3] > 0)
    if not components:
        raise ValueError('Jelly cleanup removed the whole sprite')
    keep = np.zeros(rgba.shape[:2], dtype=bool)
    body = max(components, key=len)
    yy, xx = np.asarray(body).T
    keep[yy, xx] = True
    if care_index == 19:
        # Keep both authored dream bubbles, including their antialiasing.
        # Seed them explicitly so a detached remnant of the body stroke is
        # never mistaken for a third dream bubble.
        for component in components:
            if (120, 216) in component or (145, 216) in component:
                yy, xx = np.asarray(component).T
                keep[yy, xx] = True
    rgba[~keep, 3] = 0
    return Image.fromarray(rgba)
