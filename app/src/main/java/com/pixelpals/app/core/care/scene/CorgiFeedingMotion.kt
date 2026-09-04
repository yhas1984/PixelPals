package com.pixelpals.app.core.care.scene

/** Desktop-only prototype: approach the bowl, take bites, then lick the muzzle. */
object CorgiFeedingMotion {
    val timing: CareSceneTiming = CareSceneTiming(durationMs = 5_600L, completionMs = 4_600L)

    fun frameAt(elapsedMs: Long): Int = when {
        elapsedMs < 400L -> 2
        elapsedMs < 4_000L -> if ((elapsedMs - 400L) / 300L % 2L == 0L) 0 else 1
        elapsedMs < 4_600L -> 2
        else -> 3
    }

    fun foodAt(elapsedMs: Long): Float =
        (1f - (elapsedMs - 400L).coerceAtLeast(0L) / 3_600f).coerceIn(0f, 1f)
}
