# Requested Yuki and decoration changes — 2026-09-08

Yuki's care toy is now a snowball. Shared home/desktop care performs two throws
with a hand-origin arc and dedicated pose timing. The bath uses a watering can
and water instead of a snowflake. A temporary ground-anchored partial melt and
puddle recover before bath completion. Reduced motion disables body deformation
and keeps the ball/water static. Existing bitmap art is retained.

The user chose to retain automatic device-heat behavior. The existing battery
temperature thresholds remain 40 C to enter thermal melting and 38 C to recover;
this is not ambient weather. Yuki's localized description now identifies device
heat. No manual temperature input or network weather service was added.

Home decoration's primary Place action now previews the object in the scene:
tap a destination or drag it there. Existing objects retain drag placement.
Accessible placement buttons remain available using named positions such as
Front / Left, replacing numeric coordinates. Inventory and layout persistence
continue through the same transactional repository.

Placement refinement: the drag preview now snaps to the same nearest slot used
on release. A green/red outline previews availability. An occupied slot does
not submit a placement, and a new inventory object remains selected for another
tap. A toast and accessibility announcement supplement the outline. Debug and
instrumentation builds passed; both touch-placement Android tests passed on
emulator-5580 (0.043 seconds), including retry after an occupied destination.

Validation: 211 JVM tests passed; debug/instrumentation assembly and debug lint
passed. Six Android tests passed after the final throw-origin adjustment,
including touch placement and all-species care rendering. The generated Yuki
care sheet was inspected and the snowball origin refined. A prior full suite
passed 133 Android tests before these requested changes; that earlier result
must not be represented as a full-suite validation of this final diff.

The phone became offline during installation; final changes are verified in
the emulator, with phone installation awaiting reconnection. Further visual
refinement of release/contact frames remains part of the full animation plan.
