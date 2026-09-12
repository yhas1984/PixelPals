# Planted Corgi alert and edge pauses

Original frame 1 holds raised paws. The controller used it for stationary alerts, after-care pauses and boundary pauses. It also rendered a walking contact on the first frame of a boundary stop before switching to the alert frame.

Use original planted frame 0 for those upright pauses, including the first edge-stop frame. Remove the alert's whole-body rotation around the sprite centre, which moved the paws despite a stationary window. Sitting and the original bitmap files are unchanged.

The controller regression now requires the exact planted frame and zero rotation throughout upright after-care pauses. It also checks 16 consecutive edge-stop frames at each boundary, no positional drift, inward facing, and subsequent travel away from the edge. Seated care recovery remains covered separately in the same loop. This does not create a drawn turning sequence or new anatomical in-betweens.

Validation: debug/instrumentation builds, JVM suite and lint passed (31 seconds). CorgiScaleRenderingTest, CorgiArtworkReviewTest and HomeCorgiContinuityTest passed all six tests on the physical phone in 1.802 seconds. These use TestPetBridge/offscreen rendering, not full PetView construction. Updated debug APK installed preserving data. No full Android-suite rerun or continuous visual turn review is claimed for this revision.
