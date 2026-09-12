# Tela supported rest route

Scheduled sleep now sends a request separately from checking readiness. Tela retains
the current committed action, then chooses a route toward floor contact: ceiling to
wall, wall to floor, or a silk descent from the center. Route durations scale with
distance and a peak speed of 1.8 sprite widths/s. Quintic interpolation removes the
immediate full-speed start in its traversal helpers. At ground arrival, silk is
cleared and offsets/rotation are reset before handing off to the sleeping body/bed.
Canceling the request restores normal decisions at the next action boundary.

Readiness requires ground contact; a stationary suspended spider is not treated as
supported furniture contact. Reduced motion now uses a short opacity transition
for suspended pets (see `TELA-REDUCED-REST-2026-09-08.md`). Full visual transition
acceptance remains unfinished.

Validation: debug and instrumentation APK builds, JVM tests and debug lint passed.
Installed on 192.168.1.160:43041. TelaRestRouteTest plus AutonomousSleepStateTest
passed (three tests, 0.218 seconds). The route test exercises real traversal helpers
from five starting locations, checks bounded per-frame displacement and eventual
ground support. It does not validate rendered art, an overnight run or the other
flying species. The broader transformation plan remains incomplete.
