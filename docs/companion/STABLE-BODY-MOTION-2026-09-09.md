# Stable body motion — 2026-09-09

Ginger no longer scales the whole animal during sitting, sleeping, pounce anticipation, landing or touch reactions. The authored crouch/impact/recovery poses carry these actions; grounded idle and landing no longer add a vertical offset that floats or sinks the paws. Airborne travel, rotation, walking cadence, touch reaction and facing remain.

Menta's COIL and HAPPY modes now retain neutral body scale instead of oscillating up to 3–5% and snapping back on locomotion. TOUCH also clears inherited scale. Existing frame sequences and expressive movement remain.

Validation: debug assembly and lint passed (47 seconds); test APK assembly passed. Seven instrumented tests passed on the disposable emulator (2.318 seconds), covering Ginger's pounce/landing/reset and grounded scale, Menta's expressive modes and return to locomotion, all-species behavior smoke and desktop sprite scale. Logs are in `evidence/stable-body-motion/`. No phone data was used by tests.

Limitations: this removes procedural distortion, not artwork differences. Visual inspection of Ginger's source atlas still shows head/body proportion differences across poses; final frame consistency and continuous physical-device review are outstanding. DuckBehavior's takeoff/flutter/landing scale transitions were also identified for follow-up. No assets were changed or promoted to release.
