# Piru slide and jump — 2026-09-08

Piru's slide now accelerates and brakes using GroundGait. Duration derives from
distance and a peak speed of 2.4 sprite widths per second; slide frames follow
distance instead of a separate animation clock. Vertical scale stays fixed.

Jump height now follows a constant-acceleration parabola instead of a sine arc.
Its artwork progresses once through the jump clip instead of looping according
to the unrelated animation clock. Rotation follows jump progress and settles
at landing; inherited offsets are cleared and vertical scale stays fixed.
Original assets are retained.

Debug assembly and all 209 JVM tests passed. GroundJumpTest checks ground/apex,
symmetry, constant acceleration and clamping after landing; GroundGait tests
cover speed limits and endpoint velocity. Physical review of Piru's full slide,
jump and landing artwork remains pending. This is not all-pet visual acceptance.
