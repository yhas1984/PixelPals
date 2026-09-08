# Floating pet rest readiness

Bloop waits for FLOAT_VISIBLE, full opacity and speed at most 1 px/s before normal
scheduled sleep. A rest request suppresses new disappearance/alert decisions once
floating and exponentially damps drift. Active disappearance/alert/escape actions
retain their existing completion. With reduced motion, a hidden ghost fades back
in over 200 ms at its current position before handing off; no teleport is added.

Angel waits for hover/prayer at at most 1 px/s. Requested hover/prayer damps in place
without the edge springs reaccelerating the actor, and hover does not start another
cruise. Other active flight actions finish first. Reduced motion permits stationary
flight poses except touch/recovery. Reduced-motion recovery now has a stationary
fade handoff described in `ANGEL-REDUCED-REST-2026-09-08.md`.

Nube Michi waits for its native SLEEP_FLOAT state in normal motion. With reduced
motion its already-static cloud pose can hand off directly at the same location.
Other species and complete visual/compositor acceptance remain unfinished.

Validation: debug and instrumentation builds, JVM tests and debug lint passed.
Installed on 192.168.1.160:43041 preserving data. FloatingRestTest and
AutonomousSleepStateTest passed (four tests, 0.053 seconds). Tests exercise real
drift helpers, readiness, decreasing speed and reduced-motion ghost opacity at a
fixed position; they do not establish overnight or full visual acceptance.
