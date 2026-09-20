package com.pixelpals.app.core.review

import java.util.concurrent.TimeUnit

enum class ReviewMoment {
    EXPEDITION_REWARD,
    TREASURE_DISCOVERY,
    CARE_STREAK,
}

data class ReviewPromptInput(
    val moment: ReviewMoment,
    val adoptedAt: Long,
    val bond: Int,
    val careStreakDays: Int,
    val eventAt: Long,
)

data class ReviewPromptState(
    val lastRequestedAt: Long = 0L,
    val lastVersionCode: Long = 0L,
    val requestCount: Int = 0,
)

class ReviewPromptPolicy(
    private val minimumAdoptionAgeMs: Long = TimeUnit.DAYS.toMillis(7),
    private val minimumBond: Int = 15,
    private val minimumRepeatDelayMs: Long = TimeUnit.DAYS.toMillis(120),
    private val positiveMomentLifetimeMs: Long = TimeUnit.HOURS.toMillis(24),
) {
    fun isEligible(
        input: ReviewPromptInput,
        state: ReviewPromptState,
        now: Long,
        versionCode: Long,
    ): Boolean {
        if (input.adoptedAt <= 0L || now < input.adoptedAt) return false
        if (now - input.adoptedAt < minimumAdoptionAgeMs) return false
        if (input.bond < minimumBond) return false
        if (input.eventAt <= 0L || now < input.eventAt) return false
        if (now - input.eventAt > positiveMomentLifetimeMs) return false
        if (input.moment == ReviewMoment.CARE_STREAK && input.careStreakDays < 3) return false
        if (state.lastVersionCode == versionCode) return false
        if (state.lastRequestedAt > 0L && now - state.lastRequestedAt < minimumRepeatDelayMs) return false
        return true
    }
}
