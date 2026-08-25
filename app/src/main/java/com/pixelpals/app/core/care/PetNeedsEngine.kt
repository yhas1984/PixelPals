package com.pixelpals.app.core.care

import com.pixelpals.app.status.CareAction
import kotlin.math.ceil

class PetNeedsEngine(
    private val timeProvider: TimeProvider = SystemTimeProvider,
    private val rules: PetCareRules = PetCareRules(),
) {
    fun reconcile(state: PetCareState, isActive: Boolean): PetCareState {
        if (!isActive || state.condition == PetCondition.HIBERNATING) return state
        val now: Long = timeProvider.getCurrentTimeMillis()
        if (hasReachedHibernation(state, now)) return createHibernatingState(state, now)
        val elapsedMillis: Long = (now - state.lastUpdatedAt).coerceAtLeast(0L)
        val elapsedTicks: Long = elapsedMillis / rules.tickMillis
        if (elapsedTicks == 0L) return evolveCondition(state, state, 0L)
        val evaluatedAt: Long = state.lastUpdatedAt + elapsedTicks * rules.tickMillis
        val decayed: PetCareState = state.copy(
            satiety = decayNeed(state.satiety, elapsedTicks, rules.satietyDecayPerTick),
            energy = decayNeed(state.energy, elapsedTicks, rules.energyDecayPerTick),
            hygiene = decayNeed(state.hygiene, elapsedTicks, rules.hygieneDecayPerTick),
            lastUpdatedAt = evaluatedAt,
        )
        return evolveCondition(state, decayed, elapsedTicks)
    }

    fun applyCare(state: PetCareState, action: CareAction): PetCareState {
        val now: Long = timeProvider.getCurrentTimeMillis()
        val reconciled: PetCareState = reconcile(state, isActive = true)
        if (reconciled.condition == PetCondition.HIBERNATING) return wakeFromHibernation(reconciled, now)
        val canRewardRecovery: Boolean = now - reconciled.lastCareAt >= rules.recoveryCareCooldownMillis
        val cared: PetCareState = applyNeedChange(reconciled, action, now)
        val withRecovery: PetCareState = applyRecoveryCare(cared, reconciled, action, canRewardRecovery, now)
        return resolveRecovery(evolveCondition(reconciled, withRecovery, 0L), now)
    }

    fun getMedicineAvailableAt(state: PetCareState): Long {
        if (state.lastMedicineAt == 0L) return 0L
        return state.lastMedicineAt + rules.medicineCooldownMillis
    }

    private fun evolveCondition(
        previous: PetCareState,
        current: PetCareState,
        elapsedTicks: Long,
    ): PetCareState {
        if (previous.condition == PetCondition.SICK || previous.condition == PetCondition.RECOVERING) {
            return applyPassiveRecovery(current, elapsedTicks)
        }
        val criticalNeedCount: Int = getCriticalNeedCount(current)
        if (criticalNeedCount < REQUIRED_CRITICAL_NEEDS) return current.copy(
            condition = PetCondition.HEALTHY,
            conditionStartedAt = 0L,
            criticalNeedsStartedAt = 0L,
            recoveryProgress = 0,
        )
        val criticalStartedAt: Long = getCriticalStartedAt(previous, current)
        val criticalDuration: Long = (current.lastUpdatedAt - criticalStartedAt).coerceAtLeast(0L)
        return when {
            criticalDuration >= rules.sicknessDelayMillis -> current.copy(
                condition = PetCondition.SICK,
                conditionStartedAt = current.lastUpdatedAt,
                criticalNeedsStartedAt = criticalStartedAt,
                recoveryProgress = 0,
            )
            criticalDuration >= rules.riskDelayMillis -> current.copy(
                condition = PetCondition.AT_RISK,
                conditionStartedAt = if (previous.condition == PetCondition.AT_RISK) {
                    previous.conditionStartedAt
                } else {
                    current.lastUpdatedAt
                },
                criticalNeedsStartedAt = criticalStartedAt,
            )
            else -> current.copy(criticalNeedsStartedAt = criticalStartedAt)
        }
    }

    private fun getCriticalStartedAt(previous: PetCareState, current: PetCareState): Long {
        if (previous.criticalNeedsStartedAt > 0L) return previous.criticalNeedsStartedAt
        val crossingTicks: List<Long> = listOf(
            getTicksUntilCritical(previous.satiety, rules.satietyDecayPerTick),
            getTicksUntilCritical(previous.energy, rules.energyDecayPerTick),
            getTicksUntilCritical(previous.hygiene, rules.hygieneDecayPerTick),
        ).sorted()
        val secondCrossingTick: Long = crossingTicks[1]
        return (previous.lastUpdatedAt + secondCrossingTick * rules.tickMillis)
            .coerceAtMost(current.lastUpdatedAt)
    }

    private fun getTicksUntilCritical(value: Int, decayPerTick: Int): Long {
        if (value <= rules.criticalNeedThreshold) return 0L
        val pointsToLose: Int = value - rules.criticalNeedThreshold
        return ceil(pointsToLose.toDouble() / decayPerTick.toDouble()).toLong()
    }

    private fun applyPassiveRecovery(state: PetCareState, elapsedTicks: Long): PetCareState {
        if (elapsedTicks == 0L) return state
        val progress: Int = (state.recoveryProgress + elapsedTicks * rules.recoveryProgressPerTick)
            .coerceAtMost(MAX_NEED_VALUE.toLong())
            .toInt()
        return resolveRecovery(state.copy(recoveryProgress = progress), state.lastUpdatedAt)
    }

    private fun applyNeedChange(state: PetCareState, action: CareAction, now: Long): PetCareState {
        val changed: PetCareState = when (action) {
            CareAction.FEED -> state.copy(satiety = (state.satiety + FEED_GAIN).coerceAtMost(MAX_NEED_VALUE))
            CareAction.CLEAN -> state.copy(hygiene = (state.hygiene + CLEAN_GAIN).coerceAtMost(MAX_NEED_VALUE))
            CareAction.PLAY -> state.copy(
                energy = (state.energy - PLAY_ENERGY_COST).coerceAtLeast(0),
                satiety = (state.satiety - PLAY_SATIETY_COST).coerceAtLeast(0),
            )
            CareAction.REST -> state.copy(energy = (state.energy + REST_GAIN).coerceAtMost(MAX_NEED_VALUE))
            CareAction.CHECK_IN -> state
            CareAction.MEDICINE -> state
        }
        return changed.copy(lastInteractionAt = now, lastUpdatedAt = now)
    }

    private fun applyRecoveryCare(
        cared: PetCareState,
        previous: PetCareState,
        action: CareAction,
        canRewardRecovery: Boolean,
        now: Long,
    ): PetCareState {
        if (previous.condition != PetCondition.SICK && previous.condition != PetCondition.RECOVERING) return cared
        if (action == CareAction.MEDICINE) return applyMedicine(cared, previous, now)
        if (!canRewardRecovery || action == CareAction.CHECK_IN) return cared
        val gain: Int = if (action == CareAction.REST) REST_RECOVERY_GAIN else CARE_RECOVERY_GAIN
        return cared.copy(
            recoveryProgress = (cared.recoveryProgress + gain).coerceAtMost(MAX_NEED_VALUE),
            lastCareAt = now,
        )
    }

    private fun applyMedicine(state: PetCareState, previous: PetCareState, now: Long): PetCareState {
        if (now < getMedicineAvailableAt(previous)) return state
        return state.copy(
            recoveryProgress = (state.recoveryProgress + MEDICINE_RECOVERY_GAIN).coerceAtMost(MAX_NEED_VALUE),
            lastCareAt = now,
            lastMedicineAt = now,
        )
    }

    private fun resolveRecovery(state: PetCareState, now: Long): PetCareState {
        return when {
            state.recoveryProgress >= MAX_NEED_VALUE -> state.copy(
                condition = PetCondition.HEALTHY,
                conditionStartedAt = now,
                criticalNeedsStartedAt = 0L,
                recoveryProgress = 0,
            )
            state.recoveryProgress >= rules.recoveringThreshold -> state.copy(
                condition = PetCondition.RECOVERING,
                conditionStartedAt = if (state.condition == PetCondition.RECOVERING) {
                    state.conditionStartedAt
                } else {
                    now
                },
            )
            else -> state
        }
    }

    private fun wakeFromHibernation(state: PetCareState, now: Long): PetCareState = state.copy(
        energy = state.energy.coerceAtLeast(HIBERNATION_WAKE_NEED),
        satiety = state.satiety.coerceAtLeast(HIBERNATION_WAKE_NEED),
        hygiene = state.hygiene.coerceAtLeast(HIBERNATION_WAKE_NEED),
        condition = PetCondition.RECOVERING,
        conditionStartedAt = now,
        criticalNeedsStartedAt = 0L,
        recoveryProgress = HIBERNATION_WAKE_RECOVERY,
        lastUpdatedAt = now,
        lastInteractionAt = now,
        lastCareAt = now,
    )

    private fun createHibernatingState(state: PetCareState, now: Long): PetCareState = state.copy(
        energy = state.energy.coerceAtLeast(HIBERNATION_MINIMUM_NEED),
        satiety = state.satiety.coerceAtLeast(HIBERNATION_MINIMUM_NEED),
        hygiene = state.hygiene.coerceAtLeast(HIBERNATION_MINIMUM_NEED),
        condition = PetCondition.HIBERNATING,
        conditionStartedAt = now,
        criticalNeedsStartedAt = 0L,
        recoveryProgress = 0,
        lastUpdatedAt = now,
    )

    private fun hasReachedHibernation(state: PetCareState, now: Long): Boolean {
        return now - state.lastInteractionAt >= rules.hibernationDelayMillis
    }

    private fun getCriticalNeedCount(state: PetCareState): Int = listOf(
        state.satiety,
        state.energy,
        state.hygiene,
    ).count { it <= rules.criticalNeedThreshold }

    private fun decayNeed(value: Int, ticks: Long, decayPerTick: Int): Int {
        return (value - ticks * decayPerTick).coerceAtLeast(0L).toInt()
    }

    private companion object {
        const val MAX_NEED_VALUE: Int = 100
        const val REQUIRED_CRITICAL_NEEDS: Int = 2
        const val FEED_GAIN: Int = 30
        const val CLEAN_GAIN: Int = 35
        const val REST_GAIN: Int = 35
        const val PLAY_ENERGY_COST: Int = 8
        const val PLAY_SATIETY_COST: Int = 4
        const val CARE_RECOVERY_GAIN: Int = 10
        const val REST_RECOVERY_GAIN: Int = 15
        const val MEDICINE_RECOVERY_GAIN: Int = 30
        const val HIBERNATION_MINIMUM_NEED: Int = 25
        const val HIBERNATION_WAKE_NEED: Int = 40
        const val HIBERNATION_WAKE_RECOVERY: Int = 60
    }
}
