package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TreasureCollectionStateDao {
    @Query("SELECT * FROM treasure_collection_state WHERE id = 1 LIMIT 1")
    suspend fun getState(): TreasureCollectionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TreasureCollectionStateEntity)
}
