package com.pixelpals.app.core.motion

/** Hide until persisted travel state arrives, then evaluate the current selection. */
class ExpeditionVisibility {
    private var loaded: Boolean = false
    private var travellingPetId: String? = null

    fun update(petId: String?) {
        travellingPetId = petId
        loaded = true
    }

    fun hides(petId: String): Boolean = !loaded || travellingPetId == petId
}
