# Companion movement review

This follow-up implements the movement changes on the living-companion branch. It does not certify completion of every external acceptance gate in the original plan.

## Implemented

- All 15 home actors use coherent native sprite banks for idle, locomotion and reactions rather than alternating care artwork with desktop artwork. Legacy clips normalize source camera scale; grounded alpha bounds remain measured only during loading.
- Gaits advance with distance travelled. Anticipation, acceleration, braking and planted turns prevent feet cycling while the actor is stationary. Mid-travel target reversals brake before changing facing. Native turn clips keep their original facing throughout the turn.
- Subtle species motion distinguishes crawling reptiles/spider, waddling penguin/snowman, hovering characters and walking quadrupeds. Reduced motion keeps the scene still and usable.
- Pets approach the side of a toy, face it before playing and produce a small synchronized toy response. Autonomous presentation never grants care rewards. Beds retain depth-aware placement and rest. Sleep clips settle on their final pose rather than repeating the entry animation; reduced motion selects that settled pose immediately.
- Home rendering follows display frames with a 16 ms minimum interval instead of a fixed 50 ms timer. It pauses when hidden. Home no longer loads a second 6 MiB care atlas; actual care panels still use their existing care packs.
- The background painter resets opacity before applying its gradient. A translucent decoration no longer changes the next frame's lighting.
- Desktop Corgi retains fractional pixels, brakes near edges and pauses at turns. Its movement no longer disappears at high refresh rates. Ginger's steps and body movement follow actual travel rather than an independent clock.
- The permanent desktop decoration stays removed. Care remains accessible through the cloud.

## Reproducible review

`HomeMotionPreviewActivity` is debug-only. It previews any catalog pet without selecting/adopting it, changing progress, or granting rewards. Controls switch Corgi/Taro/next pet, restart a cycle, slow time and demonstrate reduced motion. The view is the production `HomeSceneView`, not a separate animation implementation.

```bash
adb -s DEVICE shell am start -n com.pixelpals.app.debug/com.pixelpals.app.debug.HomeMotionPreviewActivity --es pet CORGI
```

## Validation

- 198 JVM tests pass, including distance held during anticipation/rest, braking before reversal, and fractional travel at 30/60/120 Hz.
- 119 Android tests pass on the disposable API 26 emulator in 61.32 seconds on the final tree (2026-09-07). They include all 15 scene loaders, decorated-room progression, floor geometry, stable background opacity, migrations, economy, expeditions and navigation.
- 14 asset/pipeline tests pass; debug build and lint pass without errors.
- Signed release APK/AAB candidate rebuilt successfully; `jarsigner -verify` confirms the AAB signature. No upload performed.
- Final scene sheets for all 15 pets were reviewed after allowing rest to settle.
- Earlier in this follow-up, 3 scene tests passed on the NE2213. The connection subsequently dropped. Those tests predate the final toy response and desktop corrections and are not final-build physical acceptance. The new address `192.168.1.194:38829` returned `No route to host` on 2026-09-07; no final APK was installed on that phone.

## Original plan acceptance still to close

Home, adoption, care, per-pet names/layout/personality, 3 environments, 24 decorations, expeditions, journal/album, postcards and store previews are integrated in the current app. Persistent desktop objects were explicitly removed by the user, superseding that part of the plan.

Full release acceptance still requires final-build physical review of every pet/action and lifecycle/accessibility combinations, a new uninterrupted soak of the revised home/desktop, measured battery/performance comparison, and Play-track purchase/restoration testing. The prior 30-minute care-lab soak is historical evidence for that care build; it is not evidence for this revised movement implementation. No production upload is authorized or performed.
