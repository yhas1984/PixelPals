# Species-aware falling shape

The shared desktop physics path stretched every non-Corgi pet to 115% height and 90% width and reset the horizontal sign, causing a facing reversal for mirrored sprites. BaseBehavior also applied generic stretching to falls/jumps and discarded the sign on dragging.

- Rigid species keep unit scale through shared falls. Jelly permits up to 15% elastic height; Bloop and Nube Michi up to 4%. Stretch depends on vertical speed in body lengths and approaches its target exponentially over 80 ms, rather than snapping on the first frame.
- Reciprocal horizontal scale preserves apparent 2D area and the existing facing sign. Reduced motion disables deformation.
- Base drag/fall/jump fallbacks keep facing and rigid proportions. Dedicated species animations retain their overrides; this is not a rewrite of all locomotion or artwork.
- Pure tests cover all species, reduced motion, bounded gradual stretch, relaxation and time-based response at 30/120 FPS. The real PetView physics test covers all 15 types in both orientations during the falling phase, including rigid scale and apparent area.

PetView construction launches repository status refreshes, so its integration test is now explicitly emulator-only. The earlier physical Corgi physics note has been corrected to distinguish routine status refreshes from deliberate progress resets. This turn's full PetView tests use the disposable emulator.

Remaining: visual review of actual falls, anatomical in-betweens and species-specific impact poses. Conserving a 2D bounding scale is not proof of constant anatomical volume or complete natural motion.

Validation: debug/test builds, lint and 237 JVM tests passed (zero JVM failures/errors/skips). Final instrumentation/lint check also passed after adding the emulator guard. Full Android suite on emulator-5580 with overlay permission: 177 tests passed in 75.927 seconds. This includes the integration loop over all 15 pets in both directions. No full-PetView test was run on the user's phone in this turn.
