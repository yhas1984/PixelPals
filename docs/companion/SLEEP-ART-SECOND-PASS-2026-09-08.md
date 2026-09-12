# Sleep artwork second visual pass

Inspected the original Angel, Moki and Menta atlases. Angel already contains a
closed-eye prayer frame (11), but the selected sequence returned to open-eye frame
10. Scheduled/home sleep now holds the first two prayer frames, preserving the
original artwork; wake reverses that sequence.

In builds with care preview assets, Bloop, Moki and Menta now use rest frames
16/17/18. Diablillo uses 3/17/9 (upright closed eyes, avoiding the unsuitable care
poses) with the existing ImpWingPainter wrapping the torso. Wing attachment uses
the actual sprite destination bounds, which are now exposed by HomeSpriteFrames;
the underlying positioning and scale calculations are unchanged.

Rendered all 15 pets on the phone and inspected corrected-0/1/2.png under
evidence/sleep-art. All now have recognizable static sleep poses in this debug
build. Angel retains the original identity; Diablillo's face remains visible above
the folded wings. This is not full animation/art acceptance: Menta's care green is
more saturated than its normal atlas, and Diablillo's head proportion differs.
Those source-art consistency issues need refinement before production promotion.
Care preview routing stays conditional on the opt-in asset packaging. Normal
release still lacks these additional poses; only Angel's native selection fix is
available independently of that packaging.

Validation: debug and instrumentation builds, JVM tests and debug lint passed.
Installed on 192.168.1.160:43041 preserving data. SleepArtworkReviewTest rendered
the final sheets successfully (6.509 seconds). This export is visual evidence,
not an automated assertion of beauty, motion continuity, memory or battery use.
