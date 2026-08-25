package com.pixelpals.app.core.care

import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareReminderPolicyTest {
    private val policy = CareReminderPolicy()

    @Test
    fun decide_prioritizesSicknessAndMedicine() {
        val decision = policy.decide(createContext(createSnapshot(condition = PetCondition.SICK)))

        assertEquals(CareReminderType.SICK, decision?.type)
        assertEquals(CareAction.MEDICINE, decision?.action)
    }

    @Test
    fun decide_usesDominantNeedOnly() {
        val snapshot = createSnapshot(hunger = 12, hygiene = 10, energy = 8)

        val decision = policy.decide(createContext(snapshot))

        assertEquals(CareReminderType.SATIETY, decision?.type)
        assertEquals(CareAction.FEED, decision?.action)
    }

    @Test
    fun decide_blocksQuietHours() {
        val context = createContext(createSnapshot(hunger = 10)).copy(localHour = 23)

        val decision = policy.decide(context)

        assertNull(decision)
    }

    @Test
    fun decide_blocksAfterTwoDailyNotifications() {
        val state = CareReminderState(sentDay = TODAY, sentCount = 2)

        val decision = policy.decide(createContext(createSnapshot(hunger = 10), state))

        assertNull(decision)
    }

    @Test
    fun decide_blocksRecentlyCaredPet() {
        val snapshot = createSnapshot(hunger = 10, lastInteractionAt = NOW - 10L * 60L * 1_000L)

        val decision = policy.decide(createContext(snapshot))

        assertNull(decision)
    }

    @Test
    fun decide_blocksSnoozedReminder() {
        val state = CareReminderState(snoozedUntil = NOW + CareReminderPolicy.SNOOZE_MILLIS)

        val decision = policy.decide(createContext(createSnapshot(hunger = 10), state))

        assertNull(decision)
    }

    private fun createContext(
        snapshot: PetStatusSnapshot,
        state: CareReminderState = CareReminderState(),
    ): CareReminderContext = CareReminderContext(
        snapshot = snapshot,
        state = state,
        now = NOW,
        todayKey = TODAY,
        localHour = 14,
    )

    private fun createSnapshot(
        hunger: Int = 80,
        energy: Int = 80,
        hygiene: Int = 80,
        condition: PetCondition = PetCondition.HEALTHY,
        lastInteractionAt: Long = NOW - 8L * 60L * 60L * 1_000L,
    ): PetStatusSnapshot = PetStatusSnapshot(
        petId = "taro",
        health = 80,
        energy = energy,
        hunger = hunger,
        hygiene = hygiene,
        bond = 20,
        mood = PetMood.HAPPY,
        careStreakDays = 1,
        softCurrency = 0,
        dominantSuggestion = CareAction.FEED,
        memoriesUnlocked = 1,
        condition = condition,
        lastInteractionAt = lastInteractionAt,
    )

    private companion object {
        const val NOW: Long = 1_800_000_000_000L
        const val TODAY: String = "2027-01-15"
    }
}
