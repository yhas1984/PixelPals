"""Regression checks for Jelly's exact reviewed rest cells and clip metadata."""
import hashlib
import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools.jelly.rest_artwork import REST_CLIP, REST_FRAMES, sources

ROOT = Path(__file__).resolve().parents[2]
ATLAS = ROOT / "app/src/carePreview/assets/pets/jelly/care_v1.png"
SPEC = ROOT / "app/src/carePreview/assets/pets/jelly/care_v1.json"
REPORT = ROOT / "tools/care/build_report.json"
CELL = 256


class JellyRestArtworkTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = json.loads(SPEC.read_text())
        cls.atlas = np.asarray(Image.open(ATLAS).convert("RGBA"))
        cls.report = next(item for item in json.loads(REPORT.read_text()) if item["pet"] == "jelly")

    def test_exact_sources_and_metadata(self):
        self.assertEqual(self.spec["frameCount"], 30)
        self.assertEqual(self.spec["rows"], 8)
        rest = next(clip for clip in self.spec["clips"] if clip["id"] == "rest")
        self.assertEqual(rest["frames"], REST_CLIP)
        self.assertEqual(rest["frameDurationMs"], 250)
        self.assertEqual(self.spec["careActions"]["rest"]["completionMs"], 4000)
        self.assertEqual([frame["name"] for frame in self.spec["frames"][24:30]],
                         [f"rest_expression_{i}" for i in range(6)])
        transforms = self.report["frames"][24:30]
        for frame, source, transform in zip(REST_FRAMES, sources(), transforms):
            expected = np.asarray(Image.open(source).convert("RGBA"))
            actual = self.atlas[frame // 4 * CELL:(frame // 4 + 1) * CELL,
                                frame % 4 * CELL:(frame % 4 + 1) * CELL]
            self.assertTrue(np.array_equal(actual, expected), frame)
            self.assertEqual(transform["sourceSha256"], hashlib.sha256(source.read_bytes()).hexdigest())

    def test_medicine_and_first_24_are_untouched_by_rest_append(self):
        medicine = np.asarray(Image.open(ROOT / "tools/jelly/clean/medicine_0.png").convert("RGBA"))
        actual = self.atlas[5 * CELL:6 * CELL, :CELL]
        self.assertTrue(np.array_equal(actual[:, :, 3], medicine[:, :, 3]))
        roi = actual[155:195, 85:182, :3]
        self.assertTrue(np.array_equal(roi, medicine[155:195, 85:182, :3]))
        self.assertEqual(self.atlas.shape, (2048, 1024, 4))
        for frame in REST_FRAMES:
            tile = self.atlas[frame // 4 * CELL:(frame // 4 + 1) * CELL,
                              frame % 4 * CELL:(frame % 4 + 1) * CELL]
            self.assertGreater(np.count_nonzero(tile[:, :, 3]), 0)
        for frame in (30, 31):
            tile = self.atlas[frame // 4 * CELL:(frame // 4 + 1) * CELL,
                              frame % 4 * CELL:(frame % 4 + 1) * CELL]
            self.assertEqual(np.count_nonzero(tile), 0)


if __name__ == "__main__":
    unittest.main()
