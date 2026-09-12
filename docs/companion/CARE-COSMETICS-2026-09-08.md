# Desktop care cosmetics — 2026-09-08

Care playback now receives the equipped color filter from PetView. Both the
dedicated Corgi renderer and shared species renderer apply it to the pet bitmap
and immediately clear it, preserving the original prop and reaction colors.
Passing null restores the original pet colors without recreating a renderer.

PetView also draws equipped auras and floating accessories during care, including
the loading fallback and Corgi fetch locomotion frames. The existing window and
cosmetic placement rules are retained. No store products or purchase data change.

Validation:

- Debug APK, instrumentation APK and debug lint passed.
- Four instrumentation tests passed on emulator-5580 (2.342 seconds): tint
  rendering/removal across all 15 pets, production ground anchors, semantic
  facing, and care loading/completion continuity.
- The tint test checks visible color changes, unchanged prop pixels and exact
  restoration on a subsequent untinted frame using the same renderer.
- The original midpoint food assertion was invalid for already-consumed food;
  the final test samples the start of feeding when each prop is visible.

Physical-device review remains pending because the supplied phone address is
unreachable. Accessory position through every care pose still needs visual
review; the rendering tests do not establish that all poses are artistically
finished or satisfy the complete project acceptance plan.
