"""Pixel invariants for the native-to-care Ginger REST projection."""
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools.ginger.rest_care import CELL, REST_FRAME_MAP, apply_rest_care

ROOT = Path(__file__).resolve().parents[2]
CARE = ROOT / "app/src/carePreview/assets/pets/ginger/care_v1.png"


class GingerRestCareTest(unittest.TestCase):
    def test_only_reviewed_rest_cells_change(self):
        current = Image.open(CARE).convert("RGBA")
        old = Image.new("RGBA", (CELL * 4, CELL * 6), (37, 64, 97, 173))
        anchors = [{"ground": [0.5, 0.93]} for _ in range(24)]
        transforms = [{} for _ in range(24)]
        projected = apply_rest_care(old.copy(), anchors, transforms)
        for index in range(24):
            box = (index % 4 * CELL, index // 4 * CELL,
                   (index % 4 + 1) * CELL, (index // 4 + 1) * CELL)
            expected = np.asarray(projected.crop(box))
            actual = np.asarray(current.crop(box))
            if index in REST_FRAME_MAP:
                np.testing.assert_array_equal(actual, expected,
                                              err_msg=f"REST frame {index} differs from builder projection")
            else:
                np.testing.assert_array_equal(expected, np.asarray(old.crop(box)),
                                              err_msg=f"non-REST frame {index} changed")

    def test_rest_landmarks_share_authored_ground(self):
        anchors = [{"ground": [0.5, 0.93]} for _ in range(24)]
        transforms = [{} for _ in range(24)]
        apply_rest_care(Image.new("RGBA", (CELL * 4, CELL * 6)), anchors, transforms)
        for index in REST_FRAME_MAP:
            self.assertAlmostEqual(anchors[index]["ground"][1], 238 / 256, places=5)
            self.assertEqual(transforms[index]["sourceFrame"], REST_FRAME_MAP[index])


if __name__ == "__main__":
    unittest.main()
