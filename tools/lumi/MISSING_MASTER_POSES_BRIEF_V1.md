# Lumi Missing Master Poses Brief v1

This brief is for AI pose exploration followed by manual paintover. It is not
permission to promote generated output directly into the Android atlas.

## Shared Output Contract

- Produce one pose per image, not a contact sheet.
- Transparent PNG output, or a clean flat background that can be removed
  without damaging the silhouette.
- Final frame: `384x384`.
- Subject fully contained with at least `16 px` transparent padding.
- Pivot: `{ "x": 192, "y": 368 }`.
- Ground/contact line must match `idle_neutral`.
- Canonical facing: right-facing readable 3/4 or profile.
- Preserve Lumi's painted golden baby-fox identity, cyan eyes, chest star,
  cream fur, large orb-tipped tail, and warm lighting.
- No text, labels, grid, checkerboard, scenery, cast shadow, watermark, extra
  characters, or invented accessories.

## `idle_breath_in`

### Pose Brief

Calm standing idle, matching `idle_neutral` exactly in camera, scale,
proportions, ground contact, and facial identity. Show only a small inhale:
the chest rises subtly, the torso expands by a very small amount, and the
weight remains planted on the same paws. Lumi is relaxed and alert, with a
closed mouth and no directional action.

### Preserve

- Body silhouette and stance from `idle_neutral`.
- Face, ears, cream chest, cyan eyes, chest star, tail, and orb from the
  canonical painted reference.
- Subtle painted motion, not a new pose or stretch.

### Do Not Add

- Tail-only motion as a substitute for breathing.
- Head turn, paw lift, step, yawn, open mouth, crouch, jump, or body stretch.
- Orb detachment, chest-star drift, camera change, or lighting change.

### Manual Retouch Gate

- The chest lift must read at phone size without changing the species
  silhouette.
- Paws remain weight-bearing and the ground line does not move.
- Chest star remains attached to the chest plane.
- No AI eye, paw, ear, fur, or tail artifacts remain.
- Compare directly with `idle_neutral` at 1:1 and at the Android overlay size.

### Existing Candidate Warning

`action_trial_v1/frames/lumi_01_idle_tail_shift.png` is only the nearest
reference. It reads as a tail variation, not a controlled inhale, and must not
be renamed or promoted as `idle_breath_in`.

## `look_profile`

### Pose Brief

Clean attentive right-facing side profile. Lumi has noticed something and holds
still in focused curiosity. The pose must read as observation, not walking,
turning, crouching, or pouncing. Keep the body grounded with a calm neutral
stance and an attentive head/eye line.

### Preserve

- Profile proportions and head geometry from the turnaround reference.
- Ear, muzzle, cheek-fur, chest-star, tail volume, and orb placement from the
  character bible.
- Same painted light direction and warm palette as `idle_neutral`.

### Do Not Add

- Locomotion weight shift, stepping anatomy, gait-like tail swing, or pounce
  tension.
- Front-facing drift, exaggerated head tilt, open action mouth, or a turn
  transition.
- Any crop, matte, extra pose content, or invented view details.

### Manual Retouch Gate

- Profile silhouette is clear at phone size.
- Eye, muzzle, ear, and cheek-fur shapes match the canonical identity.
- Orb stays attached to the tail tip and the chest star stays on the chest
  plane.
- Ground line and pivot match `idle_neutral`.
- No halos, checkerboard, labels, scenery, or stray effects remain.

### Existing Candidate Warning

The current `master_pose_review_v2` profile is an extracted turnaround design
reference. It is useful for proportions but is not an attentive animation pose;
it needs expression intent and paintover before approval.

## Suggested Prompt Skeleton

```text
Use the attached Lumi canonical key art, turnaround, and approved idle frame as
strict identity references. Create one clean 2D painted game-animation key pose
for [POSE_ID]. Preserve Lumi exactly: golden baby fox, cream muzzle/chest/paws,
cyan eyes, cyan chest star, large expressive tail with cyan orb, same camera,
same proportions, same warm painted lighting, right-facing, grounded on the
same baseline. One character only, full body visible, transparent background,
384x384 production cell, minimum 16 px padding, no text or scenery.

[POSE-SPECIFIC DESCRIPTION]
Do not redesign the character or invent anatomy. This is a single key pose for
manual paintover, not a finished spritesheet.
```
