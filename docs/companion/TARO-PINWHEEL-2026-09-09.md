# Taro pinwheel consistency

Taro's native care pinwheel and the placed decoration previously differed in colors and blade shapes. Native care, the starter home toy and the explicit pinwheel now reuse the same drawing. Taro can equip a placed pinwheel through the shared care selection path.

Only blades rotate around the hub; the support and shadow remain in their own coordinate space. Care spins at 90 degrees/second after play contact unless motion is reduced. Home play keeps a per-object angle, advances it only during active interaction, and retains the final angle on stopping instead of snapping to zero. The starter object delegates its shadow once rather than drawing it twice.

Validation: debug/test APK builds, 233 JVM tests and lint passed. Seven targeted Android rendering tests passed on the phone (11.131 seconds). The pinwheel test compares home/native-care/explicit-selection/starter-object pixels, permits minor raster rounding across coordinate transforms, verifies moving blade pixels and unchanged exposed support/shadow pixels. Its initial support region accidentally included the blade sweep; the corrected region starts below that sweep, and all seven tests were rerun successfully. App installed on the phone.

This establishes drawing consistency and blade/support separation. It does not establish full device visual acceptance of Taro's approach/contact choreography or implement Corgi pinwheel play. Other pet toys and the broader plan remain open.
