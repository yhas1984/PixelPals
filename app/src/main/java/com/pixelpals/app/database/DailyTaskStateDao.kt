package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyTaskStateDao {
    @Query("SELECT * FROM daily_task_state WHERE petId = :petId AND dayKey = :dayKey")
    suspend fun getTasksForDay(petId: String, dayKey: String): List<DailyTaskStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyTaskStateEntity)
}
