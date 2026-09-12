# Patito motion continuity — 2026-09-09

DuckBehavior now retains neutral body scale throughout walking, takeoff, flutter, landing and landing recovery. Walking frames advance with distance, with positional sway fading at movement endpoints. The foot offset lifts rather than sinks the sprite. Landing recovery stays planted.

Flight pitch carries the six-degree takeoff angle into flutter, settles to zero at its endpoint, and uses a zero-at-both-ends landing rotation. Flutter's extra offset also fades at both ends. Existing flight path endpoints, facing and frame choices remain.

Debug APK/test APK assembly and lint passed (56 seconds). Six instrumented tests passed on the disposable emulator (2.369 seconds): Patito's complete interaction flight, Ginger motion tests, Menta body-scale regression and the all-15-species behavior smoke test. The Patito test verifies scale, flight phases, return to IDLE, ground contact, bounds, bounded vertical travel and absence of abrupt pitch changes. Logs: `evidence/duck-motion/`.

This validates behavioral continuity, not full artistic consistency of every source frame. No new artwork was produced. Physical-device acceptance remains outstanding; the last phone endpoint was offline during the previous installation attempt. This APK includes the preceding Ginger/Menta changes and is ready for the next phone connection.

## Follow-up: launches at the top boundary

The earlier flight test started on the ground. A separate review found that launching from `bounds.top` still subtracted 35% of the sprite height during takeoff, moving Patito above its permitted region. Takeoff now limits lift to available headroom, and the flutter arc is bounded by its endpoints' headroom. Window positions and flight offsets respect current bounds; target ranges also handle a short vertical viewport.

A new test launches from the top left, center and right, checks window and render-offset bounds through each complete flight, and requires return to IDLE at ground level. These cases, the original flight test and the all-species behavior smoke test passed: three instrumented tests, 2.365 seconds. Debug/test APK assembly and lint passed in 48 seconds. Logs: `evidence/duck-top-boundary/`. The full-height descent has a 30-second simulated test budget because speed-limited flight legitimately takes longer than the ground-start interaction.
