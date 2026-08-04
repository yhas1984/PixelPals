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
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastCheckInDay: String = "",
    val lastDailyCompletionDay: String = ""
)
