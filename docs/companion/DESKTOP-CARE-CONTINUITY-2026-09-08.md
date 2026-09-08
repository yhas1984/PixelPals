# Desktop care continuity — 2026-09-08

## Implemented

- Care loading preserves the current locomotion pose, orientation and window position. It no longer resets the behavior or forces frame zero before the care pack is ready. In Corgi, frame zero is the drag pose, not a neutral care transition.
- Finishing care lets the behavior choose its resumed pose instead of overwriting it with frame zero.
- Behavior exposes semantic facing. Ginger accounts for its left-facing source artwork, preventing an unintended mirror change when entering right-facing care artwork.
- Care captures the rendered ground baseline once at entry. The reference accounts for source alpha, atlas pivot, atlas/species scale and current vertical transform. Both shared species playback and dedicated Corgi playback use it. This removes the fixed half-sprite downward jump of atlases anchored near their feet.
- Atlas ground bounds are collected alongside existing alpha masks at decode time. Legacy image bounds are inspected only on care entry, not each animation frame.
- Auras and floating cosmetics use scale magnitude. Facing left no longer shrinks their size and orbit to the previous 40% clamp.

## Evidence

- Debug APK, Android test APK and lint: successful.
- Current JVM suite: **206 tests**, zero failures/errors.
- Disposable-emulator full suite: **129 tests passed**, 54.239 seconds, no skipped assumptions. Overlay permission was explicitly enabled on this emulator before running it.
- New tests cover all 15 factory-selected renderers: semantic facing and ground-baseline agreement with actual rendered pixels within two raster pixels.
- Attached-window test uses a deferred fake care player, proving pose/position stability while loading and preserving the controller-selected completion pose. No care rewards are granted by that fixture; it runs only on the disposable emulator.
- Cosmetic raster test compares both facing directions and asserts that visible aura/float pixels remain identical.
- NE2213 at `192.168.1.204:41701`: latest APK and render test APK installed successfully with data-preserving updates. Five explicitly selected render-only tests passed in 2.885 seconds. No migration, repository-reset or real-care tests were run on the phone. The app was reopened afterward.

The emulator initially terminated with QEMU exit code 139. Its process was confirmed stopped before restarting. The successful complete run above is from the replacement emulator, not inferred from the interrupted process.

## Open visual work and next acceptance

This batch does not add or replace artwork. Existing poses can still differ in anatomy/camera scale; a correct runtime baseline does not prove those drawings form a natural transition. Continue with Corgi original-art gait/turn contacts and per-species missing turn/wake frames, retaining all 15 pets and both desktop/home coverage from the original plan.

The current care renderer does not carry the equipped tint into care artwork, and the desktop care branch bypasses aura/float drawing. These were identified by source inspection and remain to be corrected and visually tested. Commercial previews must faithfully match equipped appearance during interactions.

Per-species visual review, care contact review, long-running physical lifecycle/performance tests, final release candidate and real Play purchase/restoration acceptance remain open. These test results do not certify the entire transformation or authorize production publication.
