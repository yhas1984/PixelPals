package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TreasureDao {

    @Query("SELECT * FROM treasures ORDER BY lastFoundAt DESC")
    fun getAllTreasures(): Flow<List<TreasureItem>>

    @Query("SELECT * FROM treasures")
    suspend fun getAllTreasuresSnapshot(): List<TreasureItem>

    @Query("SELECT * FROM treasures WHERE emoji = :emoji LIMIT 1")
    suspend fun getTreasure(emoji: String): TreasureItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTreasure(treasure: TreasureItem): Long

    @Update
    suspend fun updateTreasure(treasure: TreasureItem): Int

}
