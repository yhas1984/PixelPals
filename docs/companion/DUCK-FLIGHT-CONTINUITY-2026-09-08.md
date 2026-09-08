# Patito flight continuity and sleep pause

The flutter phase previously interpolated from the original ground position after
takeoff had already raised the duck. It now starts from takeoff's actual endpoint.
Horizontal flight uses eased progress and a distance-based duration capped at 2.5
sprite widths per second. Ground travel uses quintic acceleration/deceleration with
a 95 px/s peak, then a short quack pause instead of immediately starting a new leg.
Landing completes before the same pause. Scheduled sleep is allowed during that
pause (or stationary reduced-motion waddling), never during takeoff or flight.

Validation: debug and test APK builds, all JVM tests and debug lint passed. Installed
on 192.168.1.160:43041 preserving data. DuckFlightContinuityTest and both tests in
AutonomousSleepStateTest passed on the phone (three tests, 0.064 seconds).
The flight test advances the real controller through every airborne/landing phase,
checks per-frame displacement bounds and eventual sleep-safe ground contact.
It bypasses asynchronous bitmap loading and does not certify artwork or visual
acceptance. Tela and the other flying/climbing sleep transitions remain pending.
