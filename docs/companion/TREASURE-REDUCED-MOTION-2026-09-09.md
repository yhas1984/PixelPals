# Treasure celebration respects reduced motion

The desktop treasure overlay previously applied its hop, rotation and body deformation regardless of the app's reduced-motion preference. PetView now clears those composite transforms when reduced motion is enabled, including a preference change during a reaction. The reaction timer still expires, so it neither blocks subsequent ambient behavior indefinitely nor restarts when motion is enabled later. Treasure ownership and rewards are unchanged.

An emulator-only integration test exercises every pet with an underlying stationary pose. It verifies a visible normal reaction, immediate clearing of translation/rotation/scaling on preference change, expiration and no later revival. Full PetView tests are kept off the user's phone because construction refreshes persisted pet state.

Validation: debug APKs, 240 JVM tests and lint passed. The full Android instrumentation suite passed 179 tests in 69.97 seconds on disposable emulator 5580; output is in `evidence/treasure-reduced-motion/android-suite.txt`. This includes the current Corgi care-camera checks and the previously uncommitted changes. It does not establish continuous visual acceptance or performance/battery acceptance.

A separate front-turn art candidate was rejected for painted transparency and inconsistent outlines/proportions; see `tools/corgi/raw/turn-front-2026-09-09/REVIEW.md`. No new turn frames were integrated.
