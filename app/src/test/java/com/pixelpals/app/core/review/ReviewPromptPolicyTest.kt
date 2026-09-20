package com.pixelpals.app.core.review

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptPolicyTest {
    private val now: Long = 1_800_000_000_000L
    private val policy = ReviewPromptPolicy()

    @Test
    fun newUserIsNotEligible() {
        assertFalse(policy.isEligible(input(adoptionDays = 2), ReviewPromptState(), now, 24))
    }

    @Test
    fun establishedUserAfterPositiveMomentIsEligible() {
        assertTrue(policy.isEligible(input(), ReviewPromptState(), now, 24))
    }

    @Test
    fun sameVersionIsNeverRequestedTwice() {
        val state = ReviewPromptState(lastRequestedAt = now - TimeUnit.DAYS.toMillis(121), lastVersionCode = 24, requestCount = 1)
        assertFalse(policy.isEligible(input(), state, now, 24))
    }

    @Test
    fun anotherVersionStillWaitsOneHundredTwentyDays() {
        val tooSoon = ReviewPromptState(lastRequestedAt = now - TimeUnit.DAYS.toMillis(119), lastVersionCode = 23, requestCount = 1)
        val ready = tooSoon.copy(lastRequestedAt = now - TimeUnit.DAYS.toMillis(120))
        assertFalse(policy.isEligible(input(), tooSoon, now, 24))
        assertTrue(policy.isEligible(input(), ready, now, 24))
    }

    @Test
    fun careMomentRequiresThreeDayStreak() {
        assertFalse(policy.isEligible(input(moment = ReviewMoment.CARE_STREAK, careStreakDays = 2), ReviewPromptState(), now, 24))
        assertTrue(policy.isEligible(input(moment = ReviewMoment.CARE_STREAK, careStreakDays = 3), ReviewPromptState(), now, 24))
    }

    @Test
    fun stalePositiveMomentIsIgnored() {
        assertFalse(policy.isEligible(input(eventAt = now - TimeUnit.HOURS.toMillis(25)), ReviewPromptState(), now, 24))
    }

    private fun input(
        moment: ReviewMoment = ReviewMoment.EXPEDITION_REWARD,
        adoptionDays: Long = 8,
        careStreakDays: Int = 3,
        eventAt: Long = now,
    ) = ReviewPromptInput(
        moment = moment,
        adoptedAt = now - TimeUnit.DAYS.toMillis(adoptionDays),
        bond = 15,
        careStreakDays = careStreakDays,
        eventAt = eventAt,
    )
}
