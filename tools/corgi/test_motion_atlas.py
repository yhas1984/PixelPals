"""Independent source regressions for the Corgi V2 motion atlas."""
from __future__ import annotations

import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools.corgi.compose_sit_variants import eye_region

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/carePreview/assets/pets/corgi/corgi_motion_v2.png"
REVIEW = ROOT / "tools/corgi/review"
CLEAN = ROOT / "tools/corgi/clean"
CELL = 256


def expected_frames() -> list[Image.Image]:
    frames: list[Image.Image] = []
    for index in range(14):
        source = Image.open(ROOT / f"app/src/main/res/drawable-nodpi/corgi_{index}.png").convert("RGBA")
        frames.append(source.resize((CELL, CELL), Image.Resampling.LANCZOS))
    for index, offset in enumerate((23, 27, 27, 28, 28, 28)):
        source = Image.open((CLEAN if index < 4 else REVIEW) / f"sit-{index}.png").convert("RGBA")
        frame = Image.new("RGBA", (CELL, CELL))
        frame.paste(source.resize((212, 212), Image.Resampling.LANCZOS), (offset, 55))
        frames.append(frame)
    for index, (x, y) in enumerate(((112, 211), (61, 179), (93, 179)), start=1):
        source = Image.open(CLEAN / f"turn-{index}.png").convert("RGBA")
        scaled = source.resize((round(source.width * .94), round(source.height * .94)), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (768, 768))
        canvas.paste(scaled, (x, y))
        frames.append(canvas.resize((CELL, CELL), Image.Resampling.LANCZOS))
    return frames


def atlas_cell(atlas: np.ndarray, index: int) -> np.ndarray:
    x, y = index % 4 * CELL, index // 4 * CELL
    return atlas[y:y + CELL, x:x + CELL]


class CorgiMotionAtlasTest(unittest.TestCase):
    def test_atlas_has_23_cells_matching_independent_sources(self) -> None:
        atlas = np.asarray(Image.open(OUT).convert("RGBA"))
        frames = expected_frames()
        self.assertEqual(atlas.shape, (6 * CELL, 4 * CELL, 4))
        self.assertEqual(len(frames), 23)
        for index, expected in enumerate(frames):
            with self.subTest(frame=index):
                self.assertTrue(np.array_equal(atlas_cell(atlas, index), np.asarray(expected)))

    def test_originals_use_full_canvas_lanczos_and_new_frames_one_camera(self) -> None:
        atlas = np.asarray(Image.open(OUT).convert("RGBA"))
        frames = expected_frames()
        for index in range(14, 23):
            alpha = np.asarray(frames[index])[:, :, 3]
            with self.subTest(frame=index):
                self.assertTrue(np.array_equal(atlas_cell(atlas, index), np.asarray(frames[index])))
                self.assertEqual(int(alpha[:55].max()), 0)
                self.assertEqual(int(np.count_nonzero(alpha[248:] >= 16)), 0)

    def test_turn_height_ground_and_uncropped_silhouette(self) -> None:
        atlas = np.asarray(Image.open(OUT).convert("RGBA"))
        bounds = []
        for index in (0, 20, 21, 22):
            alpha = atlas_cell(atlas, index)[:, :, 3]
            yy, xx = np.nonzero(alpha >= 128)
            bounds.append((int(yy.min()), int(yy.max())))
            self.assertGreater(int(xx.min()), 0)
            self.assertLess(int(xx.max()), 255)
            self.assertGreater(int(yy.min()), 0)
            self.assertLess(int(yy.max()), 255)
        reference_height = bounds[0][1] - bounds[0][0] + 1
        for top, bottom in bounds[1:]:
            self.assertLessEqual(abs(bottom - top + 1 - reference_height), reference_height * .05)
        self.assertLessEqual(max(bottom for _, bottom in bounds) - min(bottom for _, bottom in bounds), 1)

    def test_canonical_sit_variants_keep_body_nose_and_alpha(self) -> None:
        base = np.asarray(Image.open(CLEAN / "sit-3.png").convert("RGBA"))
        mask = eye_region((512, 512))
        for index in (4, 5):
            variant = np.asarray(Image.open(REVIEW / f"sit-{index}.png").convert("RGBA"))
            with self.subTest(frame=index):
                self.assertTrue(np.array_equal(variant[:, :, 3], base[:, :, 3]))
                self.assertTrue(np.array_equal(variant[~mask], base[~mask]))
        nose = (slice(185, 245), slice(365, 440))
        self.assertTrue(np.array_equal(np.asarray(Image.open(REVIEW / "sit-4.png"))[nose], base[nose]))

    def test_metadata_clips_pivot_and_anchor_contract(self) -> None:
        spec = json.loads((OUT.parent / "corgi_motion_v2.json").read_text())
        self.assertEqual((spec["columns"], spec["rows"], spec["frameCount"]), (4, 6, 23))
        self.assertEqual(spec["pivot"], {"x": 128, "y": 128})
        expected = {
            "idle": [0], "walk": [10, 11, 12, 13], "turn": [0, 20, 21, 22, 21, 20, 0], "play": [2, 0],
            "sit_down": [0, 15, 16, 17], "sit": [17, 18, 19, 18, 17],
            "stand_up": [17, 16, 15, 0], "sleep": [17], "wake": [17, 16, 15, 0],
        }
        self.assertEqual({clip["id"]: clip["frames"] for clip in spec["clips"]}, expected)
        self.assertAlmostEqual(spec["anchorTable"]["scale"], 212 / 512)
        self.assertAlmostEqual(spec["anchorTable"]["ground"], 740 / 3)


if __name__ == "__main__":
    unittest.main()
