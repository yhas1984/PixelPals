# Companion transformation

## Repository separation

`legacy/pixelpals-classic` preserves commit `fadd683`, the application before this transformation. `feature/pixelpals-living-companion` is the development branch for the new experience. The original `codex/pet-runtime-yuki-v2` branch remains unchanged. The previously uncommitted 2.4.0 / 21 version bump is retained in the new branch; the legacy branch has the version recorded in the original commit.

These branches separate source history, not Android storage. Do not install the legacy APK over a migrated companion database: the old application cannot read schema 10. Use separate test installations or a disposable emulator when comparing versions. No production publication is implied by either branch.

## Product contract

PixelPals keeps its 15 companions, Kotlin/Views, local Room storage, existing wallet, entitlements, app-open advertising and store banner. No account, backend, subscription, multiplayer, video capture or new real-money SKU is introduced.

- Home is an interactive 2D room; settings are a separate activity. Pets, Adventures and Store complete the four root destinations.
- Each pet has a persistent nickname, room environment, object placement and interaction history. Existing status, bond and memories remain authoritative.
- Three environments and 24 vector-drawn decorations share the same artwork across the room, previews and postcards.
- Three trips last 5, 15 and 30 minutes. One journey is allowed at a time. Care is unavailable for the travelling pet, its status is frozen and its overlay is paused. Return is claimed explicitly; cancellation grants nothing.
- A journey grants a catalog treasure through the existing treasure reward transaction and a route-specific decoration. Repeated journeys retain normal treasure inventory/rewards; decorations are owned once.
- Return restores desktop presence only if it was active at departure, the same pet remains selected and overlay permission is still available.
- Care continues to use the species-specific pose packs and existing completion coordinator. Automatic room interactions are presentation only; they never farm care rewards.
- Desktop companion objects were removed at the user's request. Care is accessed through the existing cloud; room decorations remain usable. Stored desktop selections are retained as inert compatibility data and never create an overlay.
- Postcards export only the app-rendered scene through a scoped FileProvider cache path. They do not capture the screen or other applications.

## State and boundaries

`CompanionRepository` owns home/placement/inventory/travel/journal transactions. Currency spends and treasure rewards go through `PixelPalsRepository` on the same Room database. `CompanionViewModel` exposes observable state and keeps user commands alive across view recreation.

Schema migrations are additive: 8 -> 9 adds the companion tables; 9 -> 10 adds the desktop-resume flag and preserves data from the first internal preview. There is no destructive migration fallback. The original schema files are retained for migration fixtures.

Travel uses monotonic uptime within one boot and bounded, nonnegative wall-time progress across boots. Moving the wall clock within one boot cannot accelerate the trip. There is no server-backed protection against device clock manipulation after reboot.

The home motion controller approaches targets continuously, decelerates and dwells at toys/beds. Species tempo and learned play/touch balance affect movement; bond affects greeting delay. Preferences and favorite object are shared through Room, rather than copied into a second wallet or status model.

## Art direction

Warm cream surfaces, forest-green actions, dark neutral text, serif companion names and simple readable body text. Room artwork uses layered gradients, a framed window, woven rug, plants and individually drawn objects. Garden and night shelter use layered landscapes. The existing pet artwork is retained; new scene artwork is code-native Canvas drawing, not raster placeholders or generated replacement pets.

Rendering pauses when hidden. Static adventure illustrations refresh only infrequently. Reduced motion removes autonomous home travel and quiets idle desktop motion while preserving direct manipulation. Sound and haptic preferences affect care feedback.

## Build and review boundary

The pre-existing version change (2.4.0 / 21) is preserved.

- Debug includes desktop care and the new companion experience.
- A full release candidate can be built with `-Ppixelpals.companion.releaseCandidate=true`.
- The ordinary release desktop-care default remains disabled until visual approval. This switch is not a deployment or publication mechanism.
- Signing uses the existing secure release configuration. Production publishing and Play test-track purchases require separate external validation/approval.

## Verification commands

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest tools.test_pet_pipeline tools.care.test_atlases
python3 tools/validate_pet_assets.py
./gradlew :app:assembleRelease :app:bundleRelease -Ppixelpals.companion.releaseCandidate=true
```

Run device instrumentation serially. Use an explicit ADB serial and `com.pixelpals.app.debug`; do not substitute a previous run's results for the current tree. The companion scene test writes `companion-catalog.png` to the debug app's external files directory for visual review.

Final release acceptance requires current-tree device interaction, all-species care review, uninterrupted soak, signing validation and real Play purchase/restoration verification. Emulator results and the debug billing simulator do not establish those external gates.
