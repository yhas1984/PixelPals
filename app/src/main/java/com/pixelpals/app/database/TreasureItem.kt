package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treasures")
data class TreasureItem(
    @PrimaryKey
    val emoji: String,
    val count: Int,
    val firstFoundAt: Long,
    val lastFoundAt: Long
)
