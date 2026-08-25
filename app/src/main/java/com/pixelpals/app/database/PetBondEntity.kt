package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pet_bond")
data class PetBondEntity(
    @PrimaryKey val petId: String,
    val bondPoints: Int = 0,
    val careStreakDays: Int = 0,
    val softCurrency: Int = 0,
    val memoriesUnlocked: Int = 0,
    val firstSeenAt: Long = 0L,
    val lastCheckInDay: String = "",
    val lastDailyCompletionDay: String = "",
    val lastTreasureInteractionMilestone: Int = 0,
    val lastTreasureActiveMilestone: Int = 0,
    val activeMinutes: Int = 0,
    val illnessRecoveries: Int = 0,
    val lastTreasureGiftDay: String = "",
    val treasuresGifted: Int = 0,
    val favoriteTreasuresGifted: Int = 0,
)
