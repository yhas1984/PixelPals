# Actual sleep artwork review

SleepArtworkReviewTest renders all 15 pets at the same actor target size, comparing
idle to five seconds of scheduled rest with the same prop/dream composition. The
three before sheets are in evidence/sleep-art. Human inspection found awake or
unsuitable resting poses for Bloop, Jelly, Angel, Patito, Diablillo, Moki and Menta.
This contradicts any inference that controller readiness tests proved sleep art.

Connected existing care rest poses for Patito and Jelly via HomeRestArtwork. Patito
settles through frames 16/17/18; Jelly holds flattened frame 17 instead of rising to
18. Both reuse a stable whole-atlas scale and existing ground alignment; no embedded
zzz/bubble frame is used. Source resolution is retained for desktop-sized drawing.
Wake reverses the selected posture progression. Other pet drawings are unchanged.

Availability follows care asset packaging: debug and opt-in care candidate builds
have these poses; ordinary release keeps the legacy fallback until artwork is
approved and packaged for it. This is not a completed production sleep-art rollout.
The remaining five affected pets require further identity/proportion review or new
frames; in particular Diablillo's ordinary scheduled renderer still lacks its real
closed-eye wing-wrap pose. Broader animation, scaling and visual acceptance remain
unfinished. Static sheets do not establish transition or live overlay acceptance.

Validation: final debug/instrumentation builds, JVM tests and debug lint passed.
Installed on 192.168.1.160:43041 preserving data. The final review export passed
(2.159 seconds); visually inspected after-0.png and after-1.png. Patito now has a
closed-eye tucked pose and Jelly a closed-eye flattened pose. Remaining affected
pets are deliberately visible as unresolved in the same comparison artifacts.

Follow-up: `SLEEP-ART-SECOND-PASS-2026-09-08.md` records the subsequent correction
of those five poses and the remaining identity/proportion issues. The before/after
files above remain evidence of this first pass, not the current final appearance.
