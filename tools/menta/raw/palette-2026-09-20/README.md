# Menta care palette, 2026-09-20

Built-in image edit of the existing `care_v1.png`, with `pet_menta.png`
as the colour reference. Selected first result; the second background-removal
attempt was unnecessary and is not used. The first result already has real alpha.

Prompt direction: preserve the exact 24 poses, cell layout, anatomy, camera,
contact positions and transparent background; change the lime green saturation
to the native portrait's soft mint and cream. No redesign, extra props or shadows.

Compared all 24 alpha silhouettes with the original: IoU 0.94–0.98, opaque
bounds differ by at most 3 source pixels (under 2 desktop pixels). Contact
metadata and cell placement remain unchanged. `tools.menta.palette.care_palette`
removes RGB hidden under zero alpha and alpha-1 specks inside the 15 px
transparent gutter; it does not resize or recrop frames.
The common care builder applies this bank for home and desktop alike.
