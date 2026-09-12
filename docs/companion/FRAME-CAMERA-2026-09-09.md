# Shared frame-camera geometry

Before calibrating Tela's atlas, review found that BaseBehavior applied its per-frame camera correction only while drawing. Alpha hit testing and the ground offset passed to desktop care ignored that correction.

The ground offset now applies the same scaling around `frameGround`. Hit testing inverses that transform before consulting the alpha mask; degenerate/nonfinite camera scales do not divide by zero. Drawing itself is unchanged.

`BaseBehaviorCameraScaleTest` checks a known opaque rectangle inside a transparent atlas with camera factors 0.5 and 1.5 around a 0.93 ground anchor. It checks opaque/transparent hit positions and the resulting care baseline. The existing Corgi scale tests also passed: 4 Android tests total, 0.278 seconds. Debug assembly, Android test assembly and lint passed in 55 seconds. Logs are in `evidence/frame-camera/`.

This is a prerequisite correction, not Tela's finished calibration. Her atlas-building pipeline normalizes source crops by maximum alpha extent (`tools/tela/pipeline/build_atlas_v2.py`, `normalize`), which is not a stable anatomical reference when legs fold or spread. A body/head landmark calibration and sequence review are still required. No new per-frame factors have been activated by this change. This build was tested on the emulator, not installed on the physical phone in this checkpoint.
