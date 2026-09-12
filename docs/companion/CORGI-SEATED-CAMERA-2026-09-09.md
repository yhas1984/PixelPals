# Corgi seated source-camera correction

## Evidence

The new CorgiArtworkReviewTest exports all 14 original sprites and five care timelines through the actual desktop renderers at a 160-pixel actor size. The prior uniform care-cell calibration made the final seated rest drawing visibly smaller than original seated frame 6. See `evidence/corgi-art-review/seated-comparison.png`: original, previous care rendering, corrected care rendering, left to right.

## Implementation

- Keep the standing care family at 0.78 actor cells and calibrate the seated family (affection, rest and medicine) at 0.96. This is fixed per authored pose family, not per-frame silhouette normalization.
- Use the seated calibration for the home's rest artwork too. The desktop anchors and care props continue to follow the calibrated coordinates.
- The care panel uses the same ratio between standing and seated families, constrained to its existing maximum size. Its authored 0.93 ground anchor stays on one baseline when action size changes. Remove its remaining whole-body breathing scale.
- No bitmap assets were edited. The differing front/three-quarter view and anatomical details still require artwork refinement; camera calibration alone does not complete that work.

## Regression

The render review asserts that the final seated care silhouette stays within 10% of the original seated height. This catches the previous undersized handoff, but does not measure head proportions, naturalness or all transitions. Before/after sheets remain available for visual review.

Final checks: debug and instrumentation builds, lint and 234 JVM tests passed (zero JVM failures/errors/skips), 54 seconds. Full Android suite on disposable emulator-5580 with overlay permission: 169 tests passed in 67.963 seconds, including room care interaction and the new rendered comparison. The phone supplied the before sheets; the emulator supplied the after sheet. Updated phone debug APK installed with data preserved. Live physical-device care acceptance remains separate from these offscreen comparisons.
