# Ginger desktop motion checks — 2026-09-09

Added two behavior-level instrumented tests using the real Ginger atlas and TestPetBridge. Both passed on the disposable emulator (0.16 seconds). Test APK compilation passed; logs are in `evidence/ginger-desktop-motion/`.

- A deterministic pounce proceeds through airborne motion, landing and sitting, stays in the bridge bounds, preserves facing and returns to the ground with bounded transforms.
- A grounded reset clears seeded care offsets/rotation and restores neutral scale while retaining facing. The subsequent idle update allows normal breathing.

The initial reset assertion incorrectly required zero offset after an idle update. It was corrected to check reset before breathing resumes; production behavior was not changed.

These tests do not validate every frame's apparent body size, climbing artwork or physical-device animation quality. No new production assets were added.

The previously validated debug APK was installed with `adb install -r` on NE2213 at the user's latest endpoint. Activity launch returned OK and the process remained active. A subsequent visual capture showed Tela and its care menu on the desktop. User data was not cleared and no instrumented tests ran on the phone.
