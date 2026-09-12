package com.pixelpals.app.feature.home

/** Equal support planes draw furniture first, so a resting pet stays on top. */
internal object HomeDepth {
    fun isBehindPet(objectGround: Float, petGround: Float): Boolean = objectGround <= petGround + .5f
}
