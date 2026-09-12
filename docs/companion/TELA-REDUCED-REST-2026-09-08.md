# Tela reduced-motion rest

The ordinary controller remains paused with reduced motion enabled. A separate
rest transition fades the entire view out over 150 ms, holds an invisible frame,
then changes its ground position once and fades in over 150 ms. No trajectory
across the screen is animated. Grounded stationary walking can hand off directly.
The sleep replacement waits until the fade finishes; silk and corner effects are
cleared at relocation and the grounded body uses its sleep pose.

Canceling the rest request or turning off reduced motion restores view visibility.
Cancellation before relocation keeps the original position. Delta values are
bounded, and invalid deltas cannot force relocation. Destruction cancels the fade.

JVM tests cover the invisible frame, exactly one relocation, final opacity and
cancellation at different update rates. Android tests exercise the real Tela
adapter and View alpha, including cancellation before relocation. These checks
do not certify compositor timing visually or the remaining flying species.

Validation: final debug and instrumentation APK builds, JVM tests and debug lint
passed. Installed on 192.168.1.160:43041 preserving data. Both TelaRestRouteTest
tests and both AutonomousSleepStateTest tests passed (four tests, 0.153 seconds).
