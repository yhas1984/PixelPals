# Corgi source-camera continuity

The original walking and frontal sitting exports use a closer camera than the standing drawing. Care artwork and the generated home atlas also used independent framing. A constant destination rectangle alone did not preserve apparent body size.

## Change

- Keep the original PNGs intact. Apply one fixed 0.84 camera correction to the entire original gait and sitting pair, anchored at the authored 740/768 floor. Do not independently fit each silhouette: lifted paws and lowered ears must remain real pose changes.
- Calibrate desktop care cells to 0.78 of the actor size. The held ball uses the same scale for its mouth attachment.
- Use the original desktop Corgi bank in the home, with the same camera corrections and the planted standing frame. Packaged care poses provide its sleep/wake sequence; care artwork remains gated by build packaging.
- Account for the home's 4% floor padding when drawing scheduled sleep on the desktop, avoiding an extra upward jump.
- Load all 14 original Corgi frames before starting playback. The previous partial load could substitute a sitting frame for a missing walking frame. Fallback camera correction now follows the actual drawn frame for other partial banks too.

## Validation

- Debug APK, Android test APK, JVM tests and lint: build successful, 55 seconds. 230 JVM tests, zero failures/errors/skips.
- Care asset validators: 8 passed. No bitmap files were changed.
- Full Android suite on disposable emulator-5580: runner reported `OK (153 tests)`, 58.411 seconds. Overlay UI coverage was skipped because overlay permission was absent. After granting it on the disposable emulator, `CareSceneOverlayUiTest` separately passed (1 test, 5.767 seconds). See the captured logs; this was not a second full-suite run.
- The new real-renderer test requires all 14 decoded frames (no placeholder substitutions), checks both orientations, bounds standing-to-gait height variation to 15%, and checks floor drift at no more than one pixel. These geometric bounds are regression checks, not a claim of perfect animation.
- Inspected the actual original gait rendering and home idle/rest export. The first review exposed partial loading in the test itself; the final evidence contains all four actual gait frames.

## Limits

These are fixed camera calibrations, not newly drawn in-between frames. Four-frame locomotion, anatomical differences between original and care illustrations, pose transitions, and continuous physical-device visual acceptance still need review. The full 15-pet plan and production acceptance are not complete.

Evidence: [desktop gait](evidence/corgi-scale/desktop-gait.png), [home/rest](evidence/corgi-scale/home-rest.png), [Android suite](evidence/corgi-scale/android-suite.txt), [overlay UI](evidence/corgi-scale/overlay-test.txt).
