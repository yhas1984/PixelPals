# Shared spoon contact geometry

Room Corgi, desktop Corgi and the shared species renderer used different empirical offsets for medicine. Room Corgi also skipped its offset with reduced motion and always drew a full spoon. These differences could place the spoon contents beside the mouth or change contact when the motion preference changed.

CareSpoonGeometry now derives the origin from the actual bowl center and painted tilt. CarePropPainter uses the same bowl dimensions and rotation; all three renderers align that geometry to their animated mouth anchors. Corgi desktop retains its existing approach/retreat offsets. Room Corgi's medicine amount now decreases with progress, including reduced motion; manual placement before contact remains unchanged.

Validation: debug app/test APKs, 245 JVM tests and lint passed. Fourteen targeted Android tests passed on disposable emulator 5580 in 5.942 seconds, covering spoon contact, shared species rendering and Corgi desktop care. CareSpoonContactTest measures the actual medicine-color centroid against the mouth for all 15 species, two viewport sizes and both motion modes, then checks consumption. It is an offscreen renderer test without progression writes. Output: `evidence/spoon-contact/android-tests.txt`.

Phone installation was attempted but the device at 192.168.1.160:40327 was offline and reconnection returned No route to host. This change is built and emulator-verified, not installed or visually accepted on that phone. Full animation/art and release acceptance remain open.
