# Overlay permission revocation — 2026-09-09

The desktop service previously checked overlay permission when attaching its window only. A later revocation could leave its enabled preference and service lifecycle active after window removal or failed updates.

`PetService` now checks permission before each foreground polling cycle (normally every four seconds). Revocation disables the desktop pet, cancels its reminders, removes the care/pet/auxiliary overlays and stops the foreground service without scheduling another poll. Independent auxiliary cleanup failures no longer prevent the remaining overlays from being released.

## Verification

- Debug assembly and lint passed (1m 7s).
- Android test APK assembly passed.
- `PetOverlayPermissionTest`: 1 test passed on disposable API 26 emulator in 6.085 seconds. It grants permission, starts the real foreground service from a resumed activity, verifies its enabled state, revokes permission using AppOps, and checks both service shutdown and disabled persistent preference. The test restores its previous permission/preference in `finally` and is emulator-only.
- `git diff --check` passed.
- Physical NE2213 API 36: the app opened and its Tela home was visually inspected before installing this fix. This was a static layout check, not acceptance of animation continuity. Permissions on the physical phone were not revoked.
- Updated debug APK installed with `install -r` successfully, preserving data. The subsequent `am start -W` returned a timeout; a follow-up check found the process alive, no AndroidRuntime error in the sampled log, and a screenshot confirmed the Tela home displayed after installation. The timeout is retained in the evidence rather than reported as a successful timed launch.

Build and test transcripts: `evidence/overlay-permission/`. The regression does not prove every Android vendor's revocation behavior, an active care's cancellation semantics, or all denied-permission onboarding paths. Physical motion/asset review and release/Play validation remain separate outstanding gates.
