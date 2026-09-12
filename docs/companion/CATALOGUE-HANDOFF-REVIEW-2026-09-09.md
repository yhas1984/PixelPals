# Desktop-to-care catalogue review

The previous care-only sheets could miss a size jump at entry. DesktopCareHandoffReviewTest now renders the actual behavior factory beside FEED, PET, REST and MEDICINE entry poses for each of the 15 pets. It uses a 160 px actor in a 320 px window, the behavior's care baseline and the production sprite calibration. Labels sit outside the drawing area. No PetView, repositories or progress writes are used.

The 15 existing desktop calibration values were moved unchanged into PetArtworkScale and compared with HEAD programmatically. TestPetBridge keeps its old default scale unless a review explicitly requests the production scale.

## Corrections from this pass

- Jelly's care bank was much smaller than its ordinary desktop body. Its desktop care source-cell factor is now 1.40 instead of the generic .94, fixed across the sequence. The room panel continues fitting its own available area. An actual body-mask comparison at feeding entry requires both width and height within 10% of the normal desktop rendering; props are excluded from that measurement.
- The larger Jelly exposed dream-cloud clipping at the end of REST. PetDreamPainter now reserves pixel padding and the full animated cloud extent at the top and bottom. Jelly's body size remains intact.
- Failed vertical-border checks now export `care-clipping.png` for diagnosis instead of leaving only a text assertion.

## First visual pass, not anatomical acceptance

All 15 initial comparison sheets were inspected. These observations identify further drawing work; they are not automatic scale prescriptions. A profile view versus a front view or a curled pose can legitimately change bounds.

| Pet | Observation / remaining review |
| --- | --- |
| Corgi | Recent care-camera fixes improve size consistency; gait versus seated/standing perspective still needs intermediate poses. |
| Taro | Default front/social pose and quadruped care differ in head/body proportions; review against a quadruped locomotion reference too. |
| Ginger | Care poses have a broader head and shorter-looking body than the ordinary seated drawing. |
| Moki | Head shape and proportions change between the ordinary perch and care poses. |
| Yuki | Care head/body balance differs from the ordinary upright snowman; inspect through transitions. |
| Angel | Head proportion changes noticeably between hovering and care poses. |
| Bloop | Outline softness and contour treatment differ between ordinary and care banks. |
| Jelly | Large care-entry shrink corrected; highlights/outlines and later deforming poses still need artistic continuity review. |
| Diablillo | The sampled ordinary pose is in profile and care is frontal; turn/entry intermediates remain necessary. |
| Nube Michi | Compare contour density and body proportions between banks; avoid treating cloud drift as a camera-size error. |
| Lumi | Entry silhouettes are broadly comparable; tail/head transitions still need continuous playback review. |
| Menta | Strong palette and face/proportion differences remain. Scaling alone cannot restore identity. |
| Patito | Ordinary swimming artwork and detailed care drawings differ visibly in rendering style/resolution. |
| Piru | Care head/body balance needs checking against the ordinary pose across the full sequence. |
| Tela | Care head mass differs from the ordinary spider; leg contact and frame-to-frame anatomy remain open. |

Evidence: `evidence/catalogue-handoffs/desktop-care-handoffs/` contains the final 15 sheets; `jelly-before.png` preserves the initial narrower-window diagnostic. Do not compare absolute pixel positions across those different diagnostic layouts. The regression compares bodies in the same renderer fixture.

Validation: debug APKs, 245 JVM tests and lint passed. Seventeen targeted Android tests passed in 7.491 seconds on disposable emulator 5580, including all-species clipping/contact checks and dreams. After adjusting only the review sheet/window layout, its one catalogue-wide test passed again in 1.778 seconds and lint passed. This is not full continuous visual acceptance, a performance soak or release approval. The newer pending updates have not been installed on the disconnected physical phone.
