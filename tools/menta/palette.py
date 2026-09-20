"""Prepare the reviewed palette edit without changing its camera or contact grid."""
from pathlib import Path

import numpy as np
from PIL import Image

SOURCE = Path(__file__).parent / "raw/palette-2026-09-20/generated.png"


def care_palette() -> Image.Image:
    rgba = np.array(Image.open(SOURCE).convert("RGBA"))
    if rgba.shape != (1536, 1024, 4):
        raise ValueError("Menta palette edit must retain the 24-cell care camera")
    for index in range(24):
        cell = rgba[index // 4 * 256:(index // 4 + 1) * 256,
                    index % 4 * 256:(index % 4 + 1) * 256]
        # Preserve the authored transparent gutter; only faint isolated alpha
        # specks occur here (the body remains inside the original padding).
        cell[:15] = 0
        cell[-15:] = 0
        cell[:, :15] = 0
        cell[:, -15:] = 0
    # Generated alpha is valid. Discard hidden RGB so tools that ignore alpha
    # cannot mistake the invisible backing colour for painted scenery.
    rgba[rgba[:, :, 3] == 0] = 0
    return Image.fromarray(rgba)
