# Continuous Corgi fetch contact

After the camera correction, the rolling ball still ended at a hardcoded point while the caught ball attached to the scaled mouth. With a 320-pixel actor, that handoff could jump roughly 21 pixels diagonally. The ball also reset its rotation to zero at the catch.

The final 220 ms, after Corgi has stopped, now blend the rolling position into the actual mouth anchor using the same smooth progress curve as locomotion. Catch ownership changes only at the original completion time. The prop preserves its final rolling orientation while held. Reduced motion continues to attach a stationary, unrotated ball immediately.

Validation on the current tree:

- Debug/test APK builds, all 230 JVM tests and lint passed (45 seconds).
- Seven targeted Android tests passed on `192.168.1.160:43041` (3.462 seconds), without database or preference changes. These cover the real Corgi frame bank, floor/scale, feeding, reduced motion, held-mouth contact, and the new pickup boundary.
- The new test samples each millisecond of pickup in both directions using the packaged mouth anchors; it rejects position or orientation jumps at the catch and checks the final mouth position.
- Debug APK installed successfully on the phone. No production publication or full Android suite rerun in this change; the preceding full-suite evidence belongs to c30e99d.

This fixes prop continuity. It does not add anatomical in-between drawings to the discrete head-lowering and head-lifting poses; continuous visual acceptance remains open.
