# USB identity and companion validation — 2.5.1

This report records the identity/profile changes, the replacement Diablillo
atlas and the exact validation completed before and after that asset change. It is a review handoff, not a release or Play publication
approval.

## Product changes

- The companion owner name is optional. The user chose “You” on the physical
  device. The dialog keeps its draft through recreation, validates and normalizes
  the local name, and lets the user remove it later.
- Greetings use the optional owner name while the first home entry preserves the
  selected pet and does not rename it. Corgi remains the default entry only when
  no selection exists; existing Corgi homes and progress are retained.
- The dashboard care scene now reads the selected pet's persisted home
  environment, including Diablillo, and applies it to the care background.
- The debug application label is differentiated as “PixelPals Test” in English
  and “PixelPals Pruebas” in Spanish so its separate storage is not confused with
  the release application.
- Diablillo's care artwork is replaced by a new 4×6 atlas with 24 poses and a
  palette correction based on the original reference. The atlas is transparent;
  post-import checks and visual evidence are recorded below.

## Evidence

Identity and dashboard evidence is under
`docs/companion/evidence/identity-2.5.1/`, including:

- `owner-question.png`, `owner-question.xml`, `emulator-owner-stable.png` and
  `release-owner-prompt.png` for the optional owner flow;
- `welcome-new.png`, `selected-debug-home.png`, `diablillo-dashboard.png` and
  `release-updated.png` for greeting, selected-pet and dashboard behavior;
- `diablillo-palette-dark.jpg` and `diablillo-palette-comparison.png` for the
  new palette/reference comparison;
- `final-ui-api35.txt`, `desktop-regression-api35.txt` and
  `final-physical-render.txt` for the recorded test sessions.

## Validation boundary

The following results were recorded before the new Diablillo atlas was imported:

- 340 JVM tests passed.
- 10 API 35 identity/dashboard tests passed.
- 19 API 35 physics, touch and overlay-permission tests passed.
- 21 read-only render checks passed on USB Android 16.

The final tree also passed 340 JVM tests, 62 Python tests and debug/release lint
with zero errors (250/224 warnings). Build log: `diablillo-final-build.txt`.

The post-import USB run passed all 19 cases in `diablillo-usb-corrected.txt`:
asset contracts, species care rendering, desktop playback, Diablillo feeding,
petting, folded-wing rest and trident/balloon play in normal/reduced modes.
The preceding run caught a clipped balloon burst at 2400 ms; the second balloon
was moved inward and the complete 19-case set passed after that correction.

The 24 imported frames were reviewed on light and dark backgrounds, including
face/eye anatomy, alpha edges and calibrated mouth/head/foot contacts. Android
render exports `diablillo-device-care.png`, `diablillo-device-expressions.png`
and `diablillo-device-play.png` show the six care actions and timed sequences.
This is evidence for this replacement, not a claim of perfect art across the
entire catalogue.

The real Diablillo five-minute expedition has survived a force-stop in the
recorded flow. The return was collected and the collection button stayed absent after another
force-stop and reopen (`expedition-claimed.png`, `expedition-persisted.png`).
This UI check does not independently inspect the database reward ledger. This handoff does not claim a 30-minute soak, battery comparison or
real Play purchase/restoration validation.

## Reproduction scope

Use the release package and the debug package as separate installations when
checking identity. Their selected pet, owner name and companion database are
separate. Instrumented USB render tests use isolated scene controllers, without changing
the user database. Manual release UI checks use real care actions and therefore
retain their normal care/bond effects. No fixture resets were run on the phone.

The signed release APK was installed with `adb -s dd68c88b install -r`.
The saved owner name You, selected Diablillo, home and expedition return persisted.
`release-home-after-ad.png` and `release-diablillo-play-*.png` record the actual
release UI after dismissing the existing opening ad.
