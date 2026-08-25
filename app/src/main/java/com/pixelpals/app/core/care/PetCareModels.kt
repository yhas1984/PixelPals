package com.pixelpals.app.core.care

enum class PetCondition {
    HEALTHY,
    AT_RISK,
    SICK,
    RECOVERING,
    HIBERNATING,
}

enum class PetNeed {
    SATIETY,
    ENERGY,
    HYGIENE,
    ATTENTION,
}

data class PetCareState(
    val energy: Int,
    val satiety: Int,
    val hygiene: Int,
    val condition: PetCondition,
    val conditionStartedAt: Long,
    val criticalNeedsStartedAt: Long,
    val recoveryProgress: Int,
    val lastUpdatedAt: Long,
    val lastInteractionAt: Long,
    val lastCareAt: Long,
    val lastMedicineAt: Long,
) {
    init {
        require(energy in NEED_RANGE) { "Energy must be between zero and one hundred" }
        require(satiety in NEED_RANGE) { "Satiety must be between zero and one hundred" }
        require(hygiene in NEED_RANGE) { "Hygiene must be between zero and one hundred" }
        require(recoveryProgress in NEED_RANGE) { "Recovery progress must be between zero and one hundred" }
    }

    companion object {
        private val NEED_RANGE: IntRange = 0..100
    }
}

data class PetCareRules(
    val tickMillis: Long = 30L * 60L * 1_000L,
    val satietyDecayPerTick: Int = 3,
    val energyDecayPerTick: Int = 2,
    val hygieneDecayPerTick: Int = 1,
    val criticalNeedThreshold: Int = 15,
    val riskDelayMillis: Long = 12L * 60L * 60L * 1_000L,
    val sicknessDelayMillis: Long = 24L * 60L * 60L * 1_000L,
    val hibernationDelayMillis: Long = 7L * 24L * 60L * 60L * 1_000L,
    val recoveryProgressPerTick: Int = 1,
    val recoveringThreshold: Int = 60,
    val recoveryCareCooldownMillis: Long = 6L * 60L * 60L * 1_000L,
    val medicineCooldownMillis: Long = 24L * 60L * 60L * 1_000L,
)

fun interface TimeProvider {
    fun getCurrentTimeMillis(): Long
}

object SystemTimeProvider : TimeProvider {
    override fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
}
