"""Independent regression checks for Ginger's placed care-cell cleanup."""
import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools.ginger.cleanup_care import clean_care_cell


ROOT = Path(__file__).resolve().parents[2]
SOURCE_PATH = ROOT / "tools/ginger/source/care_v1-before-shadow.png"
PACKAGED_PATH = ROOT / "app/src/carePreview/assets/pets/ginger/care_v1.png"
ANCHORS_PATH = ROOT / "tools/care/anchors.json"
CELL = 256


class GingerCareCleanupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = np.asarray(Image.open(SOURCE_PATH).convert("RGBA"))
        cls.packaged = np.asarray(Image.open(PACKAGED_PATH).convert("RGBA"))
        cls.mouths = json.loads(ANCHORS_PATH.read_text())["ginger"]["mouth"]

    def cell(self, atlas, index):
        left = index % 4 * CELL
        top = index // 4 * CELL
        return atlas[top:top + CELL, left:left + CELL]

    def test_layout_and_rgb_are_preserved(self):
        self.assertEqual(self.source.shape, (1536, 1024, 4))
        self.assertEqual(self.packaged.shape, self.source.shape)
        np.testing.assert_array_equal(self.source[:, :, :3], self.packaged[:, :, :3])

    def test_alpha_only_decreases_inside_reviewed_floor_rows(self):
        source_alpha = self.source[:, :, 3]
        packaged_alpha = self.packaged[:, :, 3]
        self.assertFalse(np.any(packaged_alpha > source_alpha))
        changed = packaged_alpha != source_alpha
        for index in range(24):
            left = index % 4 * CELL
            top = index // 4 * CELL
            local_rows = np.where(changed[top:top + CELL, left:left + CELL].any(axis=1))[0]
            if len(local_rows):
                first_floor_row = 150 if index in (0, 4) else 175
                self.assertGreaterEqual(int(local_rows.min()), first_floor_row)

    def test_face_and_whisker_regions_remain_byte_identical(self):
        for index, (mouth_x, mouth_y) in enumerate(self.mouths):
            left = index % 4 * CELL
            top = index // 4 * CELL
            # The calibrated box is an exclusive interior: the helper's
            # strict whisker guard intentionally leaves its boundary eligible
            # for floor cleanup.
            x0, x1 = max(0, mouth_x - 47), min(CELL, mouth_x + 47)
            y0, y1 = max(0, mouth_y - 15), min(CELL, mouth_y + 15)
            source_region = self.source[top + y0:top + y1, left + x0:left + x1]
            packaged_region = self.packaged[top + y0:top + y1, left + x0:left + x1]
            visible = source_region[:, :, 3] >= 16
            np.testing.assert_array_equal(
                source_region[visible], packaged_region[visible],
                err_msg=f"visible face/whisker region changed in frame {index}",
            )

    def test_real_shadow_samples_are_removed(self):
        # Fixed points were selected from the reviewed neutral floor matte,
        # independently of the cleanup implementation.
        samples = ((0, 38, 174), (4, 53, 164), (8, 73, 179),
                   (12, 90, 176), (17, 213, 184), (23, 65, 199))
        for index, x, y in samples:
            source_pixel = self.cell(self.source, index)[y, x]
            packaged_pixel = self.cell(self.packaged, index)[y, x]
            self.assertGreaterEqual(int(source_pixel[3]), 200)
            self.assertLessEqual(int(source_pixel[:3].max()) - int(source_pixel[:3].min()), 20)
            self.assertGreaterEqual(int(source_pixel[:3].min()), 90)
            self.assertEqual(0, int(packaged_pixel[3]), f"shadow remains in frame {index} at {(x, y)}")

    def test_warm_opaque_pixels_are_unchanged(self):
        rgb = self.source[:, :, :3].astype(int)
        warm_opaque = (self.source[:, :, 3] > 200) & (rgb.max(axis=2) - rgb.min(axis=2) > 20)
        np.testing.assert_array_equal(
            self.source[warm_opaque], self.packaged[warm_opaque],
            err_msg="opaque cream/orange pixels changed",
        )

    def test_current_spec_matches_preserved_apk_spec_when_available(self):
        previous = ROOT / "docs/companion/evidence/ginger-shadow-2026-09-12/care-spec-before.json"
        if not previous.exists():
            self.skipTest("preserved APK care spec is unavailable")
        self.assertEqual(
            json.loads((ROOT / "app/src/carePreview/assets/pets/ginger/care_v1.json").read_text()),
            json.loads(previous.read_text()),
        )

    def test_builder_recipe_is_reproducible_and_cleanup_is_idempotent(self):
        reproduced = self.source.copy()
        for index, mouth in enumerate(self.mouths):
            cleaned = clean_care_cell(Image.fromarray(self.cell(self.source, index)), index, tuple(mouth))
            left = index % 4 * CELL
            top = index // 4 * CELL
            reproduced[top:top + CELL, left:left + CELL] = np.asarray(cleaned)
        np.testing.assert_array_equal(reproduced, self.packaged)

        cleaned_twice = self.packaged.copy()
        for index, mouth in enumerate(self.mouths):
            cleaned = clean_care_cell(Image.fromarray(self.cell(self.packaged, index)), index, tuple(mouth))
            left = index % 4 * CELL
            top = index // 4 * CELL
            cleaned_twice[top:top + CELL, left:left + CELL] = np.asarray(cleaned)
        np.testing.assert_array_equal(cleaned_twice, self.packaged)


if __name__ == "__main__":
    unittest.main()
