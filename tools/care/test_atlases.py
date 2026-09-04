"""Asset contract/regression gates; no image generation or user data access."""
import hashlib
import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from tools.care.build_atlases import (
    ACTIONS, CELL, ROOT, clip_frames, clip_frame_ms, completion_ms, extract_background, extract_cells,
)


class CareAtlasTests(unittest.TestCase):
    def test_extraction_preserves_enclosed_white_anatomy(self):
        original = Image.new("RGB", (64, 64), "#fafafa")
        draw = ImageDraw.Draw(original)
        draw.ellipse((12, 12, 52, 52), fill="white", outline="#7995a9", width=3)
        cleaned = extract_background(original)
        self.assertEqual(cleaned.getpixel((0, 0))[3], 0)
        self.assertEqual(cleaned.getpixel((32, 32))[3], 255)

    def test_grid_detection_does_not_cut_shifted_rows(self):
        board = Image.new("RGBA", (400, 600))
        draw = ImageDraw.Draw(board)
        for row in range(6):
            for col in range(4):
                draw.rectangle((col * 100 + 20, row * 95 + 45, col * 100 + 80, row * 95 + 110), fill="red")
        cells, _ = extract_cells(board)
        self.assertEqual(len(cells), 24)
        self.assertTrue(all(cell.size == (61, 66) for cell in cells))

    def test_all_fifteen_packs_are_complete_transparent_and_padded(self):
        paths = sorted((ROOT / "app/src/debug/assets/pets").glob("*/care_v1.json"))
        self.assertEqual(len(paths), 15)
        for path in paths:
            with self.subTest(pet=path.parent.name):
                spec = json.loads(path.read_text())
                image = Image.open(path.with_suffix(".png"))
                self.assertEqual(image.mode, "RGBA")
                self.assertEqual(image.size, (1024, 1536))
                self.assertLessEqual(image.width * image.height * 4, 16 * 1024 * 1024)
                self.assertEqual(spec["frameCount"], 24)
                self.assertEqual([clip["id"] for clip in spec["clips"]], list(ACTIONS))
                digests = set()
                for index in range(24):
                    frame = image.crop((index % 4 * CELL, index // 4 * CELL,
                                        (index % 4 + 1) * CELL, (index // 4 + 1) * CELL))
                    alpha = np.asarray(frame)[:, :, 3]
                    self.assertGreater(np.count_nonzero(alpha > 128), 1500)
                    self.assertEqual(np.count_nonzero(alpha[:15]), 0)
                    self.assertEqual(np.count_nonzero(alpha[-15:]), 0)
                    self.assertEqual(np.count_nonzero(alpha[:, :15]), 0)
                    self.assertEqual(np.count_nonzero(alpha[:, -15:]), 0)
                    digests.add(hashlib.sha256(frame.tobytes()).hexdigest())
                    for anchor in spec["anchors"][index].values():
                        self.assertTrue(all(0 <= value <= 1 for value in anchor))
                self.assertEqual(len(digests), 24)
                for clip in spec["clips"]:
                    self.assertFalse(clip["loop"])
                    minimum_unique_frames = 1 if spec["petId"] == "diablillo" and clip["id"] == "play" else 3
                    self.assertGreaterEqual(len(set(clip["frames"])), minimum_unique_frames)
                    self.assertEqual(clip["frames"], clip_frames(spec["petId"], ACTIONS.index(clip["id"])))
                    self.assertEqual(clip["frameDurationMs"], clip_frame_ms(spec["petId"], ACTIONS.index(clip["id"])))
                    self.assertEqual(spec["careActions"][clip["id"]]["completionMs"],
                                     completion_ms(spec["petId"], ACTIONS.index(clip["id"])))
                    self.assertLessEqual(spec["careActions"][clip["id"]]["completionMs"],
                                         len(clip["frames"]) * clip["frameDurationMs"])

    def test_imp_has_two_eye_idle_and_no_dog_like_approach(self):
        spec = json.loads((ROOT / "app/src/debug/assets/pets/diablillo/care_v1.json").read_text())
        self.assertEqual(spec["clips"][2]["frames"][0], 3)
        self.assertTrue(all(not {0, 4, 8}.intersection(clip["frames"]) for clip in spec["clips"]))

    def test_imp_is_livelier_without_rushing_its_nap(self):
        spec = json.loads((ROOT / "app/src/debug/assets/pets/diablillo/care_v1.json").read_text())
        durations = {clip["id"]: len(clip["frames"]) * clip["frameDurationMs"] for clip in spec["clips"]}
        self.assertEqual(durations["feed"], 3300)
        self.assertEqual(durations["play"], 4200)
        self.assertEqual(durations["rest"], 5000)
        self.assertEqual(durations["pet"], 4200)

    def test_imp_finishes_quick_bites_before_fire_and_sleeps_upright(self):
        spec = json.loads((ROOT / "app/src/debug/assets/pets/diablillo/care_v1.json").read_text())
        clips = {clip["id"]: clip for clip in spec["clips"]}
        self.assertEqual(clips["feed"]["frames"][:7], [3, 3, 1, 1, 1, 2, 2])
        self.assertEqual(clips["feed"]["frames"][7:10], [6, 6, 6])
        self.assertEqual(clips["feed"]["frameDurationMs"], 300)
        self.assertEqual(spec["careActions"]["feed"]["completionMs"], 2100)
        self.assertEqual(clips["play"]["frames"], [5] * 14)
        self.assertEqual(spec["careActions"]["play"]["completionMs"], 3600)
        self.assertEqual(clips["pet"]["frames"], [3, 9, 9, 10, 10, 11])
        self.assertEqual(clips["rest"]["frames"], [16, 17] + [9] * 8)

    def test_cloud_cat_does_not_pounce_and_moki_does_not_eat_its_toy(self):
        for row in (0, 1):
            self.assertNotEqual(clip_frames("nube_michi", row), clip_frames("ginger", row))
            self.assertFalse({4, 5, 6, 7}.intersection(clip_frames("nube_michi", row)))
        self.assertFalse({0, 1, 2, 3, 5, 6}.intersection(clip_frames("moki", 1)))

    def test_assets_remain_debug_only(self):
        self.assertFalse(list((ROOT / "app/src/main/assets").glob("**/care_v1.*")))
        calibration = json.loads((Path(__file__).parent / "anchors.json").read_text())
        self.assertEqual(len(calibration), 15)
        self.assertTrue(all(len(pet["mouth"]) == 24 for pet in calibration.values()))


if __name__ == "__main__":
    unittest.main()
