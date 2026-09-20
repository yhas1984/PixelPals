"""Regression checks for the reviewed Diablillo RGBA care importer."""
from __future__ import annotations

import hashlib
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools.diablillo.import_care import CELL, FRAME_COUNT, import_care_atlas


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/diablillo/raw/care-redesign-palette.png"


class DiablilloCareImportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.imported = import_care_atlas(SOURCE)

    @staticmethod
    def cell(atlas: np.ndarray, index: int) -> np.ndarray:
        left, top = index % 4 * CELL, index // 4 * CELL
        return atlas[top:top + CELL, left:left + CELL]

    def test_import_is_reproducible_and_keeps_rgba(self) -> None:
        again = import_care_atlas(SOURCE)
        np.testing.assert_array_equal(np.asarray(self.imported.image), np.asarray(again.image))
        packaged = Image.open(ROOT / "app/src/carePreview/assets/pets/diablillo/care_v1.png")
        np.testing.assert_array_equal(np.asarray(self.imported.image), np.asarray(packaged))
        self.assertEqual(self.imported.image.mode, "RGBA")
        self.assertEqual(self.imported.image.size, (1024, 1536))
        self.assertEqual(len(self.imported.transforms), FRAME_COUNT)

    def test_all_frames_are_unique_and_have_sixteen_pixel_padding(self) -> None:
        atlas = np.asarray(self.imported.image)
        hashes = set()
        for index, transform in enumerate(self.imported.transforms):
            frame = self.cell(atlas, index)
            alpha = frame[:, :, 3]
            visible = alpha >= 16
            self.assertGreater(int(visible.sum()), 10_000, f"frame {index} is empty")
            yy, xx = np.where(visible)
            self.assertGreaterEqual(int(xx.min()), 16)
            self.assertGreaterEqual(int(yy.min()), 16)
            self.assertLessEqual(int(xx.max()), 239)
            self.assertLessEqual(int(yy.max()), 239)
            hashes.add(hashlib.sha256(frame.tobytes()).hexdigest())
            self.assertEqual(transform.output_bbox, (int(xx.min()), int(yy.min()), int(xx.max() + 1), int(yy.max() + 1)))
        self.assertEqual(len(hashes), FRAME_COUNT)

    def test_semantic_source_swap_and_contact_points_are_explicit(self) -> None:
        self.assertEqual(self.imported.transforms[9].source_index, 18)
        self.assertEqual(self.imported.transforms[18].source_index, 9)
        self.assertEqual(len(self.imported.contact_points), FRAME_COUNT)
        for points in self.imported.contact_points:
            self.assertEqual(set(points), {"mouth", "head", "body", "ground"})
            for point in points.values():
                self.assertTrue(all(0.0 <= value <= 1.0 for value in point))


if __name__ == "__main__":
    unittest.main()
