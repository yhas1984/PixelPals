package com.pixelpals.app.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pet_status")
data class PetStatusEntity(
    @PrimaryKey val petId: String,
    val health: Int = 92,
    val energy: Int = 78,
    @ColumnInfo(name = "hunger") val satiety: Int = 72,
    val hygiene: Int = 84,
    val mood: String = "HAPPY",
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val lastInteractionAt: Long = System.currentTimeMillis(),
    val condition: String = "HEALTHY",
    val conditionStartedAt: Long = 0L,
    val criticalNeedsStartedAt: Long = 0L,
    val recoveryProgress: Int = 0,
    val lastCareAt: Long = 0L,
    val lastMedicineAt: Long = 0L,
)
