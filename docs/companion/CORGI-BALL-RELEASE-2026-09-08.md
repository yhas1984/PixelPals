# Corgi ball release and retirement

The ball no longer remains attached until its window abruptly disappears. After the upright hold, it falls vertically from the actual scaled mouth, makes one damped bounce, rests on the existing rolling ground plane, then fades over the final 250 ms. The overlay window receives this alpha before it is removed. No permanent desktop toy is introduced.

The release samples absolute scene time, so dropped rendering frames do not accumulate physics drift. Bounce duration is derived from the same gravity as the initial fall and a 14% rebound height. Horizontal position and orientation remain continuous. Reduced motion skips the drop/bounce and fades the stationary held ball. Normal playback gains 400 ms; completion/reward remains at the catch marker.

Validation: debug/test APK builds, lint and all 232 JVM tests passed (28 seconds). Nine targeted Android tests passed on `192.168.1.160:43041` (5.789 seconds), without persistent-data changes. New pure tests cover the fall, impact, damped bounce, settling and reduced-motion retirement. The Android integration samples the actual packaged mouth anchors in both directions, checking continuous release, stable rotation, ground contact before fade and zero final alpha. Debug APK installed successfully.

Remaining visual work: Corgi still uses discrete existing head poses; an authored mouth-opening/release pose and anatomical in-between frames are not provided by this physics change. Continuous visual acceptance and the complete 15-pet plan remain open.
