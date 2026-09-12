# Corgi fetch braking — 2026-09-08

Previously, horizontal travel continued during the last 220 ms before catching
the ball, although the renderer had already switched to stationary head-lowering
art. That made the planted feet slide across the desktop.

Fetch now completes its travel before the pickup pose. The shared quintic ground
motion provides smooth acceleration and braking; duration derives from distance
and a peak speed of 4.3 sprite widths per second. The separate 220 ms pickup phase
keeps the pet stationary. Catch rewards still occur at catchMs exactly once.

`CorgiGait` now owns the distance-based frame selection for ordinary walking,
zoomies and fetch. Walking retains a .44-sprite stride and running uses .34.
Original frames 10–13 remain active; the eight candidate frames are still a
separate debug study. No permanent desktop ball is introduced.

Validation: 208 JVM tests passed with no failures or skips, including both
directions, peak-speed bounds, stationary pickup and shared foot-cycle checks.
Debug assembly passed. Three Corgi playback instrumentation tests passed on
emulator-5580, covering completion, cancellation and unavailable medicine.
Physical-device visual review is outstanding; these checks do not certify
the artwork or complete the all-species animation plan.
