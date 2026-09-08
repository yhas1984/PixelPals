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
