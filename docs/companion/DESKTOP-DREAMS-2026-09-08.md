# Dreams during desktop rest — 2026-09-08

The desktop care renderers now show the same thought cloud used by the home
scene. The existing painter is shared as PetDreamPainter; home rendering is
preserved. Desktop placement scales with the actor and clamps the cloud inside
the current window. It requires no extra overlay or touch target.

Corgi uses its dedicated REST renderer; the other fourteen species use their
shared REST renderer once the care scene has contact. Normal motion fades in
the cloud after 1.4 seconds. Reduced motion freezes the cloud and moon motif.
Dreams disappear when REST ends and do not persist during unrelated actions.

Validation: debug and instrumentation APKs built successfully. Ten Android tests
passed on emulator-5580 (7.949 seconds), covering small-window dream bounds,
reduced motion in both surfaces, all-species care rendering and Corgi playback.
The generated Yuki sheet was visually inspected and retained at
`evidence/desktop-dreams/yuki.png`.

This implements dreams during desktop care REST. It does not yet expose sleep
state from every autonomous species controller; dreams during autonomous naps
remain a separate integration task. Physical-device and all-pose artistic
acceptance remain outstanding.
