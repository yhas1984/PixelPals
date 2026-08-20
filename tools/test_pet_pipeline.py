import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pet_pipeline import validate_atlas


class PetPipelineTest(unittest.TestCase):
    def _spec(self, path: Path) -> None:
        path.write_text(json.dumps({
            "version": 2,
            "petId": "fixture",
            "atlasPath": "fixture.png",
            "frameWidth": 64,
            "frameHeight": 64,
            "columns": 1,
            "rows": 1,
            "frameCount": 1,
            "pivot": {"x": 32, "y": 60},
            "renderHints": {"drawScale": 1.0},
            "clips": [{"id": "idle", "frames": [0], "loop": True, "frameDurationMs": 100}],
            "frames": [{"index": 0, "name": "idle_00"}],
        }), encoding="utf-8")

    def test_clean_fixture_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            atlas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            for y in range(16, 48):
                for x in range(16, 48):
                    atlas.putpixel((x, y), (100, 200, 180, 255))
            atlas.save(root / "fixture.png")
            self._spec(root / "fixture.json")
            self.assertTrue(validate_atlas(root / "fixture.png", root / "fixture.json")["passed"])

    def test_interior_hole_is_blocked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            atlas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            for y in range(16, 48):
                for x in range(16, 48):
                    if not (26 <= x < 38 and 26 <= y < 38):
                        atlas.putpixel((x, y), (100, 200, 180, 255))
            atlas.save(root / "fixture.png")
            self._spec(root / "fixture.json")
            self.assertFalse(validate_atlas(root / "fixture.png", root / "fixture.json")["passed"])

    def test_bright_ground_shadow_is_blocked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            atlas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            for y in range(16, 43):
                for x in range(16, 48):
                    atlas.putpixel((x, y), (100, 200, 180, 255))
            # Detached white matte in the contact band. It is intentionally
            # outside the protected character dilation so the validator must
            # reject it even though it is not a dark neutral shadow.
            for y in range(53, 57):
                for x in range(20, 44):
                    atlas.putpixel((x, y), (210, 210, 210, 255))
            atlas.save(root / "fixture.png")
            self._spec(root / "fixture.json")
            self.assertFalse(validate_atlas(root / "fixture.png", root / "fixture.json")["passed"])

    def test_contact_anchor_drift_is_blocked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            atlas = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
            for y in range(16, 48):
                for x in range(16, 48):
                    atlas.putpixel((x, y), (100, 200, 180, 255))
            for y in range(8, 40):
                for x in range(80, 112):
                    atlas.putpixel((x, y), (100, 200, 180, 255))
            atlas.save(root / "fixture.png")
            spec = {
                "version": 2,
                "petId": "fixture",
                "atlasPath": "fixture.png",
                "frameWidth": 64,
                "frameHeight": 64,
                "columns": 2,
                "rows": 1,
                "frameCount": 2,
                "pivot": {"x": 32, "y": 60},
                "renderHints": {"drawScale": 1.0},
                "clips": [{"id": "walk", "frames": [0, 1], "loop": True, "frameDurationMs": 100}],
                "contactAnchors": [{"clip": "walk", "bottomTolerance": 1, "centerTolerance": 1}],
                "frames": [
                    {"index": 0, "name": "walk_00"},
                    {"index": 1, "name": "walk_01"},
                ],
            }
            (root / "fixture.json").write_text(json.dumps(spec), encoding="utf-8")
            report = validate_atlas(root / "fixture.png", root / "fixture.json")
            self.assertFalse(report["passed"])
            self.assertTrue(any("contact-line-drift" in violation for violation in report["violations"]))

    def test_tela_climb_face_has_no_transparency_slit(self):
        atlas = Path(__file__).resolve().parent / "tela/pipeline/atlas_v2/tela_motion_v2.png"
        spec = Path(__file__).resolve().parent / "tela/pipeline/atlas_v2/tela_motion_v2.json"
        report = validate_atlas(atlas, spec)
        self.assertTrue(report["passed"], report["violations"])
        for frame in report["frames"][12:16]:
            self.assertLess(
                int(frame["interiorTransparentPixels"]),
                40,
                f"climb frame {frame['index']} has a face alpha slit",
            )

    def test_taro_full_quadruped_candidate_is_promoted_atomically(self):
        root = Path(__file__).resolve().parent.parent
        approved = root / "tools/taro/pipeline/atlas_v2"
        candidate = root / "tools/taro/pipeline/candidates/quadruped_full_02"
        debug = root / "app/src/debug/assets/pets/taro"
        main = root / "app/src/main/assets/pets/taro"
        self.assertEqual(
            (candidate / "taro_motion_v2.png").read_bytes(),
            (debug / "taro_motion_v2.png").read_bytes(),
        )
        self.assertEqual(
            (candidate / "taro_motion_v2.json").read_bytes(),
            (debug / "taro_motion_v2.json").read_bytes(),
        )
        self.assertEqual(
            (candidate / "taro_motion_v2.png").read_bytes(),
            (approved / "taro_motion_v2.png").read_bytes(),
        )
        self.assertEqual(
            (candidate / "taro_motion_v2.json").read_bytes(),
            (approved / "taro_motion_v2.json").read_bytes(),
        )
        self.assertEqual(
            (candidate / "taro_motion_v2.png").read_bytes(),
            (main / "taro_motion_v2.png").read_bytes(),
        )
        self.assertEqual(
            (candidate / "taro_motion_v2.json").read_bytes(),
            (main / "taro_motion_v2.json").read_bytes(),
        )
        report = validate_atlas(
            candidate / "taro_motion_v2.png",
            candidate / "taro_motion_v2.json",
        )
        self.assertTrue(report["passed"], report["violations"])
        spec = json.loads((candidate / "taro_motion_v2.json").read_text(encoding="utf-8"))
        self.assertEqual(spec["renderHints"]["posture"], "quadruped_with_front_playful_social")
        self.assertEqual(spec["renderHints"]["walkPosture"], "quadruped")
        expected_pose_classes = ["playful_front" if index in range(24, 28) else "quadruped" for index in range(40)]
        self.assertEqual([frame["poseClass"] for frame in spec["frames"]], expected_pose_classes)
        self.assertEqual([frame["poseClass"] for frame in spec["frameDetails"]], expected_pose_classes)
        self.assertEqual(
            [frame["source"] for frame in spec["frames"]],
            [frame["source"] for frame in spec["frameDetails"]],
        )
        self.assertEqual(
            [frame["sourceCell"] for frame in spec["frames"]],
            [frame["sourceCell"] for frame in spec["frameDetails"]],
        )


if __name__ == "__main__":
    unittest.main()
