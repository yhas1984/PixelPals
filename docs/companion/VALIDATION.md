# Validation checkpoint — 2026-09-05

This is a development candidate, not completed release acceptance.

- Debug build, JVM tests and debug lint passed after the navigation-height and shop-preview fixes.
- Android 8 emulator: full instrumentation passed 112/112 on the development checkpoint. An earlier full run had one premium-unlock timeout; the isolated rerun and subsequent complete run passed. After the final two UI fixes, the affected navigation/purchase suite passed 8/8.
- Additive schema 8 and first-preview schema 9 migration tests passed, including wallet, purchase, treasure, status, home and ongoing journey preservation.
- Python pipeline/atlas tests: 14/14. Asset validator: 280 production frames across all 15 pets, without orphan or exact duplicate frames.
- All 15 home renders passed visibility checks; the generated contact sheet was visually inspected. This is not a review of every animated care sequence.
- NE2213 at the latest supplied ADB address: debug update installed; Spanish Bloop home rendered with existing status and treasure; navigation labels are now fully visible. Feeding was triggered and its species-specific scene was visually inspected. No destructive instrumentation/database reset was run on the user's phone in this checkpoint.
- Release candidate APK and AAB built using the existing PixelPals signing configuration. `jarsigner -verify` returned `jar verified`; the upload certificate is self-signed. Packaged manifest: `com.pixelpals.app`, version 2.4.0, code 21.
- Production publication and Play-track validation have not been performed.

Remaining acceptance: uninterrupted device soak and comparative memory/battery/frame measurements; all-species care/motion review; full permission/revocation and accessibility matrix; real Play purchase/restoration; final product/art approval. Signed artifacts alone do not satisfy these gates.
