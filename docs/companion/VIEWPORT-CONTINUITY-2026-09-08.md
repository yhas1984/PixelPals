# Desktop viewport continuity — 2026-09-08

On a configuration change, PetView now compares the previous and refreshed
movement bounds. If they differ, it cancels pending touch/hold tracking, clears
old velocity, clamps the logical position and notifies the behavior to rebuild
its trajectory. Locale-only changes with unchanged bounds do not reset motion.

PetBehavior provides the viewport callback with a reset default. The runtime
adapter publishes its updated environment before resetting, so it receives the
new dimensions before choosing subsequent motion.

Debug and instrumentation assembly passed. Three DesktopCareTransitionTest
tests passed on emulator-5580 (1.918 seconds). The extended test verifies that
the callback sees an already-clamped position and cleared drag velocity, in
addition to the existing care and ground-anchor checks. This directly exercises
reconciliation; a physical rotation/keyboard/lifecycle soak remains pending.
