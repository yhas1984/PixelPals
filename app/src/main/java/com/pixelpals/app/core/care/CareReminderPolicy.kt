package com.pixelpals.app.core.care

import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetStatusSnapshot
import kotlin.math.ceil

enum class CareReminderType {
    SATIETY,
    ENERGY,
    HYGIENE,
    ATTENTION,
    AT_RISK,
    SICK,
}

data class CareReminderState(
    val lastType: CareReminderType? = null,
    val lastSentAt: Long = 0L,
    val sentDay: String = "",
    val sentCount: Int = 0,
    val snoozedUntil: Long = 0L,
)

data class CareReminderDecision(
    val type: CareReminderType,
    val action: CareAction,
    val isCritical: Boolean,
)

data class CareReminderContext(
    val snapshot: PetStatusSnapshot,
    val state: CareReminderState,
    val now: Long,
    val todayKey: String,
    val localHour: Int,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
    val disabledTypes: Set<CareReminderType> = emptySet(),
)

class CareReminderPolicy {
    fun decide(context: CareReminderContext): CareReminderDecision? {
        val sentCountToday: Int = if (context.state.sentDay == context.todayKey) context.state.sentCount else 0
        if (context.now < context.state.snoozedUntil) return null
        if (sentCountToday >= MAX_NOTIFICATIONS_PER_DAY) return null
        if (isQuietHour(context.localHour, context.quietStartHour, context.quietEndHour)) return null
        if (context.now - context.state.lastSentAt < NOTIFICATION_COOLDOWN_MILLIS) return null
        if (context.now - context.snapshot.lastInteractionAt < RECENT_INTERACTION_MILLIS) return null
        val decision: CareReminderDecision = getDominantDecision(
            context.snapshot,
            context.now,
            context.disabledTypes,
        ) ?: return null
        if (
            context.state.lastType == decision.type &&
            context.now - context.state.lastSentAt < SAME_TYPE_COOLDOWN_MILLIS
        ) return null
        return decision
    }

    fun getNextEvaluationDelayMillis(snapshot: PetStatusSnapshot, now: Long): Long {
        if (snapshot.condition != PetCondition.HEALTHY) return MINIMUM_EVALUATION_DELAY_MILLIS
        val candidateDelays: List<Long> = listOf(
            getNeedDelay(snapshot.hunger, SATIETY_DECAY_PER_HOUR),
            getNeedDelay(snapshot.energy, ENERGY_DECAY_PER_HOUR),
            getNeedDelay(snapshot.hygiene, HYGIENE_DECAY_PER_HOUR),
            (ATTENTION_DELAY_MILLIS - (now - snapshot.lastInteractionAt)).coerceAtLeast(0L),
        )
        return candidateDelays.minOrNull()
            ?.coerceIn(MINIMUM_EVALUATION_DELAY_MILLIS, MAXIMUM_EVALUATION_DELAY_MILLIS)
            ?: MAXIMUM_EVALUATION_DELAY_MILLIS
    }

    private fun getDominantDecision(
        snapshot: PetStatusSnapshot,
        now: Long,
        disabledTypes: Set<CareReminderType>,
    ): CareReminderDecision? {
        val candidates: List<CareReminderDecision?> = listOf(
            if (snapshot.condition == PetCondition.SICK) CareReminderDecision(
                type = CareReminderType.SICK,
                action = if (snapshot.medicineAvailableAt == 0L || now >= snapshot.medicineAvailableAt) {
                    CareAction.MEDICINE
                } else {
                    CareAction.REST
                },
                isCritical = true,
            ) else null,
            if (snapshot.condition == PetCondition.AT_RISK) CareReminderDecision(
                CareReminderType.AT_RISK,
                snapshot.dominantSuggestion,
                true,
            ) else null,
            if (snapshot.hunger <= NEED_NOTIFICATION_THRESHOLD) CareReminderDecision(
                CareReminderType.SATIETY,
                CareAction.FEED,
                snapshot.hunger <= CRITICAL_NOTIFICATION_THRESHOLD,
            ) else null,
            if (snapshot.hygiene <= NEED_NOTIFICATION_THRESHOLD) CareReminderDecision(
                CareReminderType.HYGIENE,
                CareAction.CLEAN,
                snapshot.hygiene <= CRITICAL_NOTIFICATION_THRESHOLD,
            ) else null,
            if (snapshot.energy <= NEED_NOTIFICATION_THRESHOLD) CareReminderDecision(
                CareReminderType.ENERGY,
                CareAction.REST,
                snapshot.energy <= CRITICAL_NOTIFICATION_THRESHOLD,
            ) else null,
            if (now - snapshot.lastInteractionAt >= ATTENTION_DELAY_MILLIS) CareReminderDecision(
                CareReminderType.ATTENTION,
                CareAction.PLAY,
                false,
            ) else null,
        )
        return candidates.filterNotNull().firstOrNull { it.type !in disabledTypes }
    }

    private fun getNeedDelay(value: Int, decayPerHour: Int): Long {
        if (value <= NEED_NOTIFICATION_THRESHOLD) return 0L
        val hours: Long = ceil(
            (value - NEED_NOTIFICATION_THRESHOLD).toDouble() / decayPerHour.toDouble(),
        ).toLong()
        return hours * HOUR_MILLIS
    }

    private fun isQuietHour(hour: Int, startHour: Int, endHour: Int): Boolean {
        if (startHour == endHour) return false
        return if (startHour < endHour) hour in startHour until endHour else hour >= startHour || hour < endHour
    }

    companion object {
        const val MAX_NOTIFICATIONS_PER_DAY: Int = 2
        const val SNOOZE_MILLIS: Long = 2L * 60L * 60L * 1_000L
        private const val NEED_NOTIFICATION_THRESHOLD: Int = 30
        private const val CRITICAL_NOTIFICATION_THRESHOLD: Int = 15
        private const val SATIETY_DECAY_PER_HOUR: Int = 6
        private const val ENERGY_DECAY_PER_HOUR: Int = 4
        private const val HYGIENE_DECAY_PER_HOUR: Int = 2
        private const val HOUR_MILLIS: Long = 60L * 60L * 1_000L
        private const val ATTENTION_DELAY_MILLIS: Long = 6L * HOUR_MILLIS
        private const val RECENT_INTERACTION_MILLIS: Long = 30L * 60L * 1_000L
        private const val NOTIFICATION_COOLDOWN_MILLIS: Long = 6L * HOUR_MILLIS
        private const val SAME_TYPE_COOLDOWN_MILLIS: Long = 12L * HOUR_MILLIS
        private const val MINIMUM_EVALUATION_DELAY_MILLIS: Long = 30L * 60L * 1_000L
        private const val MAXIMUM_EVALUATION_DELAY_MILLIS: Long = 6L * HOUR_MILLIS
    }
}
