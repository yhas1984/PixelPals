"""Independent preservation and camera gates for the optional posture bank."""
import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/carePreview/assets/pets/ginger"


class GingerPostureAtlasTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = json.loads((ASSETS / "ginger_motion_v2.json").read_text())
        cls.atlas = Image.open(ASSETS / "ginger_motion_v2.png").convert("RGBA")

    def frame(self, index):
        x, y = index % 4 * 384, index // 4 * 384
        return self.atlas.crop((x, y, x + 384, y + 384))

    def test_original_pixels_and_clips_are_unchanged(self):
        original = ROOT / "app/src/main/assets/pets/ginger"
        source = Image.open(original / "ginger_sheet_v2.png").convert("RGBA")
        self.assertEqual(source.tobytes(), self.atlas.crop((0, 0, 1536, 1536)).tobytes())
        source_spec = json.loads((original / "ginger_sheet_v2.json").read_text())
        self.assertEqual(source_spec["clips"], self.spec["clips"][:len(source_spec["clips"])])
        self.assertEqual(source_spec["pivot"], self.spec["pivot"])
        self.assertEqual(source_spec["frames"], self.spec["frames"][:16])

    def test_all_new_poses_have_real_alpha_and_planted_paws(self):
        self.assertEqual((1536, 1920), self.atlas.size)
        self.assertEqual(19, self.spec["frameCount"])
        for index in (16, 17, 18):
            pixels = np.asarray(self.frame(index))
            alpha = pixels[:, :, 3]
            ys, xs = np.where(alpha >= 128)
            self.assertGreater(len(xs), 10000)
            self.assertGreater(xs.min(), 4)
            self.assertLess(xs.max(), 380)
            self.assertLess(abs(int(ys.max()) - 367), 2)
            self.assertFalse(np.any(alpha[:8]))
            self.assertFalse(np.any(alpha[376:]))
            self.assertFalse(np.any(alpha[:, :4]))
            self.assertFalse(np.any(alpha[:, 380:]))
            self.assertLess(np.count_nonzero(alpha) / alpha.size, .45)

    def test_camera_declares_fixed_support_anchor_without_bleed(self):
        self.assertEqual({"0": .68, "1": .68, "2": .70}, self.spec["postureCamera"]["frameScales"])
        self.assertTrue(self.spec["renderHints"]["preserveFrameAnchors"])
        self.assertEqual(0, self.spec["renderHints"]["recommendedBleedInsetPx"])

    def test_all_clip_indices_are_packaged_and_transition_endpoints_match(self):
        clips = {clip["id"]: clip for clip in self.spec["clips"]}
        self.assertEqual([0, 16, 17, 18], clips["stand_up"]["frames"])
        self.assertEqual(list(reversed(clips["stand_up"]["frames"])), clips["sit_down"]["frames"])
        for clip in clips.values():
            self.assertTrue(clip["frames"])
            self.assertTrue(all(0 <= i < 19 for i in clip["frames"]))


if __name__ == "__main__":
    unittest.main()
