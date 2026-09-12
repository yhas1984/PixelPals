# Jelly, Menta and Bloop rest recovery

The terminal rest defect recorded in `CURRENT-STATE-VALIDATION-2026-09-09.md` is now addressed for these three pets. The shared renderer uses reviewed existing poses during the final second: resting intermediates 18 and 17, drowsy upright 16, then neutral eyes-open 12. These are existing source drawings, not newly generated frames. The 5-second duration and 4-second reward point remain unchanged.

`SpeciesRestRecovery` coordinates the pose selection, smooth recovery of Jelly/Menta deformation and Bloop transparency, dream fade at waking onset, and bed fade after the eyes open. Room and desktop share that renderer. Other species' rest sequences and scheduled overnight sleep are unaffected. Reduced motion keeps its static pose until completion, then uses the awake endpoint without interpolated body deformation.

The three native Android review strips in `evidence/rest-recovery/rest-recovery/` were visually inspected. They show the sleeping-to-awake sequence and removal of props at completion. This improves the existing-bank exit; it does not certify the subsequent handoff to every ordinary locomotion pose or resolve Menta's palette/identity differences. Additional art and continuous physical review remain required.

Validation:
- Debug app/test assembly, 247 JVM tests, debug lint and diff whitespace checks passed.
- Ten Android tests passed in 7.33 seconds on the final renderer: recovery review, all-species clipping/contact suite, and all-pet care asset/render/completion cases.
- Recovery tests exercise normal/reduced mode, actual selected frames, unchanged duration, one reward, and neutral terminal body proportions/opacity.
- Initial visual strips were generated before a behavior-neutral optimization avoiding an offscreen alpha layer at full opacity; final instrumentation reran successfully after that optimization.
- No physical installation or production promotion was performed.

Final logs: `evidence/rest-recovery/build.txt` and `evidence/rest-recovery/android-tests.txt`.
