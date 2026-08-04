package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PetStatusDao {
    @Query("SELECT * FROM pet_status WHERE petId = :petId LIMIT 1")
    suspend fun getByPetId(petId: String): PetStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PetStatusEntity)
}
