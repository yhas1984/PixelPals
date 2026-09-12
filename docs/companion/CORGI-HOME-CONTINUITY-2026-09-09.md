# Corgi home/desktop continuity

## Findings and changes

- HomeSceneView applied a 3.5% whole-body anticipation squash/stretch plus a periodic whole-body breathing scale to Corgi, even after the desktop scale corrections. Corgi now preserves its cell proportions in that path. Other species retain their existing motion.
- The home selected walking contacts at a fixed 105 scene-unit stride; the desktop selected them at 0.44 actor widths. Both now use CorgiGait, with the home body sway and bounce following the same phase as its contacts.
- Desktop action changes and affection clear prior offsets and rotation, avoiding carrying digging or sniffing transforms into the next action.
- Original bitmap files are unchanged. This does not add anatomically drawn in-between poses or close the full catalog's visual acceptance.

## Regression coverage

HomeCorgiContinuityTest renders the actual home actor before and halfway through anticipation at different breathing times and compares the pixels. It also compares each of the four walking contacts against the original rendered artwork at actor sizes 80 and 290. Existing scene and desktop Corgi tests accompany it. The checks are offscreen and do not mutate pet progress.

Final validation: debug APK, instrumentation APK, JVM suite and lint passed (48 seconds). Physical phone: the three named test classes passed all 12 tests in 9.384 seconds. The updated debug APK was installed with data preserved. The full Android suite was not repeated after these final home changes; its preceding 166-test result belongs to the previous revision.
