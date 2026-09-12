# Moki reduced-motion supported rest

Reuses RestFadeTransition to hide the entire overlay before moving a wall/ceiling
pet to the bottom track. The controller's surface, edge progress, velocity, frame
and position update atomically with the view, preventing the old trajectory from
pulling it back after the fade. Horizontal position is retained within valid track
bounds. Sleep readiness waits for opacity to return to one.

Canceling before relocation leaves the original position and restores visibility.
Turning off reduced motion or destroying the behavior cancels the fade. Ordinary
motion keeps the perimeter route implemented previously.

MokiReducedRestTest covers a real controller on the top surface, cancellation,
exactly one invisible relocation, final view/controller alignment and ten seconds
of stable supported perch. It does not prove visual/compositor acceptance or every
device's window placement and inset behavior.

Validation: final debug and instrumentation builds, JVM tests and debug lint
passed. Installed on 192.168.1.160:43041 preserving data. MokiReducedRestTest and
both TelaRestRouteTest tests passed on that phone (three tests, 0.125 seconds).
