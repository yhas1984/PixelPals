# Corgi action transitions

The user's remaining size discontinuities included procedural deformation and a mismatched run anticipation pose. Original bitmaps and the shared home/desktop camera calibration are preserved.

- Desktop physics no longer stretches Corgi to 115% height / 90% width or reverses a left-facing animal during a fall. The behavior fallback fall/jump paths also preserve proportions.
- Alert, seated rest and affection no longer pulse the scale of the entire body.
- A short planted anticipation replaces original frame 9 at the beginning of a zoomie. That separately framed lying pose was not a running contact. The gait still uses its existing fixed camera correction.
- Walking decelerates before a timed action; zoomies accelerate and decelerate, and walking after a bow or affection starts from rest.

## Verification

- Debug and instrumentation APK builds, lint and 233 JVM tests passed (zero JVM failures/errors/skips).
- Full Android suite on disposable emulator-5580 with overlay permission: 166 tests, 69.085 seconds, passed. This includes the previously pending naming-dialog changes retained in the working tree.
- Only CorgiScaleRenderingTest and PetViewPhysicsIntegrationTest ran on the physical phone: 8 tests, 0.611 seconds, passed. Correction from subsequent source review: PetView construction launches routine repository status refreshes. These tests do not explicitly reset progress, but should not have been described as having no persistence effects. PetViewPhysicsIntegrationTest now requires a disposable emulator.
- Updated the physical phone's debug APK with a preserving install.

## Remaining visual work

These changes remove identified runtime size changes. They do not redraw anatomy or add in-between frames. The existing gait test permits 15% silhouette-height variation and is not proof that head/body proportions match every care, sniffing, digging or seated illustration. Complete visual acceptance across those poses and the other 14 pets remains open.
