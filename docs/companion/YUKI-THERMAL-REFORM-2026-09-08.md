# Yuki thermal reformation

Cooling previously switched straight from the melted transform to idle. The thermal
brain now reverses its melt timeline, selecting the existing atlas poses backwards
while restoring scale with the same smooth curve. The timeline is bounded to .96s
so prolonged heat cannot cause prolonged recovery. A renewed hot reading reverses
direction from the current pose; pause/resume preserves the current deformation.
Battery thresholds remain 40 C enter / 38 C exit. No ambient measurement is claimed.

PetBrainResult now optionally supplies playbackSeconds; PetAnimationPlayer seeks
only when requested. Other brains retain automatic playback. Yuki tests exercise
reversed frames, scale continuity, paused recovery and reheating without a reset.

Validation: debug JVM suite and assembleDebug pass. Physical visual acceptance is
still pending; this reuses existing poses and does not add hand-drawn recovery art.
