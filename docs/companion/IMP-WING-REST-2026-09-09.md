# Diablillo: unfold, then settle wings at waking

The rejected spread-wing endpoint from `CATALOGUE-WAKE-2026-09-09.md` has been replaced with a two-stage exit. During the final second of rest, Diablillo opens its eyes and unwraps its wings, then folds the membranes downward alongside the torso before returning to ordinary desktop behavior. The shoulders stay attached; only wing geometry changes. The existing five-second duration and four-second reward point are preserved.

`ImpWingPainter` now interpolates its membrane and ribs into a narrow resting shape behind the body. Foreground wrapping disappears as the wings open. Its default resting amount is reset on each draw, including icons, so a previous rest scene cannot leak geometry into another use. The final tips were shortened after visual review to avoid extending below the feet. Reduced motion retains the static sleeping pose and switches directly to the awake, side-folded endpoint.

`SpeciesRestRecovery` now includes all 14 shared-care species; Corgi continues using its separate choreography. This removes the remaining absent awake endpoint in the shared care renderer. It does not establish final artistic consistency between care atlases and ordinary desktop artwork.

Validation of the final source:
- Debug app/test assembly, 249 JVM tests and debug lint passed.
- Eleven Android tests passed in 7.952 seconds: wing geometry/reset, the 14-species rendered recovery/completion review, all-species clipping/contact cases and care asset/render checks.
- The final native Android strip was inspected: `evidence/imp-wing-rest/diablillo.png`. It ends with narrow wings alongside the body, instead of the previously rejected full span.
- Logs: `evidence/imp-wing-rest/build.txt` and `android-tests.txt`.

Physical-device continuous playback and the care-to-locomotion identity/scale handoff remain part of the wider acceptance work. No physical installation or production release was performed in this pass.
