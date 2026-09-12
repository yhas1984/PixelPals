"""Regression checks for Jelly's alpha-only outline cleanup."""
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools.pet_pipeline import _components
from tools.jelly.cleanup_outline import remove_outline


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/jelly/source"
LEGACY = [SOURCE / f"jelly_{index}.png" for index in range(8)]


class JellyOutlineCleanupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.legacy_pairs = []
        for index in range(8):
            source = Image.open(SOURCE / f"jelly_{index}.png").convert("RGBA")
            packaged = Image.open(ROOT / "app/src/main/res/drawable-nodpi" / f"jelly_{index}.png").convert("RGBA")
            cls.legacy_pairs.append((source, packaged, remove_outline(source)))
        source = Image.open(SOURCE / "pet_jelly.png").convert("RGBA")
        packaged = Image.open(ROOT / "app/src/main/res/drawable-nodpi/pet_jelly.png").convert("RGBA")
        cls.pet_pair = (source, packaged, remove_outline(source))
        care_source = Image.open(SOURCE / "care_v1.png").convert("RGBA")
        care_packaged = Image.open(ROOT / "app/src/carePreview/assets/pets/jelly/care_v1.png").convert("RGBA")
        cls.care_pairs = []
        for index in range(24):
            box = (index % 4 * 256, index // 4 * 256,
                   (index % 4 + 1) * 256, (index // 4 + 1) * 256)
            source_cell = care_source.crop(box)
            packaged_cell = care_packaged.crop(box)
            cls.care_pairs.append((source_cell, packaged_cell,
                                   remove_outline(source_cell, care_index=index)))

    def test_rgb_and_dimensions_are_preserved_for_all_sources(self):
        pairs = self.legacy_pairs + [self.pet_pair]
        for original, packaged, cleaned in pairs:
            self.assertEqual(original.size, packaged.size)
            self.assertEqual(original.size, cleaned.size)
            np.testing.assert_array_equal(
                np.asarray(original)[:, :, :3], np.asarray(packaged)[:, :, :3],
                err_msg="RGB changed in packaged legacy/cat asset",
            )
            np.testing.assert_array_equal(np.asarray(cleaned), np.asarray(packaged))
        for index, (source, packaged, cleaned) in enumerate(self.care_pairs):
            self.assertEqual(source.size, packaged.size)
            np.testing.assert_array_equal(np.asarray(source)[:, :, :3], np.asarray(cleaned)[:, :, :3])
            if index < 20:
                np.testing.assert_array_equal(np.asarray(cleaned), np.asarray(packaged))

    def test_alpha_never_increases_and_external_sticker_samples_are_removed(self):
        for original, _, cleaned in self.legacy_pairs + [self.pet_pair]:
            self.assertFalse(np.any(np.asarray(cleaned)[:, :, 3] > np.asarray(original)[:, :, 3]))
        for original, _, cleaned in self.care_pairs:
            self.assertFalse(np.any(np.asarray(cleaned)[:, :, 3] > np.asarray(original)[:, :, 3]))
        samples = (("jelly_0.png", 390, 274), ("jelly_3.png", 377, 225),
                   ("jelly_7.png", 241, 465), ("pet_jelly.png", 247, 85))
        for name, x, y in samples:
            if name == "pet_jelly.png":
                original, _, cleaned_image = self.pet_pair
            else:
                index = int(name.removeprefix("jelly_").removesuffix(".png"))
                original, _, cleaned_image = self.legacy_pairs[index]
            original = np.asarray(original)
            cleaned = np.asarray(cleaned_image)
            self.assertFalse(np.any(cleaned[:, :, 3] > original[:, :, 3]), name)
            self.assertGreater(int(original[y, x, 3]), 200)
            self.assertLessEqual(int(original[y, x, :3].max()) - int(original[y, x, :3].min()), 35)
            self.assertEqual(0, int(cleaned[y, x, 3]), f"outline remains in {name} at {(x, y)}")

    def test_central_green_and_interior_highlights_survive(self):
        points = (("jelly_0.png", 384, 384), ("jelly_0.png", 264, 384),
                  ("pet_jelly.png", 256, 256), ("pet_jelly.png", 256, 196))
        for name, x, y in points:
            if name == "pet_jelly.png":
                original, _, cleaned_image = self.pet_pair
            else:
                index = int(name.removeprefix("jelly_").removesuffix(".png"))
                original, _, cleaned_image = self.legacy_pairs[index]
            original = np.asarray(original)
            cleaned = np.asarray(cleaned_image)
            self.assertGreater(int(original[y, x, 3]), 200)
            self.assertGreater(int(cleaned[y, x, 3]), 0, f"interior pixel lost in {name}")
            np.testing.assert_array_equal(original[y, x], cleaned[y, x])

    def test_cleanup_leaves_one_legacy_body_component(self):
        for _, _, cleaned in self.legacy_pairs:
            self.assertEqual(1, len(_components(np.asarray(cleaned)[:, :, 3] > 0)))
        cleaned = np.asarray(self.pet_pair[2])
        self.assertEqual(1, len(_components(cleaned[:, :, 3] > 0)), "pet_jelly.png")
        for index, (_, _, cleaned_image) in enumerate(self.care_pairs):
            expected = 3 if index == 19 else 1
            self.assertEqual(expected, len(_components(np.asarray(cleaned_image)[:, :, 3] > 0)), f"care frame {index}")

    def test_care_frame_19_keeps_body_and_two_sleep_bubbles(self):
        original_image, _, cleaned_image = self.care_pairs[19]
        original = np.asarray(original_image)
        cleaned = np.asarray(cleaned_image)
        self.assertEqual(3, len(_components(cleaned[:, :, 3] > 0)))
        for x, y in ((217, 120), (216, 145)):
            self.assertGreater(int(original[y, x, 3]), 200)
            self.assertGreater(int(cleaned[y, x, 3]), 0)
            np.testing.assert_array_equal(original[y, x], cleaned[y, x])

    def test_cleanup_is_idempotent(self):
        for source, _, once in self.legacy_pairs:
            np.testing.assert_array_equal(np.asarray(once), np.asarray(remove_outline(once)))
        np.testing.assert_array_equal(np.asarray(self.pet_pair[2]), np.asarray(remove_outline(self.pet_pair[2])))
        for index, (_, _, once) in enumerate(self.care_pairs):
            twice = remove_outline(once, care_index=index)
            np.testing.assert_array_equal(np.asarray(once), np.asarray(twice), f"care frame {index}")


if __name__ == "__main__":
    unittest.main()
