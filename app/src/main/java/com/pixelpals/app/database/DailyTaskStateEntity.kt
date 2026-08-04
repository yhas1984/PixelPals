package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_task_state")
data class DailyTaskStateEntity(
    @PrimaryKey val id: String,
    val petId: String,
    val taskId: String,
    val dayKey: String,
    val completedAt: Long,
    val rewardCoins: Int
)
