# Ground locomotion follow-up — 2026-09-08

## Installed changes

- Shared `GroundGait` provides a quintic travel curve with zero velocity and acceleration at the endpoints. Duration accounts for the curve's peak derivative, so long trips do not exceed the intended maximum speed.
- Piru's production controller advances walking frames and waddle from distance travelled. It no longer cycles its feet at a fixed time rate while the body slows down, or stretches vertically during walking. Peak walking speed is proportional to sprite size.
- Yuki's active **Runtime V2** already has distance-based frame playback. Its brain now accelerates/decelerates through the shared travel curve while retaining its existing 70 px/s maximum. Walking no longer uses the time-driven idle stretching/floating transform. Its inactive legacy controller remains unchanged.
- Original artwork is preserved. This batch does not add frames or accept missing transitions as completed.

## Validation of this tree

- Debug APK, Android test APK and lint: successful.
- JVM: **206 tests**, zero failures/errors. New checks cover peak speed, smooth endpoints, density/refresh-independent gait and Yuki's active runtime.
- Full disposable-emulator instrumentation: 125 reported, 124 passed and one overlay permission assumption skipped, 47.480 seconds.
- Granted SYSTEM_ALERT_WINDOW on that emulator and reran `CareSceneOverlayUiTest`: **1 passed**, 5.873 seconds. Thus all 125 unique tests were exercised successfully across the two runs.
- New Android Piru regression checks the factory-selected controller's actual window displacement against the speed limit and confirms it walks. Full-catalog smoke also passed.
- Initial test attempt with Yuki selected through the factory exposed that Yuki uses Runtime V2, not the legacy controller. The unused-controller edits were removed and the active-runtime path was corrected and verified before installation.
- Before this gait follow-up, all nine render-only desktop/home tests passed on the physical NE2213 at `192.168.1.204:41701` in 4.905 seconds. The user-selected Yuki was observed on the desktop. The final gait APK was then installed successfully with data-preserving `adb install -r` and opened.

## Still outstanding

Physical acceptance of each gait and transition, all 15 pets' care contacts, dedicated missing art, prolonged lifecycle/performance review, store presentation polish and real Play purchase/restoration verification. No production publication or whole-plan completion is claimed.
