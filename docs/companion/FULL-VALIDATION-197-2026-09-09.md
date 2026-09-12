# Current full validation — 197 Android tests

The current debug app and test APK passed all 197 Android instrumentation tests in 81.251 seconds on the disposable FinAI_Test emulator, with zero failures or skipped assumptions. Overlay permission was granted on the emulator so desktop transition and overlay UI tests actually executed. All 249 JVM tests also passed after the final production change. Debug/test assembly and lint passed.

Artifact hashes, source fingerprint and exact runner status counts are recorded in `evidence/current-full-validation/artifacts.json`. Logs distinguish initial failures, isolated reproduction and the final successful complete run.

## Defect discovered and corrected

The first full run failed scrolling to the classic dashboard's collection button. An isolated run passed. The test was strengthened to wait for exact data and completed layout, click the button and verify the actual album, including its asynchronous data load.

The strengthened full run exposed a click at y=2336 on the 1080x2400 emulator, rejected as injection outside the app. The dashboard used system-bar padding inside a full-screen ScrollView; scrolling could place the control over the system navigation area. `applySystemBarsInsets()` now applies system-bar margins to the viewport itself, preserving its design padding. The album tests and then the entire 197-test suite passed with this correction.

## Scope and remaining acceptance

This run includes the recent Ginger/Menta body-scale, Patito flight/boundary, species wake recovery, migration matrix and home accessibility work. It does not prove anatomical consistency of all source frames or replace continuous physical-device visual review, battery/performance soak, signing, or Play-track billing/restoration checks. Production promotion remains outstanding.

The user provided a new phone endpoint during validation. Installation of the preceding validated motion APK succeeded with data preserved, and MainActivity reported a successful cold launch. A subsequent screenshot was black while the phone reported Dozing, so no new physical visual acceptance is claimed from that capture.

After the final full pass, installation of the APK containing the dashboard-insets correction also succeeded on that phone using `install -r`.
