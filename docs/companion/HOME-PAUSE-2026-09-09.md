# Home scene pause survives asynchronous work

`HomeSceneView.pause()` previously removed the current callback without remembering that updates were paused. A subsequent `loadPet()` completion, editing-mode change or reattachment could schedule the animation again while the caller still expected it to remain paused.

The view now stores explicit pause state and separates internal callback cancellation from lifecycle pause. Only `resume()` clears an explicit pause. Attachment and window visibility still restart an eligible scene, while editing cadence changes preserve pause state.

The instrumented regression uses the debug home laboratory as a host, without changing the user's database. It pauses a scene, detaches/reattaches it, toggles visibility and editing, and loads Ginger again. It requires stable animation time and no scheduled tick while paused, then advancing time after explicit resume. This test and existing scene/decoration tests passed: 12 tests in 5.212 seconds. Debug app/test assembly and lint passed. Logs: `evidence/home-pause/`.

This is a lifecycle correctness fix. It does not establish battery savings or replace the required prolonged physical-device performance test. The accompanying emulator samples are a short diagnostic of the Corgi home preview, not a comparison with a historical baseline or a release performance gate.

The 40.22-second sample retained PID 4924. `gfxinfo` recorded 2,397 frames, zero janky frames, p50 7 ms and p99 12 ms. Total PSS samples were 60,320, 51,818 and 51,626 KiB. These three samples show no observed growth in this interval; they cannot exclude a longer-lived leak.
