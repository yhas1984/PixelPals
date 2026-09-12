# Preserve existing rest across schedule boundaries

Starting scheduled rest previously reset an already sleeping pet to APPROACH_BED (or restarted REST without a bed). This could replace the sleeping drawing with locomotion and restart the sleep animation at the nightly clock/DND boundary. The shared motion controller now preserves REST and WAKE on entry into the schedule.

- A pet already sleeping at the selected bed retains its elapsed sleep time and position.
- A sleeping pet that needs another bed completes WAKE before walking there.
- Re-enabling the schedule during WAKE preserves that transition instead of cutting it short.
- The prior moved-bed handling remains: moving a bed during scheduled or ordinary low-energy rest wakes the pet before relocation. Reduced-motion home rest stays in place; this change does not introduce teleportation.

The shared controller is also used by desktop scheduled sleep, although desktop furniture coordinates are independent of home layout. This change is display-only and grants no care or currency.

Validation: 243 JVM tests passed, including 10 ScheduledMotionTest scenarios; debug assembly and lint passed. The full 179-test Android suite passed immediately before this change, not on this exact source revision. The new regression evidence is the controller tests, not a claim of physical all-pet sleep review.

Follow-up: ScheduledPetSleep now reapplies the observed home bed selection while active, instead of capturing it only when sleep starts. This updates ambient furniture without resetting the motion controller. Debug assembly passed; SharedPropArtworkTest passed all 3 renderer tests on the phone at 192.168.1.160:40327 in 3.645 seconds, and the APK was installed with data preserved. Those tests check shared artwork geometry, not a complete interactive bed-change scenario. Explicit care sessions continue capturing their own props for the session duration.
