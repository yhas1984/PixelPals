"""Check packaged medicine mass, camera, expressions, and calibrated contact."""
import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]


class JellyMedicineArtworkTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.atlas = Image.open(ROOT / 'app/src/carePreview/assets/pets/jelly/care_v1.png').convert('RGBA')
        cls.spec = json.loads((ROOT / 'app/src/carePreview/assets/pets/jelly/care_v1.json').read_text())

    def test_four_reviewed_expressions_are_packaged_at_stable_scale(self):
        areas, widths, heights = [], [], []
        for index in range(4):
            pose = np.array(self.atlas.crop((index * 256, 1280, (index + 1) * 256, 1536)))
            expected = np.array(Image.open(ROOT / f'tools/jelly/clean/medicine_{index}.png').convert('RGBA'))
            np.testing.assert_array_equal(expected, pose)
            y, x = np.where(pose[:, :, 3] >= 32)
            widths.append(x.max() - x.min() + 1)
            heights.append(y.max() - y.min() + 1)
            areas.append(len(x))
            self.assertTrue(190 <= widths[-1] <= 198)
            self.assertTrue(130 <= heights[-1] <= 138)
            self.assertGreater(x.min(), 16)
            self.assertLess(x.max(), 240)
            self.assertEqual(0, int(pose[:80, :, 3].sum()))
        self.assertLessEqual(max(widths) - min(widths), 2)
        self.assertLessEqual(max(heights) - min(heights), 2)
        self.assertLess((max(areas) - min(areas)) / min(areas), .01)

    def test_spoon_contact_is_inside_each_drawn_mouth(self):
        for index in range(20, 24):
            x, y = [round(value * 256) for value in self.spec['anchors'][index]['mouth']]
            pose = np.array(self.atlas.crop(((index % 4) * 256, 1280, (index % 4 + 1) * 256, 1536)))
            pixel = pose[y, x]
            self.assertGreater(pixel[3], 240)
            self.assertLess(pixel[1], 120, f'Mouth anchor missed drawn mouth in {index}')


if __name__ == '__main__':
    unittest.main()
