# Corgi fetch exit

Fetch previously returned immediately from the upright care pose to a walking stride. `resumeAfterFetch` now explicitly selects the original standing frame and holds the alert state for 650 ms before normal acceleration resumes. It retains the fetch direction, including when the chase chose the side opposite to the pet's initial heading.

Validation: debug and test APKs, 230 JVM tests, and lint passed (45 seconds). Eight targeted Android tests passed on the connected phone (4.678 seconds), covering original-frame scale/floor, care/ball contact, and the new real-controller exit check. The exit check exercises both directions, verifies a stationary half-second pause at ground level, and verifies subsequent travel in the same direction. It does not mutate persistent care state. Debug APK installed successfully.

Remaining: the prop is still removed when care ends; a designed release/retirement sequence and additional anatomical transition frames remain open. Controller and pixel checks do not establish complete continuous-animation visual acceptance.

Follow-up: [ball release](CORGI-BALL-RELEASE-2026-09-08.md) adds the drop, bounce and fade before window removal. Additional anatomical frames remain open.
