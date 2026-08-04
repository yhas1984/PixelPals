package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pet_status")
data class PetStatusEntity(
    @PrimaryKey val petId: String,
    val health: Int = 92,
    val energy: Int = 78,
    val hunger: Int = 72,
    val hygiene: Int = 84,
    val mood: String = "HAPPY",
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val lastInteractionAt: Long = System.currentTimeMillis()
)
