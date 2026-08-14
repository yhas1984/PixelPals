# PixelPals Pet Production Bible v1

## Purpose

This document is the quality contract for PixelPals pet art and animation. It
exists to keep generated, retouched, and hand-painted assets consistent before
they enter Android production.

Lumi is the first pet that must satisfy the contract. Existing pets are not
retroactively changed by this document; they migrate only after the Lumi pilot
is approved.

## Source Of Truth

Reference priority is explicit:

1. The approved painted character reference.
2. The character-specific bible.
3. Approved clean key poses and turnarounds.
4. Retouched animation frames.
5. Generated exploration sheets and trial atlases.

Generated images are exploration material. They are never the final visual
authority without manual review and retouching.

## Visual Contract

- Preserve the character silhouette, proportions, palette, face, markings,
  accessories, and lighting language across every frame.
- Use one canonical camera and one canonical facing direction per pet.
- Keep the subject fully inside the cell. Do not rely on clipping to hide a
  broken pose.
- Keep the ground/contact line stable. Feet may move during a step, but the
  character must not float or sink between equivalent poses.
- Keep important identity features readable at the in-app display size.
- Keep effects that are not part of the character, such as magic trails,
  separated when the runtime needs to animate them independently.

## Technical Contract

Production atlas defaults:

- PNG with real RGBA transparency.
- `384x384` frame cells.
- Atlas dimensions equal `frameWidth * columns` by `frameHeight * rows`.
- `frameCount == columns * rows` for the first production version.
- At least `16 px` transparent inner padding on every side of every frame.
- One stable pivot per pet, normally `{ "x": 192, "y": 368 }` for Lumi.
- `recommendedBleedInsetPx = 1`.
- `filterBitmap = true` for painted artwork.
- No baked checkerboard, white background, grid, labels, numbers, watermark,
  scenery, or external shadow.

Required atlas metadata is defined by `PetAtlasSpec`:

- `version`
- `petId`
- `atlasPath`
- `previewPath`
- `frameWidth`, `frameHeight`
- `columns`, `rows`
- `frameCount`
- `pivot`
- `renderHints`
- `clips`
- `frames`

Frame names use lowercase `snake_case` and describe one unambiguous pose:

```text
lumi_00_idle_neutral
lumi_01_idle_breath_in
lumi_08_walk_contact_left
```

Avoid names such as `new`, `final2`, `copy`, or names that describe an
implementation detail instead of the pose.

## Production Workflow

### 1. Define

Write the character bible, action list, frame budget, pivot, and rejection
criteria before generating frames.

### 2. Explore

Use AI to explore poses and variations from the approved references. Generate
one action or small pose family at a time, not an entire production atlas by
default.

### 3. Select

Choose the clearest silhouette and the most on-model pose for each beat. Delete
near-duplicates rather than using them to inflate a frame count.

### 4. Retouch

Manually correct anatomy, face, eyes, fur edges, tail, accessories, lighting,
and contact points. Remove background remnants and AI artifacts.

### 5. Normalize

Place each approved frame into the canonical cell, align its pivot and ground
line, and preserve the required padding. Do not normalize by independently
scaling every pose until the character loses physical continuity.

### 6. Pack

Build the atlas and metadata with a reproducible script. Keep individual clean
frames for review and future repacking.

### 7. Validate

Run technical checks, review each clip as a loop or one-shot, then test it in
the Android debug overlay on a real device.

### 8. Promote

Only an approved atlas is copied to production assets. Trial files stay under
`tools/` or `src/debug/` and are never silently promoted.

## Animation Principles

- Prefer readable key poses over many weak in-betweens.
- Idle should breathe and settle; it must not twitch randomly.
- Walk should communicate weight and direction without obvious foot sliding.
- A pounce needs anticipation, launch, airborne shape, landing, and recovery.
- A one-shot must have a readable beginning and a calm exit to a compatible
  state.
- Sleep is a long hold with subtle breathing, not a fast loop.
- Transitions should use a compatible settle/neutral pose instead of popping
  between unrelated silhouettes.
- Direction is runtime state. Art is authored in one canonical direction unless
  a clip explicitly needs a different view.

## Rejection Gates

Reject a frame or clip when any of these is true:

- Identity drift changes the species or signature features.
- Ear, paw, tail, eye, orb, or chest-marking geometry changes without intent.
- A limb, tail, effect, or accessory is duplicated, missing, or fused.
- The frame is cropped, touches the atlas edge, or depends on neighboring-cell
  content.
- The background or a light/dark matte remains visible after compositing.
- The pivot causes visible floating, sinking, or contact jumps.
- Lighting, camera, or scale changes between adjacent frames.
- A cycle only looks correct at one preview size.
- Code-side scaling, offsets, or frame substitutions are being used to hide an
  art defect.

## QA Checklist

### Technical

- [ ] PNG is RGBA and background is truly transparent.
- [ ] Atlas dimensions and metadata agree.
- [ ] Frame indexes are contiguous and clips reference valid frames.
- [ ] No frame is empty or below the padding minimum.
- [ ] Required semantic clips exist.
- [ ] Preview and individual frames are available for review.

### Visual

- [ ] Character stays on model across the complete set.
- [ ] Ground line and pivot are stable.
- [ ] No tail, ear, paw, orb, or marking is clipped.
- [ ] No halos, checkerboard, labels, or scenery remain.
- [ ] Loop seams are readable at the actual Android display size.

### Runtime

- [ ] Atlas loads in the debug overlay.
- [ ] Facing changes do not move the contact point.
- [ ] Tap, drag, and release transitions are readable.
- [ ] Idle and sleep do not force unnecessary high-frequency updates.
- [ ] Thirty-minute device run has no crash or runaway layout updates.

## Promotion Rule

The current Lumi action trial is explicitly `R&D`. It is useful for runtime
integration and behavior timing, but it does not satisfy the visual promotion
gate until the frames receive manual cleanup and clip-specific review.
