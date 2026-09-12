# Corgi care wake and handoff

The desktop rest choreography ended on curled sleeping frame 19 and handed directly to walking. It now reverses existing rest poses after the unchanged reward contact at 5800 ms: frame 18, then 17 at 6200 ms, then seated frame 16 at 6600 ms. The cushion fades over the last 400 ms. Dreams stop for this normal-motion waking phase, and rest no longer scales the whole body to simulate breathing.

The real Corgi controller resumes with a 650 ms seated pause after completed rest and an upright pause after other cares. Position and heading are preserved before accelerating back to walking. Other behavior implementations retain their reset contract. Reduced-motion care retains a stationary pose without the reversed animation.

The original atlas remains unchanged; the four existing poses do not constitute newly drawn anatomical in-betweens. There is still a change from the care illustration to the original seated illustration, requiring visual acceptance. This is not completion of the full 15-pet plan.

Validation: debug and Android-test builds, lint and 234 JVM tests passed with zero JVM failures/errors/skips (47 seconds). The new JVM regression checks wake ordering, fixed body scale and cushion fade without changing reward timing. The desktop controller test checks seated/upright pauses and subsequent travel in both directions.

Physical phone: CorgiScaleRenderingTest and CorgiDesktopFeedRendererTest passed all 10 checks in 3.979 seconds. These are offscreen renderer/controller tests without progress mutations. Updated debug APK installed preserving data. Full Android suite and live sleep interaction acceptance were not repeated for this revision.
