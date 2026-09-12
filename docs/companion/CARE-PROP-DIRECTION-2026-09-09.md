# Stable interaction side through head turns

Moki's play anchors cross the body center (frame 4 mouth x=.30078, frame 5 x=.57031, body x approximately .5). The renderer recomputed the leaf's side from each current mouth position, turning a head movement into a sudden relocation across the pet. Direction-sensitive food/toy offsets now use the entry pose's side for the action. Animated mouth position, toy movement and species choreography remain active.

The initial feeding inspection did not establish a Moki feeding-side defect; the reproduced problem was in play. The shared fix also makes the other direction-sensitive trajectories retain their entry side.

Validation: debug APKs, 245 JVM tests and lint passed. Fifteen targeted Android tests passed on disposable emulator 5580 in 5.953 seconds. The added pixel test samples Moki's whole play sequence at 41 points, normal/reduced, checking a visible leaf on the original side of the mouth. Existing all-species rendering, medicine contact and Corgi care checks passed. Evidence: `evidence/prop-direction/android-tests.txt`.

Reconnection to phone 192.168.1.160:40327 returned No route to host. This and the preceding medicine-contact update remain uninstalled there. The change does not add anatomical in-betweens or complete visual acceptance.
