# Pose continuity while decorating

Entering decoration already paused scene time, but rendering treated editing as reduced motion. This replaced the current walking frame with an idle frame and removed its lift and rotation. The active toy also returned to its resting position.

Editing now preserves the current rendered pose and toy response while scene time remains paused. Reduced-motion preferences still apply independently. The paused editor repaints once per second for time-of-day updates; touch events invalidate immediately. Changing editor mode restarts scheduling so normal animation resumes without waiting for that timer.

Validation on 2026-09-09:
- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, and `lintDebug`: successful.
- Android emulator: 9 tests passed across `HomeCorgiContinuityTest`, `DecorationTouchPlacementTest`, and `PostcardExporterTest`.
- The new regression compares the rendered walking pose before entering, during, and immediately after leaving editing.
- Physical installation remains pending: `192.168.1.160:40327` returned `No route to host`. Emulator checks do not establish physical-device visual acceptance or completion of the full project plan.

Evidence: `evidence/home-edit-pose/android-tests.txt`.
