"""Verify Jelly care ground anchors follow the cleaned alpha bounds."""
import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
ATLAS = ROOT / "app/src/carePreview/assets/pets/jelly/care_v1.png"
SPEC = ROOT / "app/src/carePreview/assets/pets/jelly/care_v1.json"
BEFORE = ROOT / "tools/jelly/source/care-reference-spec.json"
CELL = 256


class JellyGroundAnchorCleanupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = json.loads(SPEC.read_text())
        cls.before = json.loads(BEFORE.read_text())
        cls.atlas = np.asarray(Image.open(ATLAS).convert("RGBA"))

    def test_ground_y_matches_cleaned_alpha_and_other_anchors_are_unchanged(self):
        self.assertEqual(30, len(self.spec["anchors"]))
        self.assertEqual((2048, 1024, 4), self.atlas.shape)
        for index, anchor in enumerate(self.spec["anchors"]):
            left = index % 4 * CELL
            top = index // 4 * CELL
            alpha = self.atlas[top:top + CELL, left:left + CELL, 3]
            rows = np.where((alpha >= 32).any(axis=1))[0]
            self.assertTrue(len(rows), f"care frame {index} has no visible pixels")
            expected_y = (int(rows[-1]) + 1) / CELL
            self.assertAlmostEqual(expected_y, anchor["ground"][1], places=7, msg=f"frame {index}")
            if index < 20:
                self.assertEqual(self.before["anchors"][index]["ground"][0], anchor["ground"][0])
                for name in ("mouth", "head", "body"):
                    self.assertEqual(self.before["anchors"][index][name], anchor[name])


if __name__ == "__main__":
    unittest.main()
