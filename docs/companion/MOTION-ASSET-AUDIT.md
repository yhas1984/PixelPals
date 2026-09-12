# Home asset audit — 2026-09-12

The audit follows the current `HomeLocomotion`, `HomeRestArtwork`, and `HomeLegacyClips` selectors. Base counts are distinct frame indices after clip fallbacks; effective rest is reported separately because reviewed care art and Tela's dedicated rest pose replace the base sleep route. These are structural counts, not quality scores.

| Pet | Debug home JSON / fallback | Release home JSON / fallback | Base counts debug (idle/walk/turn/play/sleep/wake) | Base counts release (idle/walk/turn/play/sleep/wake) | Effective rest debug | Effective rest release default | Effective rest release RC | Care art packaged |
|---|---|---|---|---|---|---|---|---|
| corgi | legacy drawables (forced) | legacy drawables (forced) | 1/4/1/2/2/2 | 1/4/1/2/2/2 | care_v1 4/4 | base sleep 2/2 | care_v1 4/4 | yes (carePreview) |
| taro | app/src/debug/assets/pets/taro/taro_motion_v2.json | app/src/main/assets/pets/taro/taro_motion_v2.json | 4/8/4/2/4/reverse | 4/8/4/2/4/reverse | base sleep 4/4 | base sleep 4/4 | base sleep 4/4 | yes (carePreview) |
| bloop | legacy drawables | legacy drawables | 1/2/1/2/1/1 | 1/2/1/2/1/1 | care_v1 3/3 | base sleep 1/1 | care_v1 3/3 | yes (carePreview) |
| nube_michi | legacy drawables | legacy drawables | 1/2/1/2/1/1 | 1/2/1/2/1/1 | base sleep 1/1 | base sleep 1/1 | base sleep 1/1 | yes (carePreview) |
| jelly | legacy drawables | legacy drawables | 1/4/1/2/1/1 | 1/4/1/2/1/1 | care_v1 2/2 | base sleep 1/1 | care_v1 2/2 | yes (carePreview) |
| ginger | app/src/main/assets/pets/ginger/ginger_sheet_v2.json | app/src/main/assets/pets/ginger/ginger_sheet_v2.json | 1/4/1/2/1/3 | 1/4/1/2/1/3 | base sleep 1/3 | base sleep 1/3 | base sleep 1/3 | yes (carePreview) |
| angel | app/src/main/assets/pets/angel/angel_sheet_v4.json | app/src/main/assets/pets/angel/angel_sheet_v4.json | 4/2/4/4/2/reverse | 4/2/4/4/2/reverse | base sleep 2/2 | base sleep 2/2 | base sleep 2/2 | yes (carePreview) |
| patito | legacy drawables | legacy drawables | 1/2/1/2/1/1 | 1/2/1/2/1/1 | care_v1 3/3 | base sleep 1/1 | care_v1 3/3 | yes (carePreview) |
| diablillo | legacy drawables | legacy drawables | 2/2/2/2/1/1 | 2/2/2/2/1/1 | care_v1 3/3 | base sleep 1/1 | care_v1 3/3 | yes (carePreview) |
| moki | app/src/debug/assets/pets/moki/moki_sheet_v1.json | app/src/main/assets/pets/moki/moki_sheet_v1.json | 4/4/4/4/4/reverse | 4/4/4/4/4/reverse | care_v1 3/3 | base sleep 4/4 | care_v1 3/3 | yes (carePreview) |
| yuki | app/src/main/assets/pets/yuki/yuki_sheet_v1.json | app/src/main/assets/pets/yuki/yuki_sheet_v1.json | 3/2/3/4/1/reverse | 3/2/3/4/1/reverse | base sleep 1/1 | base sleep 1/1 | base sleep 1/1 | yes (carePreview) |
| piru | app/src/main/assets/pets/piru/piru_sheet_v1.json | app/src/main/assets/pets/piru/piru_sheet_v1.json | 3/2/3/4/2/reverse | 3/2/3/4/2/reverse | base sleep 2/2 | base sleep 2/2 | base sleep 2/2 | yes (carePreview) |
| menta | app/src/main/assets/pets/menta/menta_sheet_v1.json | app/src/main/assets/pets/menta/menta_sheet_v1.json | 3/4/3/2/1/reverse | 3/4/3/2/1/reverse | care_v1 3/3 | base sleep 1/1 | care_v1 3/3 | yes (carePreview) |
| tela | app/src/debug/assets/pets/tela/tela_motion_v2.json | app/src/main/assets/pets/tela/tela_motion_v2.json | 4/8/4/4/4/reverse | 4/8/4/4/4/reverse | TelaRestPose 2/2 | TelaRestPose 2/2 | TelaRestPose 2/2 | yes (carePreview) |
| lumi | app/src/debug/assets/pets/lumi/lumi_motion_v2.json | app/src/main/assets/pets/lumi/lumi_motion_v2.json | 4/8/4/4/4/reverse | 4/8/4/4/4/reverse | base sleep 4/4 | base sleep 4/4 | base sleep 4/4 | yes (carePreview) |

## Interpretation and limits

- Corgi is legacy in both debug and release because `HomeLocomotion.load` returns `loadLegacy` before looking for an atlas. The debug `companion/pets/corgi.json` is therefore not a home route.
- The current selector reads the merged `pets/<pet>` directory and prefers `motion_v2` by filename. This script approximates that merge with debug assets taking precedence over main assets; it does not claim that an unbuilt source set is packaged.
- `carePreview` contains care atlases for all 15 pets and is added to debug. Release receives that source set only with `-Ppixelpals.companion.releaseCandidate=true`. `HomeRestArtwork` currently consumes care rest sequences for Corgi, Patito, Bloop, Moki, Menta, Jelly, and Diablillo. Tela instead replaces sleep with `TelaRestPose.frames` (38, 39). Care art packaging and home-rest usage are separate facts.
- The selector is duplicated here because a Python audit cannot execute Android `AssetManager`, `BuildConfig`, or Gradle source-set merging. If Kotlin routing changes, this report must be updated; the checks are not runtime proof.
- Missing dedicated clip names use Kotlin's fallback behavior. The audit makes no claim about artistic quality, visual continuity, or a need for new frames from a missing name.
