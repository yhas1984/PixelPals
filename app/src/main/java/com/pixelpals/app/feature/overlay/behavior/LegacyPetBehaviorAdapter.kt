package com.pixelpals.app.feature.overlay.behavior

/**
 * Explicit rollback boundary for pets that have not passed the runtime gates.
 * It deliberately delegates without changing timing, rendering or physics.
 */
class LegacyPetBehaviorAdapter(
    private val delegate: PetBehavior,
) : PetBehavior by delegate
