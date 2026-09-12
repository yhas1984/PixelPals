# Corgi care camera and treasure proportions

The seated-care calibration introduced for rest had also been applied to petting and medicine. Their upright source drawings fill more of the atlas cell, so they grew at care boundaries (medicine measured 125 px versus the original seated frame's 108 px at a 160 px actor size).

Each authored family now has a fixed camera: rest .96, petting/medicine .81, bath .82, feeding/fetch .78. Home care and desktop care read the same calibration. No per-frame silhouette normalization or bitmap edits were introduced; curled and lowered poses retain their natural height changes. The original Corgi bank remains unchanged.

Treasure reactions no longer widen or flatten Corgi. The existing movement remains, with unit body scale throughout the reaction.

Validation:

- Debug app and instrumentation APK builds, 240 JVM tests and lint passed on the final application source. The final instrumentation/lint gate also passed.
- 19 targeted Android tests passed on disposable emulator 5580 in 4.608 seconds: original rendering, care endpoints, home gait continuity, feeding/fetch renderer, physics and treasure proportions in both directions.
- The upright bath endpoint check uses a 3% height tolerance against the original seated pose. Rest/medicine use 10%; this measures camera continuity, not anatomical correctness.
- Inspected the actual renderer contact sheet in `evidence/corgi-upright-scale/care-handoffs.png`; instrumentation output is stored alongside it.

These changes do not establish that every original-frame transition is smooth. Original pose perspective differences and missing anatomical in-betweens still require animation work and continuous device review. This is not full-plan or release acceptance.
