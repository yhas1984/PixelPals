package com.pixelpals.app.core.ads

data class AppOpenLaunchDecision(
    val launchCount: Int,
    val isEligible: Boolean,
)

class AppOpenLaunchGate(private val minimumLaunches: Int = DEFAULT_MINIMUM_LAUNCHES) {
    fun register(previousLaunchCount: Int): AppOpenLaunchDecision {
        val launchCount: Int = (previousLaunchCount + 1).coerceAtLeast(0)
        return AppOpenLaunchDecision(
            launchCount = launchCount,
            isEligible = launchCount >= minimumLaunches,
        )
    }

    companion object {
        private const val DEFAULT_MINIMUM_LAUNCHES: Int = 3
    }
}
