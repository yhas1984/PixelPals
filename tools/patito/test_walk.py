"""Regression tests for the authorised Patito walk-sheet extraction.

These checks use the source artwork and fixed semantic regions, rather than
reconstructing expectations from the cleanup mask itself.
"""
from __future__ import annotations

import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools.patito.build_walk import CELL, COLS, ROWS, SOURCE, clean_sheet


ROOT = Path(__file__).resolve().parents[2]
REVIEW = ROOT / "tools/patito/review"


def _array(image: Image.Image) -> np.ndarray:
    return np.asarray(image.convert("RGBA"))


def _cell(sheet: np.ndarray, index: int) -> np.ndarray:
    row, col = divmod(index, COLS)
    return sheet[row * CELL : (row + 1) * CELL, col * CELL : (col + 1) * CELL]


class WalkExtractionTest(unittest.TestCase):
    def test_six_review_files_are_exact_512_rgba_crops(self) -> None:
        source = _array(Image.open(SOURCE))
        cleaned = _array(clean_sheet(Image.open(SOURCE)))
        self.assertEqual(source.shape, (ROWS * CELL, COLS * CELL, 4))
        self.assertEqual(cleaned.shape, source.shape)

        for index in range(COLS * ROWS):
            path = REVIEW / f"walk-{index}.png"
            with self.subTest(frame=index):
                self.assertTrue(path.is_file())
                output = Image.open(path)
                self.assertEqual(output.mode, "RGBA")
                self.assertEqual(output.size, (CELL, CELL))
                self.assertTrue(np.array_equal(_array(output), _cell(cleaned, index)))

    def test_cleanup_only_reduces_alpha_and_preserves_opaque_rgb(self) -> None:
        source = _array(Image.open(SOURCE))
        cleaned = _array(clean_sheet(Image.open(SOURCE)))
        self.assertTrue(np.all(cleaned[:, :, 3] <= source[:, :, 3]))
        kept = cleaned[:, :, 3] > 0
        self.assertTrue(np.array_equal(cleaned[:, :, :3][kept], source[:, :, :3][kept]))

    def test_connected_neutral_border_is_removed(self) -> None:
        cleaned = _array(clean_sheet(Image.open(SOURCE)))
        for index in range(COLS * ROWS):
            with self.subTest(frame=index):
                cell = _cell(cleaned, index)
                self.assertEqual(int(cell[0, 0, 3]), 0)
                border = np.concatenate(
                    (cell[:16, :, 3].ravel(), cell[-16:, :, 3].ravel(),
                     cell[:, :16, 3].ravel(), cell[:, -16:, 3].ravel())
                )
                self.assertLessEqual(int(np.count_nonzero(border)), 16)

    def test_dark_blue_scarf_pixels_survive(self) -> None:
        source = _array(Image.open(SOURCE))
        cleaned = _array(clean_sheet(Image.open(SOURCE)))
        # Fixed, reviewed interior points in the scarf (local cell coordinates).
        # Keeping the coordinates and source RGB explicit makes this regression
        # independent of whichever neutral-background mask is being evaluated.
        scarf_points = (
            ((300, 280), (310, 290), (320, 260)),
            ((320, 260), (320, 270), (320, 290)),
            ((300, 260), (330, 270), (320, 290)),
            ((290, 260), (320, 270), (310, 290)),
            ((300, 270), (310, 270), (300, 290)),
            ((280, 260), (300, 260), (320, 290)),
        )
        for index in range(COLS * ROWS):
            original = _cell(source, index)
            result = _cell(cleaned, index)
            with self.subTest(frame=index):
                for x, y in scarf_points[index]:
                    self.assertGreaterEqual(int(original[y, x, 2]), int(original[y, x, 0]) + 15)
                    self.assertEqual(int(original[y, x, 3]), 255)
                    self.assertEqual(int(result[y, x, 3]), 255)
                    self.assertTrue(np.array_equal(result[y, x, :3], original[y, x, :3]))

    def test_enclosed_eye_whites_survive(self) -> None:
        source = _array(Image.open(SOURCE))
        cleaned = _array(clean_sheet(Image.open(SOURCE)))
        for index in range(COLS * ROWS):
            original = _cell(source, index)
            result = _cell(cleaned, index)
            roi = original[60:220, 180:440, :3]
            white = (roi.min(axis=2) >= 240) & ((roi.max(axis=2) - roi.min(axis=2)) <= 25)
            dark = roi.max(axis=2) < 110
            near_dark = np.zeros_like(dark)
            for dy in range(-4, 5):
                for dx in range(-4, 5):
                    near_dark[max(0, dy):min(160, 160 + dy), max(0, dx):min(260, 260 + dx)] |= dark[
                        max(0, -dy):min(160, 160 - dy), max(0, -dx):min(260, 260 - dx)
                    ]
            eye_white = white & near_dark
            with self.subTest(frame=index):
                self.assertGreater(int(eye_white.sum()), 50)
                self.assertTrue(np.all(result[60:220, 180:440, 3][eye_white] > 0))
                self.assertTrue(np.array_equal(
                    result[60:220, 180:440, :3][eye_white],
                    roi[eye_white],
                ))


if __name__ == "__main__":
    unittest.main()
