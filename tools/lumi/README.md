# Lumi Production Pipeline

Lumi is integrated as a premium production candidate. The canonical generated
atlas lives in `pipeline/`; Android runtime, catalog, billing, and asset
validation use the 40-frame V2 candidate. Release promotion remains gated by
manual master-pose approval and device QA.

## Production Planning

The pilot now has an explicit quality contract before more frames are
generated:

- `PET_PRODUCTION_BIBLE_V1.md`: shared art, atlas, animation, rejection, and
  device-QA rules for every PixelPals pet.
- `LUMI_CHARACTER_BIBLE_V1.md`: Lumi's identity, personality, canonical views,
  pose invariants, and manual-retouch checklist.
- `lumi_production_plan_v1.json`: the current candidate contract and promotion
  gates. Its historical 32-frame plan is retained only for provenance.

The former source atlases and review boards are archived under
`archive/v1/`. They remain research material and are not runtime inputs.

## Direction

The canonical reference is `/home/yhas/Pictures/pixelpals_refs/lumi.png`: a
painted golden fox with cream fur tufts, cyan eyes, a chest star, and a large
orb-holding tail. That artwork is the source of truth; procedural substitutes
are discarded.

## Canonical V2 Pipeline

- `pipeline/generate_boards.py`: generates the ten sequential GPT Image 2 boards.
- `pipeline/build_atlas_v2.py`: cleans, normalizes, mirrors, and builds the 40-frame atlas.
- `pipeline/validate_atlas_v2.py`: validates dimensions, padding, pivots, and frame coverage.
- `pipeline/atlas_v2/lumi_motion_v2.{png,json}`: canonical candidate atlas and timings.
- `app/src/main/assets/pets/lumi/lumi_motion_v2.{png,json}`: promoted Android candidate copy.

## Current output

- `archive/v1/create_2d_reference_scene.py`: embeds the source PNG into Blender, creates a
  square atlas camera, safe-area guides, named anchor points, timeline markers,
  and an explicitly unbound 2D rig scaffold.
- `archive/v1/blender_2d/lumi_2d_reference_v1.blend`: packed Blender reference scene.
- `archive/v1/blender_2d/lumi_reference_square_v1.png`: 384x384 transparent camera render.
- `archive/v1/build_source_atlas.py`: extracts the supplied 4x4 contact sheet by connected
  components, so poses that cross nominal row gutters are not clipped.
- `archive/v1/validate_source_atlas.py`: validates the 1536x1536 atlas draft, metadata,
  frame coverage, transparency, and 16 px padding.
- `archive/v1/source_atlas/lumi_sheet_source_v1.png`: 16-frame, 384 px atlas draft.
- `archive/v1/source_atlas/lumi_sheet_source_v1.json`: draft frame names and clips.
- `archive/v1/source_atlas/lumi_frames_source_v1.png`: cleaned contact sheet for review.
- `archive/v1/source_atlas/lumi_source_report.json`: records source bounds and which poses
  crossed the original 384x256 row boundaries.
- `archive/v1/source_atlas/action_trial_v1/`: 32-frame cleaned R&D atlas used by the debug
  overlay; it is not a production promotion.
- `archive/v1/build_fox_motion_atlas.py`: extracts the four GPT-image-2 fox-motion boards,
  removes detached artifacts, normalizes 20 fox-like motion poses plus 8
  special-action poses to `384x384` cells, and writes a 28-frame debug atlas.
- `archive/v1/source_atlas/fox_motion_rnd_v1/`: the former R&D atlas and preview used to
  test fox locomotion. Its first 20 frames cover alert, investigation, a full
  eight-pose walk, and pounce; the remaining 8 frames cover magic, surprise,
  drowsiness, sleep, snuggle, and return-to-idle.
- `app/src/debug/assets/pets/lumi/lumi_fox_motion_rnd_v1.*`: former debug-only copy of
  that atlas and metadata. It must not be copied into production assets.

## Validation

```bash
LUMI_REFERENCE_PATH=/home/yhas/Pictures/pixelpals_refs/lumi.png \
  /home/yhas/Downloads/blender-5.2.0-linux-x64/blender \
  --background --factory-startup --python tools/lumi/archive/v1/create_2d_reference_scene.py

python3 tools/lumi/archive/v1/build_source_atlas.py
python3 tools/lumi/archive/v1/validate_source_atlas.py
```

The source is a flattened image. Blender can use it immediately as a packed
reference and composition plane, but independent professional poses require
layer separation or additional painted poses. The scaffold records that limit
instead of producing fake deformation frames. The supplied spritesheet is a
separate, usable pose source; its original rows were only 256 px high, so the
builder recovers crossing poses globally before creating the 384 px draft.

The current candidate is copied into `app/src/main/assets/pets/lumi/` only
after the V2 pipeline validator passes. The archive is never promoted again.

Production Android integration now uses `LumiBehavior` and the pure
`LumiMotionController`; the debug overlay remains available for visual review.

The archived fox-motion atlas drove the old debug overlay behavior. Runtime states use
the new walk, investigate, stalk, pounce, recovery, idle, and rest clips. Walk
movement is phase-locked to the four walk frames, and long walks may include a
small, cooled-down hop before continuing. This avoids root sliding while keeping
the baby fox playful. This is behavior validation only: generated poses still
require manual visual review, anatomy cleanup, and clip-loop approval before
production promotion.

## Next Production Gate

Approve six manually retouched master poses before expanding the atlas:

`idle_neutral -> idle_breath_in -> look_profile -> walk_contact_right -> pounce_air -> sleep_curl`

The current V2 candidate has been tested in the Android debug overlay, but
release promotion still requires the manual pose gate.

## Master Pose Review

The first review board is generated at:

- `archive/v1/review/master_pose_review_v1/lumi_master_pose_review_v1.png`
- `archive/v1/review/master_pose_review_v1/lumi_master_pose_review_v1.json`
- `archive/v1/build_master_pose_review.py`: reproducible board builder

The normalized second review set is generated at:

- `archive/v1/review/master_pose_review_v2/lumi_master_pose_review_v2.png`
- `archive/v1/review/master_pose_review_v2/lumi_master_pose_review_v2.json`
- `archive/v1/review/master_pose_review_v2/frames/`: six `384x384` review frames
- `archive/v1/build_master_pose_set.py`: reproducible normalizer and board builder
- `MISSING_MASTER_POSES_BRIEF_V1.md`: exact AI exploration and manual-paint
  brief for `idle_breath_in` and `look_profile`.
- `validate_master_pose_set.py`: blocks incomplete or technically invalid
  master-pose review sets.

Current review result:

- Useful candidates needing retouch: `idle_neutral`, `walk_contact_right`,
  `pounce_air`, `sleep_curl`.
- Missing poses that must be painted rather than renamed: `idle_breath_in`.
- `look_profile` now has a normalized turnaround extract for design reference,
  but it is still not an attentive animation pose and remains blocked.
- Promotion remains blocked until all six candidates pass manual visual review.

Validate the current review set with:

```bash
python3 tools/lumi/validate_master_pose_set.py
```
