package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equipped_accessory")
data class EquippedAccessoryEntity(
    @PrimaryKey val petId: String,
    val accessoryId: String,
    val equippedAt: Long = System.currentTimeMillis()
)
