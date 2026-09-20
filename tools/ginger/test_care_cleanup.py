"""Regression checks for Ginger care cleanup and native REST replacement."""
import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools.ginger.cleanup_care import clean_care_cell
from tools.ginger.rest_care import REST_FRAME_MAP, apply_rest_care

ROOT = Path(__file__).resolve().parents[2]
SOURCE_PATH = ROOT / "tools/ginger/source/care_v1-before-shadow.png"
PACKAGED_PATH = ROOT / "app/src/carePreview/assets/pets/ginger/care_v1.png"
ANCHORS_PATH = ROOT / "tools/care/anchors.json"
SPEC_PATH = ROOT / "app/src/carePreview/assets/pets/ginger/care_v1.json"
PREVIOUS_SPEC_PATH = ROOT / "tools/ginger/source/care_v1-before-rest.json"
CELL = 256
CLEANUP_FRAMES = tuple(index for index in range(24) if index not in REST_FRAME_MAP)


class GingerCareCleanupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = np.asarray(Image.open(SOURCE_PATH).convert("RGBA"))
        cls.packaged = np.asarray(Image.open(PACKAGED_PATH).convert("RGBA"))
        cls.mouths = json.loads(ANCHORS_PATH.read_text())["ginger"]["mouth"]

    @staticmethod
    def cell(atlas, index):
        left, top = index % 4 * CELL, index // 4 * CELL
        return atlas[top:top + CELL, left:left + CELL]

    def cleaned_then_native(self):
        atlas = self.source.copy()
        for index in CLEANUP_FRAMES:
            cleaned = clean_care_cell(Image.fromarray(self.cell(atlas, index)), index,
                                      tuple(self.mouths[index]))
            left, top = index % 4 * CELL, index // 4 * CELL
            atlas[top:top + CELL, left:left + CELL] = np.asarray(cleaned)
        anchors = [{"ground": [0.5, 238 / 256]} for _ in range(24)]
        transforms = [{} for _ in range(24)]
        projected = apply_rest_care(Image.fromarray(atlas), anchors, transforms)
        return np.asarray(projected), anchors

    def test_builder_reproduction_and_non_rest_byte_identity(self):
        reproduced, _ = self.cleaned_then_native()
        np.testing.assert_array_equal(reproduced, self.packaged)
        for index in CLEANUP_FRAMES:
            np.testing.assert_array_equal(self.cell(self.packaged, index),
                                          self.cell(reproduced, index))

    def test_layout_and_rgb_are_preserved_on_cleaned_frames(self):
        self.assertEqual(self.source.shape, (1536, 1024, 4))
        self.assertEqual(self.packaged.shape, self.source.shape)
        for index in CLEANUP_FRAMES:
            np.testing.assert_array_equal(self.cell(self.source, index)[:, :, :3],
                                          self.cell(self.packaged, index)[:, :, :3])

    def test_alpha_only_decreases_inside_reviewed_floor_rows(self):
        for index in CLEANUP_FRAMES:
            source_alpha = self.cell(self.source, index)[:, :, 3]
            packaged_alpha = self.cell(self.packaged, index)[:, :, 3]
            self.assertFalse(np.any(packaged_alpha > source_alpha), f"alpha grew in frame {index}")
            changed_rows = np.where((packaged_alpha != source_alpha).any(axis=1))[0]
            if len(changed_rows):
                first_floor_row = 150 if index in (0, 4) else 175
                self.assertGreaterEqual(int(changed_rows.min()), first_floor_row)

    def test_reviewed_shadow_samples_are_removed(self):
        samples = ((0, 38, 174), (4, 53, 164), (8, 73, 179), (12, 90, 176))
        for index, x, y in samples:
            source_pixel = self.cell(self.source, index)[y, x]
            packaged_pixel = self.cell(self.packaged, index)[y, x]
            self.assertGreaterEqual(int(source_pixel[3]), 200)
            self.assertLessEqual(int(source_pixel[:3].max()) - int(source_pixel[:3].min()), 20)
            self.assertGreaterEqual(int(source_pixel[:3].min()), 90)
            self.assertEqual(0, int(packaged_pixel[3]), f"shadow remains in frame {index} at {(x, y)}")

    def test_cleanup_contract_excludes_native_rest_cells(self):
        for index in CLEANUP_FRAMES:
            cleaned = clean_care_cell(Image.fromarray(self.cell(self.source, index)), index,
                                      tuple(self.mouths[index]))
            cleaned_again = clean_care_cell(cleaned, index, tuple(self.mouths[index]))
            np.testing.assert_array_equal(np.asarray(cleaned_again), np.asarray(cleaned),
                                          err_msg=f"cleanup is not idempotent in frame {index}")
        reproduced, _ = self.cleaned_then_native()
        for index in REST_FRAME_MAP:
            np.testing.assert_array_equal(self.cell(self.packaged, index),
                                          self.cell(reproduced, index))

    def test_face_regions_and_warm_art_are_preserved_on_cleaned_frames(self):
        for index in CLEANUP_FRAMES:
            mouth_x, mouth_y = self.mouths[index]
            source_cell, packaged_cell = self.cell(self.source, index), self.cell(self.packaged, index)
            x0, x1 = max(0, mouth_x - 47), min(CELL, mouth_x + 47)
            y0, y1 = max(0, mouth_y - 15), min(CELL, mouth_y + 15)
            source_region, packaged_region = source_cell[y0:y1, x0:x1], packaged_cell[y0:y1, x0:x1]
            visible = source_region[:, :, 3] >= 16
            np.testing.assert_array_equal(source_region[visible], packaged_region[visible],
                                          err_msg=f"face region changed in frame {index}")
            rgb = source_cell[:, :, :3].astype(int)
            warm = (source_cell[:, :, 3] > 200) & (rgb.max(axis=2) - rgb.min(axis=2) > 20)
            np.testing.assert_array_equal(source_cell[warm], packaged_cell[warm],
                                          err_msg=f"warm art changed in frame {index}")

    def test_spec_preserves_existing_contract_and_validates_native_anchors(self):
        current = json.loads(SPEC_PATH.read_text())
        previous = json.loads(PREVIOUS_SPEC_PATH.read_text())
        expected = dict(previous)
        expected["anchors"] = list(previous["anchors"])
        for index in REST_FRAME_MAP:
            expected["anchors"][index] = current["anchors"][index]
        self.assertEqual(current, expected)
        _, anchors = self.cleaned_then_native()
        for index in REST_FRAME_MAP:
            self.assertEqual(current["anchors"][index], anchors[index])


if __name__ == "__main__":
    unittest.main()
