# Menta travel continuity — 2026-09-08

Menta previously capped every trip at 14 seconds, making long trips exceed the
configured 38 px/s movement speed. Travel now uses the shared quintic motion
curve and derives duration from distance and peak speed, without a duration cap
that overrides the speed limit. Longer trips consequently take longer.

Slither and climb now arrive at the exact destination before choosing the next
action. Both reset movement offsets at arrival. Body waves track eased distance,
their offsets settle at departure/arrival, and slithering keeps a fixed vertical
scale. Startup begins in a coiled idle pose until an actual target is selected,
instead of interpolating from uninitialized zero coordinates.

Validation: debug and instrumentation assembly and all 208 JVM tests passed.
The shared ground-motion tests cover endpoint velocity and peak speed. The new
MentaTravelTest passed on emulator-5580, checking midpoint, destination, stable
scale and settled offsets for slither and climb. These checks do not replace
physical visual review of the full trips and directional artwork.
