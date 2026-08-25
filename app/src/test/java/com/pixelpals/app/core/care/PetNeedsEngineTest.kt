package com.pixelpals.app.core.care

import com.pixelpals.app.status.CareAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetNeedsEngineTest {
    private val clock: FakeTimeProvider = FakeTimeProvider(START_TIME)
    private val engine: PetNeedsEngine = PetNeedsEngine(clock)

    @Test
    fun reconcile_decaysNeedsInThirtyMinuteTicks() {
        clock.advanceHours(1)

        val result: PetCareState = engine.reconcile(createState(), isActive = true)

        assertEquals(94, result.satiety)
        assertEquals(96, result.energy)
        assertEquals(98, result.hygiene)
    }

    @Test
    fun reconcile_preservesPartialTicksBetweenReads() {
        clock.advanceMinutes(20)
        val first: PetCareState = engine.reconcile(createState(), isActive = true)
        clock.advanceMinutes(10)

        val second: PetCareState = engine.reconcile(first, isActive = true)

        assertEquals(100, first.satiety)
        assertEquals(97, second.satiety)
    }

    @Test
    fun reconcile_doesNotDecayInactivePet() {
        clock.advanceHours(48)

        val result: PetCareState = engine.reconcile(createState(), isActive = false)

        assertEquals(createState(), result)
    }

    @Test
    fun reconcile_marksPetAtRiskAfterSustainedCriticalNeeds() {
        val critical: PetCareState = createState(energy = 15, satiety = 15, hygiene = 80)
        clock.advanceHours(13)

        val result: PetCareState = engine.reconcile(critical, isActive = true)

        assertEquals(PetCondition.AT_RISK, result.condition)
    }

    @Test
    fun reconcile_makesPetSickAfterTwentyFourCriticalHours() {
        val critical: PetCareState = createState(energy = 15, satiety = 15, hygiene = 80)
        clock.advanceHours(25)

        val result: PetCareState = engine.reconcile(critical, isActive = true)

        assertEquals(PetCondition.SICK, result.condition)
        assertEquals(0, result.recoveryProgress)
    }

    @Test
    fun applyCare_clearsRiskWhenCriticalNeedsRecover() {
        val atRisk: PetCareState = createState(
            energy = 15,
            satiety = 15,
            hygiene = 80,
            condition = PetCondition.AT_RISK,
            criticalNeedsStartedAt = START_TIME - 13L * HOUR_MILLIS,
        )

        val fed: PetCareState = engine.applyCare(atRisk, CareAction.FEED)

        assertEquals(PetCondition.HEALTHY, fed.condition)
        assertEquals(45, fed.satiety)
    }

    @Test
    fun applyCare_medicineAcceleratesRecoveryOncePerDay() {
        val sick: PetCareState = createState(condition = PetCondition.SICK)
        val first: PetCareState = engine.applyCare(sick, CareAction.MEDICINE)

        val repeated: PetCareState = engine.applyCare(first, CareAction.MEDICINE)

        assertEquals(30, first.recoveryProgress)
        assertEquals(first.recoveryProgress, repeated.recoveryProgress)
        assertTrue(engine.getMedicineAvailableAt(first) > clock.getCurrentTimeMillis())
    }

    @Test
    fun reconcile_recoversSickPetWithoutPermanentFailure() {
        val sick: PetCareState = createState(condition = PetCondition.SICK)
        clock.advanceHours(50)

        val result: PetCareState = engine.reconcile(sick, isActive = true)

        assertEquals(PetCondition.HEALTHY, result.condition)
        assertEquals(0, result.recoveryProgress)
    }

    @Test
    fun reconcile_hibernatesAfterSevenDaysWithoutInteraction() {
        clock.advanceHours(7 * 24 + 1)

        val result: PetCareState = engine.reconcile(createState(), isActive = true)

        assertEquals(PetCondition.HIBERNATING, result.condition)
        assertTrue(result.energy >= 25)
        assertTrue(result.satiety >= 25)
        assertTrue(result.hygiene >= 25)
    }

    @Test
    fun applyCare_wakesHibernatingPetIntoRecovery() {
        val hibernating: PetCareState = createState(
            energy = 25,
            satiety = 25,
            hygiene = 25,
            condition = PetCondition.HIBERNATING,
        )

        val result: PetCareState = engine.applyCare(hibernating, CareAction.CHECK_IN)

        assertEquals(PetCondition.RECOVERING, result.condition)
        assertEquals(60, result.recoveryProgress)
    }

    private fun createState(
        energy: Int = 100,
        satiety: Int = 100,
        hygiene: Int = 100,
        condition: PetCondition = PetCondition.HEALTHY,
        criticalNeedsStartedAt: Long = 0L,
    ): PetCareState = PetCareState(
        energy = energy,
        satiety = satiety,
        hygiene = hygiene,
        condition = condition,
        conditionStartedAt = if (condition == PetCondition.HEALTHY) 0L else START_TIME,
        criticalNeedsStartedAt = criticalNeedsStartedAt,
        recoveryProgress = 0,
        lastUpdatedAt = START_TIME,
        lastInteractionAt = START_TIME,
        lastCareAt = 0L,
        lastMedicineAt = 0L,
    )

    private companion object {
        const val START_TIME: Long = 1_800_000_000_000L
        const val HOUR_MILLIS: Long = 60L * 60L * 1_000L
    }
}

private class FakeTimeProvider(private var currentTimeMillis: Long) : TimeProvider {
    override fun getCurrentTimeMillis(): Long = currentTimeMillis

    fun advanceMinutes(minutes: Int) {
        currentTimeMillis += minutes * 60L * 1_000L
    }

    fun advanceHours(hours: Int) {
        currentTimeMillis += hours * 60L * 60L * 1_000L
    }
}
