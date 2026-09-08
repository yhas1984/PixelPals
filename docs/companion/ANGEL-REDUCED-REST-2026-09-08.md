# Angel reduced-motion recovery handoff

Reduced motion suppresses idle-controller updates, so an Angel in RECOVER could
remain there indefinitely and never become eligible for scheduled sleep. A rest
request now runs the shared opacity transition, holding the same window position.
While fully invisible, it synchronizes the internal position/target, clears flight
velocity and offsets, and selects the original closed-eye prayer frame (11).
Readiness remains false until the fade has fully restored visibility.

Touch remains under the normal interaction path. Canceling the rest request or
turning reduced motion off restores opacity; canceling before the hidden handoff
preserves RECOVER. Destruction also restores opacity.

The new FloatingRestTest case checks cancellation, the invisible mode change,
unchanged window coordinates and eventual sleep readiness with the correct frame.
This is controller/View evidence, not visual acceptance of compositor timing or a
complete overnight/device-lifecycle test.

Validation: debug and instrumentation builds, JVM tests and debug lint passed.
Installed on 192.168.1.160:43041 preserving data. All three FloatingRestTest cases
passed on that phone (0.034 seconds). No production publication was performed.
