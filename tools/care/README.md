# Visual care — debug candidate

## All-species desktop care rollout (2026-09-04)

The floating desktop care affordance is now enabled for the complete 15-pet
catalog. Corgi keeps its dedicated fetch-window controller; the other fourteen
species use the shared `SpeciesDesktopCare` playback with their own checked-in
care atlas, profile timing, food, toy, touch, wash and rest presentation. The
same icon-only cloud follows the selected pet and hides medicine until the
status engine makes a dose available.

`DesktopCarePlayback.SUPPORTED_PETS` is the single compatibility source used by
the overlay view and service, so adding a catalog pet cannot silently leave its
desktop affordance behind. Completion and cancellation still flow through the
existing coordinator, preserving exactly-once rewards and progress updates.
The rollout changes no release flag: desktop care remains debug-only until the
release assets and product decision are explicitly approved.

Validation for the all-species rollout:

- Debug APK, debug test APK, 185 unit tests and lint pass; lint reports no errors.
- `python3 -m unittest tools.care.test_atlases tools.test_pet_pipeline`: 14 pass.
- All 30 safe instrumentation tests pass on the physical NE2213 at
  `192.168.1.162:40831`. The expanded playback test runs all six actions for
  every non-Corgi pet in both directions and reduced motion, including
  cancellation and unavailable medicine.
- Physical review sheets for every species and a 15-pet cloud menu were
  inspected from the device output under `/tmp/pixelpals-care-review-all/`.

## Diablillo trident balloon game (2026-09-04)

Diablillo's previous ember-juggling play has been replaced in both the care room
and floating desktop with a literal balloon game. Three balloons bob around the
pet; Diablillo prepares its trident, thrusts at each target in order, shows a
colored ten-ray pop, and raises the weapon after the third hit. The compatible
two-eye reaching pose (frame 5) is held throughout, so both hands stay on the
trident; malformed frames 0, 4 and 8 remain forbidden. The 4.2s presentation
commits the play effect exactly once at 3.6s. Reduced motion keeps the same three
readable states without animated bobbing or flashing bursts.

`ImpBalloonPlayMotion` owns the deterministic three-strike timing independently
from `ImpBalloonPlayPainter`, which draws the balloons, strings, trident and
bursts with native Canvas primitives. The weapon rotates around a calibrated
frame-5 hand grip rather than around the torso or target. A dedicated regression
asserts that its shaft crosses that grip at every strike and final pose. The
central target and burst radius are bounded for both the compact 320x220 room
and the larger desktop overlay. No raster pet assets were generated or modified.

Final validation for this follow-up:

- Debug APK, debug test APK, 185 unit tests and lint pass.
- `python3 -m unittest tools.care.test_atlases tools.test_pet_pipeline`: 14 pass.
- All 30 allowlisted safe care instrumentation tests pass on the physical
  NE2213 at `192.168.1.162:40831`, including normal/reduced motion, exact pop
  timing, room/desktop bounds and the existing Corgi regressions.
- The eight-frame physical-device review sheet was inspected at the preparation,
  three strikes, maximum second burst and raised-trident ending. Review file:
  `/tmp/imp-balloon-trident-grip-final.png` (outside git).
- Debug APK SHA256:
  `e786f0d725bb20015c0dbda444417331fe17739f8481e0cfedb83ab907db8a9e`.
- The final APK was installed with `adb install -r`; existing device data was
  preserved. The temporary instrumentation package is removed after validation.

This remains part of the debug-only care candidate. It does not promote the
care scenes to release.

## Diablillo desktop care pilot (2026-09-04)

Diablillo is now the second floating-desktop care pilot, after Corgi. Tapping the
existing overlay pet opens the same compact thought cloud, but each illustrated
control receives Diablillo's profile: chili, balloons with trident, common hand, sparkle wash
and folded bat wings. Medicine remains absent until the status engine reports
that a dose is available.

`SpeciesDesktopCare` loads Diablillo's checked-in care pack and renders it inside
the existing pet window through `SpeciesCareRenderer`. It reuses the application
`CareSceneCoordinator`, so completion, cancellation and room/overlay exclusion
have the same exactly-once semantics as Corgi. It does not use Corgi's bowl,
cushion, canine poses or external fetch-ball window. The imp pops three balloons
in place; chili fire, gentle petting, foam and wing-wrap sleep are the same
approved species choreography used in the room.

The common `DesktopCarePlayback` boundary keeps Corgi's tuned fetch controller
intact while allowing a species-aware renderer for the new pilot. Care bitmaps
are recycled when playback ends. Drag, keyboard, screen-off, overlay hiding and
configuration changes cancel unfinished playback without applying its effect.
The accessibility description now uses the localized selected-pet name.

Validation for this desktop follow-up:

- Debug APK, debug test APK, 180 unit tests and lint pass (0 errors, 185 warnings).
- `python3 -m unittest tools.care.test_atlases tools.test_pet_pipeline`: 14 pass.
- All 27 safe care instrumentation tests pass across the final emulator runs.
  The new `SpeciesDesktopCarePlaybackTest` covers six actions, both directions,
  reduced motion, unavailable medicine and cancellation with fake effects and
  no database access. Existing Corgi desktop tests remain green.
- The offscreen cloud review confirms five icon-only healthy controls with
  Diablillo-specific food, toy and rest art. Review file:
  `/tmp/diablillo-care-cloud-es.png` (outside git).
- Debug APK SHA256:
  `facf61beb18a3aa5d3a7b1c09f22a203e27b9f36439501de176f0de66da607e1`.
- Physical installation and live approval are pending. The last supplied
  `192.168.1.162:41017` endpoint refused the connection and ADB mDNS found no
  replacement; no device data was removed.

This remains debug-only. It does not enable care scenes in release or add any
other pet to the desktop pilot.

## Diablillo wing sleep, gentle petting and chili fire (2026-09-04)

This follow-up changes only Diablillo's feed, pet and rest presentation:

- The hammock is removed. Its rest icon is now a pair of bat wings. The wings
  attach behind the shoulders, fold across the torso in two seconds and remain
  closed for the rest of the five-second nap. The sleep clip settles upright at
  the clean, closed-eye pose 9, not the curled dog-like sleep poses. Horns and
  face remain visible; the wings share the actor's breathing transform.
- Petting lasts 4.2s instead of 1.8s: two soft hand strokes, at most 2.2 degrees
  of lean, and held contented poses instead of rapid pose alternation. Play's
  three-second pace and all other pets' movement remain unchanged.
- Feeding preserves the original seven fast bite frames at 300ms each, then
  adds an open-mouth fire burp and a smile (3.3s total). The chili is consumed
  before the mouth-anchored orange/yellow puff, which appears from 2160–2910ms.
  Feeding still commits once at 2100ms; the visual epilogue awards nothing.
- Reduced motion shows closed wings and a small steady puff, with the appropriate
  open-mouth pose but no flashing, drifting sparks or moving wings. Unaccepted
  manual feeding and cancelled scenes cannot emit fire.
- Canvas paths/gradients keep the wings and fire light; no raster assets were
  generated or changed. The deterministic atlas builder matches the checked-in
  JSON. Spanish/English manual rest hints refer to wings rather than a bed.

The Kotlin skill guided the pure `ImpCareMotion` clocks and independent wing/fire
painters, with unit and safe offscreen-device regression tests. Those visuals
are now also consumed by the debug desktop pilot described above; they still do
not promote release assets. Earlier validation records below are historical.

## Personality refinement candidate (2026-09-04, follow-up)

The user requested clearer props, less repetitive play, a cloud-like Michi,
a livelier Diablillo and visible washing foam. Native Canvas props and the
existing pose boards are reused; no raster assets were regenerated.

- Michi absorbs water droplets and floats with a rainbow. Its feeding/play clips
  use airy, relaxed poses rather than Ginger's feline nibble and paw/pounce clips.
  A vapor base, soft expansion and translucency reinforce its cloud identity.
- Diablillo holds a red chili. Feeding lasts 2.4s, play 3s and petting 1.8s;
  its 5s nap remains slow. The malformed three-eye and quadruped poses remain excluded.
- Moki still catches a fly to eat, but plays peekaboo with a moving leaf, without
  the feeding tongue sequence. Lumi nibbles purple forest berries and follows a
  star-centered magic orb, with no insect wings. Tela eats a visible green cricket,
  not an unrecognizable silk-wrapped morsel.
- Non-Corgi play alternates three per-pet patterns: direct approach, feint and
  double pass. Anticipation, attempts and recovery drive both the actual atlas
  poses and the toy path. Variation is chosen once per scene, never randomly per
  rendered frame, and cannot change care rewards or completion semantics.
- Shared washing foam builds over the body and rinses away before the ending.
  Reduced motion retains readable foam without drifting bubbles. It is also
  wired into Corgi's existing desktop cleaning; accepted fetch remains unchanged.
- Manual instructions now say toy/bed instead of incorrectly calling every
  species' props a ball/cushion. Menus remain icon-based where already icon-based.

Scope remains the existing room scenes plus Corgi's enabled desktop pilot.
Other pets' desktop entry points have not been enabled as part of this refinement.
No release promotion, version changes or user progress mutations are included.
The Kotlin guidance informed the separation of pure play/wash timing, profile
configuration and Canvas rendering, with isolated tests for each boundary.

Current follow-up validation:

- Debug APK/test APK build, 175 unit tests and lint pass (0 errors, 182 warnings).
- `python3 -m unittest tools.care.test_atlases tools.test_pet_pipeline`: 13 pass,
  including exact metadata/generator timing parity and the new excluded poses.
- On the API 26 `FinAI_Test` emulator, all 19 allowlisted instrumentation tests
  pass in 14.157s: the previous five safe classes plus
  `CarePersonalityRevisionTest`. They do not open or clear the progress database.
- Normal/reduced motion, all three play variants, room/desktop-size bounds,
  manual targets/completion, Corgi regression, menu-size props, lather/rinse and
  existing Spanish/English accessibility gates pass. Review sheets were visually
  inspected; the final rainbow floats beside Michi's head and the imp's ember
  travels beside its right hand rather than crossing its eyes. Moki's ending now
  smiles instead of using feeding's lip-lick pose.
- Review files: `/tmp/pixelpals-personality.aLwlc1/care-review` (outside git).
- Final debug APK SHA256:
  `00691db6094e62ab05e0495697a02f72f9c5c6d4ae6ed3191943e2f30689e145`.
- Physical-device installation and live visual acceptance are **pending**:
  `192.168.1.160:39273` returned `No route to host` and ADB mDNS discovered no
  wireless devices. The user was asked for the current debugging IP/port.
  The APK was installed only in the emulator with `-r`, without deleting data.
  The older NE2213 results below describe the previous revision, not this one.

## Species-specific room care and Diablillo correction (2026-09-04)

`PetCareProfile` now specifies each pet's food, toy, bed, feeding/play/touch/wash
style and temperament-paced motion. `SpeciesCareMotion` is pure and bounded;
`SpeciesCareRenderer` shares the transformed anchors with manual hit testing.
`CareToolDrawable` and the scene use the same native Canvas illustrations.
No font emoji are used as props and no new raster assets were generated.

| Pet | Food | Play | Rest |
| --- | --- | --- | --- |
| Bloop | Mist | Floating bubble | Moon mist |
| Nube Michi | Cloud fish | Pawing yarn | Cloud |
| Jelly | Fruit cube | Bouncing spring | Puddle |
| Corgi | Existing bowl | Existing mouth-fetch ball | Existing cushion |
| Ginger | Fish | Pawing a feather | Basket, curled up |
| Angel | Star morsel | Gliding with a halo | Cloud cradle |
| Patito | Seeds, pecked from the ground | Paddling after a leaf | Reed nest |
| Diablillo | Chestnut, held in hands | Juggling an ember | Rocking hammock |
| Moki | Fly, caught with its tongue | Dangling insect | Leafy branch |
| Yuki | Snowflake | Twirling ice crystal | Snowdrift |
| Piru | Small fish | Sliding ice puck | Ice floe |
| Taro | Lettuce | Slowly following a pinwheel | Moss |
| Menta | Small egg | Slithering by a hoop | Coiled on a leaf |
| Tela | Silk-wrapped morsel | Suspended silk spool | Web hammock |
| Lumi | Light motes | Orbiting firefly | Starlight nest |

The three-eye bug is painted into Diablillo's original pose **8**, not a double
draw. Its idle/petting clip now starts at the clean two-eye pose **3**. Feeding
and play also omit quadruped approach poses **0** and **4**. Moki's play clip
omits the human-like hand catches and instead uses crouch/tongue/retraction poses.
`clip_frames()` in the atlas builder owns these overrides, with regression tests
against the checked-in metadata. Original PNG/source boards remain unchanged;
excluded cells must not be reintroduced into playback.

The stats CTA is **Estadísticas de tu mascota** / **Pet Stats**. Its buttons use
wrap-content height plus their original minimum touch height, allowing large text.

Scope boundary: this change is wired into **El rincón** for all fifteen pets.
The previous **Corgi-only desktop pilot is deliberately unchanged**; the user was
asked whether to open the desktop pilot to other pets, and no response has yet
been received. The generic renderer is tested at desktop dimensions, but this
is not evidence that other pets have an enabled desktop affordance. Release
care assets/flags, progress rules, medicine eligibility, rewards and Corgi's
accepted desktop choreography are unchanged. No production promotion is implied.

Final validation for this revision:

- Debug build, test APK, 168 unit tests and lint pass (0 errors, 182 warnings;
  the six additional lint suggestions concern Canvas KTX helpers).
- `python3 -m unittest tools.care.test_atlases tools.test_pet_pipeline`: 11 pass.
- On NE2213 `192.168.1.160:39273`, 16 isolated instrumentation tests pass in
  48.97 seconds. The explicit class allowlist is `SpeciesCareRenderingTest`,
  `CareSceneAssetsTest`, `CorgiCareCloudTest`, `CorgiDesktopFeedRendererTest`, and
  `CorgiDesktopCarePlaybackTest`. None opens/clears the progress database.
- Gates cover every species/action, manual and automatic completion, full frame
  bounds in both room and desktop-sized canvases, reduced motion, distinct
  illustrated food/toys/beds, medicine visibility, Corgi regressions, Diablillo's
  excluded poses, and Spanish/English stats text at font scales 1.0 and 1.6.
- The six-action review sheets for all 14 changed pets were inspected. That
  review caught face-overlapping props and Moki's tongue direction; both were
  adjusted and the full device gate rerun. The original Corgi renderer is unchanged.
- Final debug APK SHA256:
  `b47cfa424ab00a3ac673f5ab9db59adde1d1327e77967306e26dc5a6cc6ae31b`.
  Installed using `adb install -r`, preserving debug data and the separate
  production package. The auxiliary `com.pixelpals.app.debug.test` package was
  removed after the successful run; it can be reinstalled from the generated test
  APK. PixelPals itself and its progress were not removed. No version change or
  release promotion.
- Live room/launcher visual acceptance is still pending: the phone was dozing
  (`mWakefulness=Dozing`) and its screen capture was black. Offscreen rendering
  on real hardware is not the same as live interaction approval. Review outputs
  and that screen capture remain in `/tmp/pixelpals-species.reGwyH`, outside git.

## Desktop correction: Corgi-only prototype (2026-09-04)

The user clarified that care belongs to the **existing floating desktop pet**, not
another pet in a room/card. The service now exposes a thought cloud for Corgi:
tap Corgi, then choose the illustrated bowl, ball, hand, sponge, cushion or spoon. `PetView` draws either
locomotion or care, never both, with the bowl on the same baseline. No second actor
or care panel opens. The cloud follows Corgi and clamps to screen edges.
The previous room/overlay implementation below is historical work, not the accepted
desktop interaction. Other pets have no new desktop care affordance in this trial.

`CorgiDesktopCare` reuses the exactly-once coordinator. Dragging, hiding, screen
off, rotation, keyboard appearance or detachment cancel uncommitted playback;
an already-started transaction remains application-owned. Release stays disabled.
The user confirmed that desktop feeding looks better, then requested a thought cloud
instead of a named button. The cloud uses a cream surface, softly animated food,
ball and hand illustrations, and 48dp touch targets. The cloud is now
152x136dp, with two rows and a 12-second base timeout. There are no visible names;
Spanish and English screen-reader labels remain. Reduced motion disables icon motion.
All six actions are supported in this Corgi-only debug trial; normally five are shown,
with the two bottom controls centered. Medicine appears only when needed and due.

`CorgiAdditionalCareMotion` provides testable sprite-space choreography for cleaning
(sponge strokes, foam, shake and clean reaction), rest (settling on a cushion and
gentle breathing), and medicine (spoon approach, sip, empty spoon and withdrawal).
Cleaning and medicine last 4 seconds; the nap lasts 7 seconds. Tools stay anchored
to body/mouth/ground points and no second actor or care panel is added.
After the user's positive interaction feedback, the medicine demonstration was removed.
Medicine is absent from both the visual menu and accessibility tree unless Corgi is
sick/recovering and its dose cooldown has elapsed. Unknown, healthy, at-risk and
hibernating states do not expose it. Status updates refresh an open cloud immediately;
a one-second check while open also handles dose availability changing with time.
The click handler rechecks availability and the controller checks the authoritative
snapshot before loading care assets or playing an animation. A rejected dose performs
no animation and applies no effect. An eligible dose uses the normal exactly-once repository path. Other care rules,
feeding, fetch and petting are unchanged. The Kotlin skill guided the separation
of pure motion data, desktop rendering and isolated lifecycle tests.

The user subsequently clarified that play must be a real chase and mouth pickup,
not the previous short pounce in place. `CorgiFetchMotion` now moves the existing
pet window across the desktop at a nominal 2.3 sprite widths/second, using all
four regular walking frames as a running gait. A separate non-touchable ball-only
window rolls ahead. Corgi lowers its head, catches the ball, then lifts it using
the care atlas's mouth anchors. Only one actor is drawn at a time. Direction
selection favors available space; the endpoint remains the pet's new position.
Reduced motion keeps the pet still. Grabbing during fetch cancels immediately.
Feeding and petting keep their previous timings.

Latest checks: 164 unit tests pass; build and lint pass (0 errors, 176 warnings).
`CorgiFetchMotionTest` covers real displacement, gait, both directions, boundaries,
pickup timing, reduced motion, cancellation and exactly-once completion.
Ten safe, non-database instrumentation tests pass on the API 26 `FinAI_Test` emulator
in 1.464 seconds: feeding baseline/bowl, reduced motion, all six action footprints,
every additional pose in both directions, icon-only controls and Spanish/English
accessibility, mouth pickup anchors, and real desktop-controller completion/cancellation
with injected fake effects. New checks exercise hidden/reappearing medicine, centered
five-control layout, healthy/unknown/at-risk/hibernating/cooldown states and zero
rendered care frames or effects for rejected doses. No test clears app data.
Debug APK SHA256: `76d6c660c703282cb7be3abdd4dc6f3cc10206b7804f7b580ebfb7c18e297a1a`.
The updated APK was installed with `adb install -r` on NE2213 after reconnecting to
the user-provided endpoint `192.168.1.160:39273`. The package is
`com.pixelpals.app.debug` (version 20 / 2.3.0-debug); PackageManager confirmed the
update and MainActivity launched successfully. Existing debug progress was preserved;
the production package was not replaced. The ten updated instrumentation tests ran
on the emulator, not on the phone. Live physical review of the new hidden-medicine
menu remains for the user.
The new healthy cloud was visually checked at `/tmp/corgi-cloud-medicine-hidden.png`.
The cloud was previously inspected on the real NE2213 launcher, attached to the same
floating Corgi (not inside an app screen). The actual desktop screenshot is kept
only in `/tmp/corgi-cloud-desktop.png`, not checked in with personal launcher content.
The mouth pickup sheet rendered on NE2213 was visually inspected at
`/tmp/corgi-fetch-mouth.png`. The preceding six-action sheet and cloud were inspected
at `/tmp/corgi-six-actions.png` and `/tmp/corgi-six-cloud.png`. A real launcher capture
at `/tmp/corgi-screen-check.png` shows the existing floating Corgi asleep on its
cushion; after completion WindowManager lists only the original pet overlay.
The user said the interactions feel good and requested conditional medicine visibility.
That change is installed on the phone and still needs physical visual review. Canvas checks do not
establish actual window pacing. The complete fetch was not visually verified live
by the agent in this revision. Personal launcher captures stay outside git.
This is a visual prototype, not approval to promote any care assets to production.

## Status (2026-09-04)

The shared scene controller, coordinator, room ViewModel/panel, bounded overlay
host, illustrated tools and atomic repository completion path are implemented.
There are **15 complete debug care packs, 360 individually padded poses**.
Automatic and direct-manipulation care run in the room and bounded overlay.
This is **not a production-approved release**: NE2213 visual acceptance remains
pending. Existing care remains available when a pet has no care pack.
`CARE_SCENES_ENABLED` is true in debug and false in release.

`source/` contains 15 generated review boards, one per pet, with six action rows
and four poses per row. These are NOT production sprite atlases: the generator
painted checkerboards into RGB images instead of producing alpha. Menta was
corrected to a flat white background; Bloop's missing fourth eating pose was
regenerated. `generation_manifest.json` records the built-in image generation
prompts and corrections. No fallback API or external image service was used.

The user authorized local Python/Pillow processing on 2026-09-04. The pipeline
removes exterior-connected neutral background, preserves enclosed pale anatomy,
finds actual whitespace between irregular rows, removes tiny disconnected
speckles, normalizes scale/baselines and packs RGBA frames. Originals are retained.
`anchors.json` contains reviewed mouth coordinates plus forehead adjustments;
the generated metadata includes mouth/head/body/ground anchors for every pose.
`build_report.json` records source/atlas hashes and transforms. All atlases are
1024x1536 RGBA, 6 MiB decoded, below the 16 MiB care-pack budget.

`review/` contains aligned sheets with anchor marks. `runtime/care-review/`
contains six-action contact sheets rendered by **Android Canvas**, not design
mockups. `runtime/care-ui/` contains actual emulator screenshots of the room and
floating tray. The generated pose boards and original prompts were produced
with the built-in imagegen tool, not a fallback API. Kotlin implementation
follows the `kotlin-development` skill's separated controller/host/test structure.

## Remaining verification and promotion

1. Physically review every pet/action, especially pale sprite edges, prop contact,
   manual strokes, large fonts, TalkBack, Spanish/English, reduced animation,
   landscape, keyboard, lock/unlock, pet switches and overlay visibility policy.
   Automated Canvas coverage is not equivalent to human visual approval.
2. Obtain explicit approval before copying any care assets to main/release or
   enabling the release flag. No version change, publication or commit was made.

## Rebuild and verification commands

```bash
python3 tools/care/build_atlases.py
python3 -m unittest tools.care.test_atlases tools.test_pet_pipeline
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --offline
```

The debug lab `com.pixelpals.app.debug.CareScenePreviewActivity` accepts `pet`,
`action`, `manual` and `soak` intent extras. It never accesses the repository or
awards real care. `soak=true` cycles pets/actions for 30 minutes, alternating
automatic/manual rounds. It cancels if backgrounded. `monitor_soak.py` checks
process continuity and records PSS, heap, active bitmap bytes and completion
counts. This is a **render/input soak**, not a physical overlay/service soak.

```bash
adb -s emulator-5554 shell am start -n com.pixelpals.app.debug/com.pixelpals.app.debug.CareScenePreviewActivity --ez soak true
python3 tools/care/monitor_soak.py --serial emulator-5554 --output tools/care/runtime/soak.json
```

## Care metadata contract

Pet IDs use `PetType.name.lowercase()` (including `nube_michi`). Atlas fields
are the existing `PetAtlasSpec` schema, with 24 contiguous frame entries and
non-looping clips named `feed`, `play`, `pet`, `clean`, `rest`, `medicine`.
Rows use that same order. Each clip contains its actual frame sequence and
`frameDurationMs`; no per-pet frame timers belong in Kotlin.

Add `careActions` keyed by clip name, each containing `completionMs`, strictly
positive and at or before the clip duration. Total automatic durations are
4000, 5000, 3000, 4000, 5000 and 3000 ms respectively. Put completion at the
meaningful final action before the ending reaction (normally 80–90% of clip).

Add `anchors`, an array of 24 objects, one per frame. Each contains `mouth`,
`head`, `body`, `ground`, each a normalized `[x, y]` point inside the frame.
Decoded atlas budget is at most 16 MiB. `CarePoseLoader` checks dimensions and
requires a bitmap with alpha before displaying it. Do not recycle a bitmap
still referenced by the render thread.

## Validation already run

- Debug build and the complete 140-test unit suite pass, including 15 pure scene
  controller/coordinator tests (exactly-once completion, strokes, invalid release,
  fixed ball target after release, cancellation, competing hosts and commit detachment).
- Debug lint passes with zero errors (171 warnings, including existing app warnings,
  KTX suggestions and debug-lab text). It uses the existing default-English / `values-es`
  resource convention. Do not introduce an empty `values-en` directory: it
  causes misleading missing-translation errors for the default English strings.
- Six Room integration tests pass on the API 26 emulator (`FinAI_Test`),
  using an in-memory database and uniquely namespaced preferences. They cover
  care effects, daily rewards, affection cooldown, medicine restrictions,
  maximum bond, inactive pets and waking from hibernation.
- Android Canvas tests exercise 15 x 6 x 2 combinations, including reduced-motion
  drawing and malformed metadata. Real UI tests cover room automatic care,
  dragging from a button, activity recreation, ownership release, bounded overlay
  care and outside-touch closing. Existing Moki/Taro tap tests also pass.
- Six Android result-formatting tests check real Spanish/English deltas, capped
  values, medicine recovery, waking and healing without displaying a misleading
  negative recovery reset. No zero-point rewards are invented in the result card.
- The final 20-test Android battery passes in 25.215 seconds. The room test also
  leaves and resumes after completion, verifying the panel does not replay care
  or remain stuck on a loading message.
- Ten Python tests pass (four care asset gates plus six shared pipeline tests).
- The final APK completed an uninterrupted **30m 1.691s render/input soak** on
  the API 26 emulator: **386 scenes, 386 completion callbacks, unchanged PID**.
  This includes two full automatic/manual passes over all 15 x 6 combinations.
  The 46 PSS samples range from 38,729 to 47,174 KiB; each observed active bitmap
  is 6,291,456 bytes (6 MiB). This is not a claim of physical-device performance
  or a 30-minute overlay/service test. Evidence: `runtime/soak.json` and
  `runtime/validation.json`, including hashes of the exact tested APKs.
- The final candidate passes release Kotlin compilation. An earlier care candidate
  completed R8, but `assembleRelease` could not package an APK because the
  environment did not provide `signingConfig.release.storeFile`. Final-candidate
  release packaging/R8 has not been revalidated. This is not a signed release
  deliverable. The merged release assets contain
  no `care_v1` packs and the release feature flag remains false.
- Installed the exact debug APK on the **NE2213 / API 36** with `install -r`.
  Only `com.pixelpals.app` (2.3.0, version code 20) was present beforehand, so
  `com.pixelpals.app.debug` is a separate fresh installation; the habitual app
  and its progress were not replaced, cleared or copied.
- Eight non-database instrumented checks passed on the NE2213 in 16.45 seconds:
  `CareSceneAssetsTest` (all 180 pet/action/mode combinations) and
  `CareResultFormatterTest`. Phone-rendered contact sheets are retained in
  `runtime/ne2213/care-review/`. The temporary instrumentation APK was removed.
- Opened the actual room and verified its six Spanish care controls through the
  UI hierarchy, including disabled medicine for a healthy Corgi. Live feeding
  visual acceptance remains **unverified**: the screen entered Dozing/lockscreen
  and the capture was black (`runtime/ne2213/screen-off.png`), not evidence of
  the animation. No lock or security settings were changed. No physical visual
  approval, release activation or publication has been performed.

Never run the existing destructive `PetCareLifecycleTest` or
`TreasureCollectionRepositoryTest` against the user's installed data. The new
`CareSceneRepositoryTest` does not clear the application database.
