# Reference animation V3 — 2026-09-08

## Implemented

Corgi has a 28-frame candidate bank: 4 idle expressions, 4 paw-play poses, 4 settling poses, 4 waking poses, 8 walk poses and 4 turning poses. Taro has 12 new quadruped play/settle/wake drawings, retaining its existing 8 walk and 4 turn frames. The 52 packed frames live exclusively under `src/debug/assets/companion/pets`; release and desktop/care atlas routing is preserved.

The shared home controller now chooses observation, exploration, play or rest using energy, illness, curiosity, learned play preference and bond. A repetition penalty reduces repeated activities. Absent toys cannot trigger play; an exhausted or ill companion rests without granting care or changing persistent needs. Waking has its own transition before movement. Reduced motion shows a settled rest pose in place when needed. Toy response follows the selected play clip's contact phase.

The debug preview includes Rest/Wake controls that only change preview inputs, plus a fixed seed for reproducible behavior. Production uses normal randomness. The existing care and desktop frameworks were not replaced.

## Asset preparation

Sources and the import script are checked in. `python3 tools/companion/build_reference_assets.py` uses the existing Corgi background extraction, removes a one-pixel matte fringe, uses one scale across each action bank and grounds every frame. Corgi's generated sheet has uneven gutters; its inspected crop boundaries are explicit. Taro's first generated board was rejected for touching/clipped cells; only the second source was imported.

`python3 tools/companion/audit_motion.py` checks all candidate cells for empty content, clipping, floor contact and contiguous metadata, and writes the 15-pet routing inventory. Missing metadata initially failed Android loading; the importer and validator were corrected before the final run.

## Acceptance boundary

These are review candidates, not completed artistic acceptance for the whole catalogue. Remaining work includes care-specific contacts, desktop integration, movement review and dedicated drawings for the other 13 pets. Reversing existing sleep frames is a fallback, not a substitute for a designed waking sequence. The asset inventory explicitly identifies idle-as-turn fallbacks.

## Verification

- 203 JVM tests passed; debug compilation, Android test compilation and lint passed.
- 52 atlas cells passed the importer checks; the 15-pet route audit is recorded separately.
- V3 was installed with `adb install -r` on NE2213 at `192.168.1.204:37773`. All 5 non-destructive `CompanionSceneTest` tests passed in 3.035 seconds after the planted-paw and dream changes. The final Corgi home sheet was inspected with the rear paws planted in idle and the dream cloud visible at rest. Taro was also inspected in the preceding physical review.
- The full emulator suite initially exposed missing metadata (fixed), then the old assumption that a random pet must choose play within a fixed interval (review now explicitly selects the path). A final full-suite rerun was interrupted by a QEMU failure before producing a result. Do not report 119 passing tests for V3.
- Release Kotlin compilation was checked during implementation, before the final review-only helper. No signed V3 release or production upload was made.
- Physical scene success is not full care/desktop/lifecycle/performance acceptance. The new atlas artwork remains debug-only.

## User feedback incorporated

The first candidate repeated a lifted rear paw in Corgi's idle. Its idle and walk sources were replaced with planted-foot drawings; play return and wake completion reuse that corrected stance. The previous sources remain only as reproducible source history, not as the routed idle/walk frames.

All home pets now show a small dream cloud after settling into rest, with moon/stars, heart or toy motifs. It fades in, drifts gently and disappears on waking. Reduced motion freezes the motif and position. English/Spanish accessibility descriptions update only when the dream state changes. Desktop dream effects are not included.

Blender was discussed as a possible rigged production workflow, not adopted or installed. No 3D migration is included in this change.
