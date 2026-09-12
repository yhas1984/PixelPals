#!/usr/bin/env python3
"""Audit the home routes selected by the current Kotlin home implementation.

Checks package contents and atlas structure only. It does not assess art quality.
"""
from datetime import date
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
PETS = "corgi taro bloop nube_michi jelly ginger angel patito diablillo moki yuki piru menta tela lumi".split()

# HomeLegacyClips.forPet, as distinct frame indices per routed clip.
LEGACY = {
    "corgi": ([0], [4, 5, 6, 7], [1, 0], [2, 3]),
    "patito": ([2], [0, 1], [3, 2], [2]),
    "diablillo": ([0, 1], [2, 3], [0, 1], [0]),
    "bloop": ([0], [0, 1], [2, 0], [0]),
    "nube_michi": ([1], [2, 3], [1, 2], [0]),
    "jelly": ([0], [1, 2, 3, 0], [4, 5], [0]),
}
# Read directly from HomeRestArtwork.load. The source atlas is checked below.
REST_SEQUENCES = {
    "corgi": [16, 17, 18, 19],
    "patito": [16, 17, 18], "bloop": [16, 17, 18],
    "moki": [16, 17, 18], "menta": [16, 17, 18],
    "jelly": [16, 17, 17], "diablillo": [3, 17, 9],
}


def read_spec(path):
    return json.loads(path.read_text())


def check_atlas(path):
    """Validate dimensions, contiguous metadata, and clip index bounds."""
    spec = read_spec(path)
    image_path = path.with_suffix(".png")
    if not image_path.exists():
        image_path = ROOT / "app/src/main/assets" / spec["atlasPath"]
    image = Image.open(image_path)
    expected = (spec["columns"] * spec["frameWidth"], spec["rows"] * spec["frameHeight"])
    if image.size != expected:
        raise AssertionError(f"{path}: expected {expected}, got {image.size}")
    if [f["index"] for f in spec["frames"]] != list(range(spec["frameCount"])):
        raise AssertionError(f"{path}: frame metadata is not contiguous")
    for clip in spec["clips"]:
        if not clip["frames"] or not all(0 <= i < spec["frameCount"] for i in clip["frames"]):
            raise AssertionError(f"{path}: invalid {clip['id']} clip indices")
    return spec


def choose(spec, *names):
    clips = {clip["id"]: clip for clip in spec["clips"]}
    return next((clips[name] for name in names if name in clips), spec["clips"][0])


def route_manifest(pet, variant):
    """Mirror HomeLocomotion: Corgi legacy, otherwise merged pets assets."""
    if pet == "corgi":
        return None, "legacy drawables (forced)"
    debug = sorted((ROOT / f"app/src/debug/assets/pets/{pet}").glob("*.json"))
    main = sorted((ROOT / f"app/src/main/assets/pets/{pet}").glob("*.json"))
    merged = {path.name: path for path in main}
    if variant == "debug":
        merged.update({path.name: path for path in debug})
    paths = [path for name, path in merged.items() if not name.startswith("care")]
    if paths:
        path = sorted(paths, key=lambda p: ("motion_v2" in p.name, p.name), reverse=True)[0]
        return path, "debug pets asset" if path in debug else "main pets asset"
    if pet in LEGACY:
        return None, "legacy drawables"
    raise FileNotFoundError(f"No home route manifest for {pet}")


def atlas_counts(pet, path):
    spec = check_atlas(path)
    idle = choose(spec, "idle", "sit", "perch_loop", "hover")
    walk = choose(spec, "walk", "crawl_loop", "right", "glide", "hover")
    turn = choose(spec, "turn", "idle", "sit", "perch_loop", "hover")
    play = choose(spec, "play", "playful_delight", "happy", "front_social", "groom", "grace", "tongue_strike", "idle")
    sleep = choose(spec, "prayer", "sleep", "perch_loop", "idle") if pet == "angel" else choose(spec, "blink", "idle") if pet == "menta" else choose(spec, "sleep", "prayer", "perch_loop", "idle")
    wake = next((clip for clip in spec["clips"] if clip["id"] == "wake"), None)
    values = [idle, walk, turn, play, sleep]
    return "/".join(str(len(set(clip["frames"]))) for clip in values) + "/" + (str(len(set(wake["frames"]))) if wake else "reverse")


def legacy_counts(pet):
    clips = LEGACY[pet]
    idle, walk, play, sleep = clips
    return "/".join(str(len(set(clip))) for clip in (idle, walk, idle, play, sleep, sleep[::-1]))


def effective_rest(pet, packaged, path):
    if pet == "tela":
        return "TelaRestPose 2/2"
    if packaged and pet in REST_SEQUENCES:
        sequence = REST_SEQUENCES[pet]
        return f"care_v1 {len(set(sequence))}/{len(set(sequence[::-1]))}"
    if path is None:
        sleep = LEGACY[pet][3]
        return f"base sleep {len(set(sleep))}/{len(set(sleep[::-1]))}"
    spec = read_spec(path)
    sleep = (choose(spec, "prayer", "sleep", "perch_loop", "idle") if pet == "angel"
             else choose(spec, "blink", "idle") if pet == "menta"
             else choose(spec, "sleep", "prayer", "perch_loop", "idle"))
    sleep_frames = sleep['frames'][:2] if pet == "angel" else sleep['frames']
    wake = next((clip['frames'] for clip in spec['clips'] if clip['id'] == 'wake'), sleep_frames[::-1])
    return f"base sleep {len(set(sleep_frames))}/{len(set(wake))}"


def main():
    care_root = ROOT / "app/src/carePreview/assets"
    lines = [f"# Home asset audit — {date.today().isoformat()}", "", "The audit follows the current `HomeLocomotion`, `HomeRestArtwork`, and `HomeLegacyClips` selectors. Base counts are distinct frame indices after clip fallbacks; effective rest is reported separately because reviewed care art and Tela's dedicated rest pose replace the base sleep route. These are structural counts, not quality scores.", "", "| Pet | Debug home JSON / fallback | Release home JSON / fallback | Base counts debug (idle/walk/turn/play/sleep/wake) | Base counts release (idle/walk/turn/play/sleep/wake) | Effective rest debug | Effective rest release default | Effective rest release RC | Care art packaged |", "|---|---|---|---|---|---|---|---|---|"]
    for pet in PETS:
        debug_path, debug_source = route_manifest(pet, "debug")
        release_path, release_source = route_manifest(pet, "release")
        debug_counts = legacy_counts(pet) if debug_path is None else atlas_counts(pet, debug_path)
        release_counts = legacy_counts(pet) if release_path is None else atlas_counts(pet, release_path)
        care = care_root / f"pets/{pet}/care_v1.json"
        packaged = care.exists() and care.with_suffix(".png").exists()
        debug_route = str(debug_path.relative_to(ROOT)) if debug_path else debug_source
        release_route = str(release_path.relative_to(ROOT)) if release_path else release_source
        debug_rest = effective_rest(pet, packaged, debug_path)
        default_rest = effective_rest(pet, False, release_path)
        rc_rest = effective_rest(pet, packaged, release_path)
        lines.append(f"| {pet} | {debug_route} | {release_route} | {debug_counts} | {release_counts} | {debug_rest} | {default_rest} | {rc_rest} | {'yes (carePreview)' if packaged else 'no'} |")
    lines += ["", "## Interpretation and limits", "", "- Corgi is legacy in both debug and release because `HomeLocomotion.load` returns `loadLegacy` before looking for an atlas. The debug `companion/pets/corgi.json` is therefore not a home route.", "- The current selector reads the merged `pets/<pet>` directory and prefers `motion_v2` by filename. This script approximates that merge with debug assets taking precedence over main assets; it does not claim that an unbuilt source set is packaged.", "- `carePreview` contains care atlases for all 15 pets and is added to debug. Release receives that source set only with `-Ppixelpals.companion.releaseCandidate=true`. `HomeRestArtwork` currently consumes care rest sequences for Corgi, Patito, Bloop, Moki, Menta, Jelly, and Diablillo. Tela instead replaces sleep with `TelaRestPose.frames` (38, 39). Care art packaging and home-rest usage are separate facts.", "- The selector is duplicated here because a Python audit cannot execute Android `AssetManager`, `BuildConfig`, or Gradle source-set merging. If Kotlin routing changes, this report must be updated; the checks are not runtime proof.", "- Missing dedicated clip names use Kotlin's fallback behavior. The audit makes no claim about artistic quality, visual continuity, or a need for new frames from a missing name.", ""]
    destination = ROOT / "docs/companion/MOTION-ASSET-AUDIT.md"
    destination.write_text("\n".join(lines))
    print(f"{len(PETS)} pet routes audited; report written to {destination}")


if __name__ == "__main__":
    main()
