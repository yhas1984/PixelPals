from pathlib import Path
import unittest

import numpy as np
from PIL import Image

from tools.bloop.cleanup import clean_care_cell


ROOT = Path(__file__).resolve().parents[2]
CARE_SOURCE = ROOT / "tools/bloop/source/care_v1.png"
CARE_PACKAGED = ROOT / "app/src/carePreview/assets/pets/bloop/care_v1.png"
LEGACY = ("fantasma_1", "fantasma_2", "fantasma_3", "fantasma_4", "fantasma_5", "fantasma_7", "fantasma_8", "pet_bloop")


def rgba(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("RGBA"))


def cleaned_atlas(source: np.ndarray) -> np.ndarray:
    result = source.copy()
    for index in range(24):
        row, column = divmod(index, 4)
        cell = Image.fromarray(source[row * 256:(row + 1) * 256, column * 256:(column + 1) * 256])
        result[row * 256:(row + 1) * 256, column * 256:(column + 1) * 256] = np.asarray(clean_care_cell(cell, index))
    return result


def test_care_cleanup_is_reproducible_and_body_safe() -> None:
    source = rgba(CARE_SOURCE)
    packaged = rgba(CARE_PACKAGED)
    generated = cleaned_atlas(source)
    assert source.shape == packaged.shape == (1536, 1024, 4)
    assert np.array_equal(generated, packaged)
    assert np.array_equal(generated[:, :, :3], source[:, :, :3])
    assert np.all(generated[:, :, 3] <= source[:, :, 3])
    for index in (1, 2, 7, 18, 19):
        row, column = divmod(index, 4)
        ys = slice(row * 256, (row + 1) * 256)
        xs = slice(column * 256, (column + 1) * 256)
        assert np.array_equal(generated[ys, xs], source[ys, xs])
    for index in range(24):
        row, column = divmod(index, 4)
        y0, y1 = row * 256 + 90, row * 256 + 230
        x0, x1 = column * 256 + 100, column * 256 + 200
        assert np.array_equal(generated[y0:y1, x0:x1], source[y0:y1, x0:x1])


def test_legacy_cleanup_preserves_dimensions_and_rgb() -> None:
    for name in LEGACY:
        source = rgba(ROOT / "tools/bloop/source" / f"{name}.png")
        packaged_dir = "drawable-nodpi" if name.startswith("fantasma") else "drawable-xxhdpi"
        packaged = rgba(ROOT / "app/src/main/res" / packaged_dir / f"{name}.png")
        assert packaged.shape == source.shape
        assert np.array_equal(packaged[:, :, :3], source[:, :, :3])
        assert np.all(packaged[:, :, 3] <= source[:, :, 3])


class BloopCleanupTests(unittest.TestCase):
    def test_care_artwork(self):
        test_care_cleanup_is_reproducible_and_body_safe()

    def test_legacy_artwork(self):
        test_legacy_cleanup_preserves_dimensions_and_rgb()


if __name__ == "__main__":
    unittest.main()
