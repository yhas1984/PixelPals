# Physical validation of 3f7a85c — 2026-09-08

Connected to the user-provided endpoint 192.168.1.204:41533. Installed the
current debug APK with `adb install -r`, preserving app data. Opened MainActivity
and observed the existing selected Yuki and home. Activated the visible desktop
button and observed Yuki above another application, with no permanent ball.

Ran only these non-data-mutating instrumentation checks on the phone:

- MentaTravelTest
- PetDreamRenderingTest
- DesktopCareTransitionTest#baselineMatchesRenderedFeetForEveryProductionPet

All three passed in 3.793 seconds. Reopened PixelPals after instrumentation.
Screenshots of the phone were inspected locally and are not included in the
repository because they also show the user's other application.

This is evidence of installation, home/overlay rendering and the named tests.
It is not visual acceptance of all animation loops, physical soak/performance
validation or completion of the full release plan. Rotation continuity remains
under review; no rotation fix is included in this validation entry.
