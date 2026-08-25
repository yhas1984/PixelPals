package com.pixelpals.app.status

import com.pixelpals.app.core.care.PetCondition

enum class PetMood {
    HAPPY,
    SLEEPY,
    HUNGRY,
    DIRTY,
    BORED,
    EXCITED
}

enum class CareAction {
    FEED,
    CLEAN,
    PLAY,
    REST,
    CHECK_IN,
    MEDICINE,
}

enum class PetPersonality {
    SWEET,
    DREAMY,
    BOUNCY,
    LOYAL,
    ELEGANT,
    ANGELIC,
    CURIOUS,
    CHAOTIC
}

data class PetStatusSnapshot(
    val petId: String,
    val health: Int,
    val energy: Int,
    val hunger: Int,
    val hygiene: Int,
    val bond: Int,
    val mood: PetMood,
    val careStreakDays: Int,
    val softCurrency: Int,
    val dominantSuggestion: CareAction,
    val memoriesUnlocked: Int,
    val condition: PetCondition = PetCondition.HEALTHY,
    val recoveryProgress: Int = 0,
    val medicineAvailableAt: Long = 0L,
    val lastInteractionAt: Long = 0L,
)

data class DailyTask(
    val id: String,
    val title: String,
    val description: String,
    val rewardCoins: Int,
    val completed: Boolean
)

data class MemoryMoment(
    val id: String,
    val title: String,
    val subtitle: String
)
