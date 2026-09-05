package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "companion_home")
data class CompanionHomeEntity(
    @PrimaryKey val petId: String,
    val nickname: String = "",
    val environment: String = "COZY",
    val adoptedAt: Long = 0,
    val feedCount: Int = 0,
    val playCount: Int = 0,
    val touchCount: Int = 0,
    val lastLearnedAt: Long = 0,
    val favoriteObject: String = "ball",
    val desktopObject: String = "ball",
)

@Entity(tableName = "home_decoration", primaryKeys = ["petId", "decorationId"])
data class HomeDecorationEntity(val petId: String, val decorationId: String, val column: Int, val row: Int)

@Entity(tableName = "decoration_inventory")
data class DecorationInventoryEntity(@PrimaryKey val decorationId: String, val acquiredAt: Long)

@Entity(tableName = "companion_journal")
data class CompanionJournalEntity(@PrimaryKey val id: String, val petId: String, val kind: String, val detail: String, val occurredAt: Long)

@Entity(tableName = "companion_expedition")
data class CompanionExpeditionEntity(
    @PrimaryKey val slot: Int = 1,
    val requestId: String,
    val petId: String,
    val destination: String,
    val startedAt: Long,
    val lastWallTime: Long,
    val lastUptime: Long,
    val bootCount: Int,
    val elapsedMs: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val resumeDesktop: Boolean = false,
)
