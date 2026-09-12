# Consolidated validation after care and home continuity changes

This checkpoint validates the current uncommitted worktree on top of `d1c1b72e2b4e621aab3574a28c0b2a133a3fef7a`. APK hashes and a source-content fingerprint are recorded in `evidence/current-state-validation/artifacts.json`; the commit alone does not identify this candidate.

## Executed gates

- Full Android instrumentation: **183 tests passed in 66.428 seconds**, using the disposable `FinAI_Test` emulator on port 5580. No physical-user database was used.
- Production asset validator: **280 frames across 15 pets passed** coverage, metadata, visibility and the applicable duplicate checks. Atlas validation does not establish anatomical consistency or detect every possible duplicate.
- Care atlas contract suite: **8 tests passed**, including all 15 transparent 24-frame packs and padding checks.
- The preceding build of these unchanged APKs passed debug app/test assembly, **245 JVM tests with no failures, errors or skips**, and debug lint. Its log and current JVM report totals are preserved with the instrumentation evidence.

This is the first full Android run after the recent spoon-contact, fixed prop-side, Jelly calibration/dream-padding and editor-pose changes. It supersedes the previous 179-test full-run evidence for this source state.

## What the passing suite establishes

The exercised cases include migration fixtures, repository transactions, care completion, all-species software rendering/contact/clipping, home editing, onboarding recreation, postcard export and localization checks. Examples inspected in source: the v8 migration fixture preserves wallet, purchased entitlement, health and treasure values; the v9 fixture preserves a named home and ongoing journey. These are seeded cases, not proof for every historical user database or device configuration.

## Outstanding visual defect identified during this pass

`SpeciesCareMotion.resting` leaves Jelly's PUDDLE transform at scale 1.07/0.93, Menta's WARM_LEAF width at .975, and Bloop's MOON_MIST alpha at .85 at progress 1. The shared `CarePoseSpec.getFrame` clamps to the last rest clip frame. These terminal effects need to recover in coordination with an actual waking sequence before handing back to ordinary desktop behavior. Merely resetting the transform in the last frame would introduce another snap. This pass records the defect; it does not claim to fix waking artwork.

The catalogue handoff review still identifies identity/style differences and missing intermediate poses. Continuous visual review, final drawings, physical performance/battery/long-duration testing, denied/revoked-permission scenarios and real Play test-track purchases/restoration remain required. No production promotion is implied by the passing tests. The latest physical endpoint remains unreachable; this candidate has not been installed on that phone.

Logs and artifact identity: `evidence/current-state-validation/`.
