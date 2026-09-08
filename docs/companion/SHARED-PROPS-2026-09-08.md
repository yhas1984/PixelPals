# Shared household / desktop artwork

The base ball and linen cushion now delegate to CarePropPainter, the same drawing
used by Corgi desktop care. Home, decoration previews, shop previews and postcards
therefore share the actual geometry/colors rather than independent approximations.
Other catalog variants retain their identity; per-species toy/bed selection and
variant propagation into desktop care remain outstanding. This is the first
integration step, not completion of the all-object consistency request.

Also, scheduled home rest approaches an available bed using the existing gait,
anticipation and braking, then holds REST for the schedule duration. Reduced motion
rests in place. Removing a bed during approach or ending the schedule cannot trap
movement. Focused home-depth and scheduled-motion JVM tests and debug build pass.
Physical visual acceptance remains pending.

## Species starter objects

Home and placement previews now pass the selected species into CarePropPainter;
shop previews and labels do the same. Non-Corgi starter labels describe the species'
toy/resting spot, rather than calling snowballs or webs a honey ball/linen cushion.
Other purchased decoration names/designs remain intact. Diablillo's wing wrapping
stays body choreography: the furniture/icon path uses a cushion, never detached wings.

Debug APK and instrumentation APK build successfully. Installed both on
192.168.1.160:43041 with install -r. SharedPropArtworkTest passed on the phone
(3.464 seconds): all 30 starter objects compare opaque pixels exactly against the
care renderer and contain visible artwork. The test does not change app persistence.
This establishes common artwork, not full pose/contact/choreography acceptance.
Propagating selected purchased variants into desktop play/rest remains outstanding.
