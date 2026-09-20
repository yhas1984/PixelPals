package com.pixelpals.app.core.care.scene

/** Reviewed rest-to-upright poses; Corgi has its own longer choreography. */
object SpeciesRestRecovery {
    fun supports(bed: CareBed): Boolean = when (bed) {
        CareBed.PUDDLE, CareBed.WARM_LEAF, CareBed.MOON_MIST, CareBed.BASKET, CareBed.MOSS,
        CareBed.SNOW, CareBed.CLOUD_CRADLE, CareBed.CLOUD, CareBed.NEST, CareBed.BRANCH,
        CareBed.ICE, CareBed.WEB, CareBed.STARLIGHT, CareBed.WING_WRAP -> true
        CareBed.CUSHION -> false
    }

    fun getFrame(bed: CareBed, progress: Float, reduced: Boolean): Int? {
        if (!supports(bed)) return null
        val awake: Int = when (bed) {
            CareBed.WEB, CareBed.WING_WRAP -> 15
            CareBed.STARLIGHT -> 1
            else -> 12
        }
        if (progress >= 1f) return awake
        // Ginger's native entry pose is awake. Reduced motion holds the
        // authored sleeping curl instead of leaving her staring while dreaming.
        if (reduced && bed == CareBed.BASKET) return 19
        if (reduced || progress < .8f) return null
        if (bed == CareBed.WING_WRAP) return if (progress < .85f) 9 else if (progress < .95f) 11 else awake
        return when {
            progress < .85f -> 18
            progress < .9f -> 17
            progress < .95f -> if (bed == CareBed.STARLIGHT) 10 else 16
            else -> awake
        }
    }

    fun getSleepAmount(progress: Float): Float = progress.coerceIn(0f, 1f) * (1f - smooth(progress, .8f, 1f))
    fun getBedAlpha(bed: CareBed, progress: Float, reduced: Boolean): Float =
        if (!supports(bed) || reduced) 1f else 1f - smooth(progress, .95f, 1f)
    fun getDreamAlpha(bed: CareBed, progress: Float): Float =
        if (!supports(bed)) 1f else 1f - smooth(progress, .8f, .85f)

    private fun smooth(progress: Float, start: Float, end: Float): Float {
        val t: Float = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
