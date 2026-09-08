# Taro's care pinwheel stays on the ground

The FOLLOW prop position was relative to a transformed actor anchor. Taro's small approach motion and changing atlas anchors therefore moved the pinwheel support too. Its old vertical offset also left the support above the ground.

The care renderer now captures the scene center and floor during layout and positions Taro's pinwheel independently of body transforms. Its base is aligned using the shared drawing's support endpoint. This applies to room care and desktop care; the already-placed home decoration keeps its existing position.

Validation: debug/test builds, 233 JVM tests and lint passed (21 seconds). Eight targeted Android rendering tests passed on the connected phone (16.838 seconds). The new pixel test samples six animation times in both room and desktop layouts, locates the actual wooden support pixels, checks their unchanged horizontal/vertical position and checks floor contact within two pixels. APK installed successfully. No full Android-suite rerun in this change.

This verifies prop placement. Taro's anatomical approach/contact animation still needs continuous visual acceptance; no new pose drawings were added.
