package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PetBondDao {
    @Query("SELECT * FROM pet_bond WHERE petId = :petId LIMIT 1")
    suspend fun getByPetId(petId: String): PetBondEntity?

    @Query("SELECT * FROM pet_bond")
    suspend fun getAll(): List<PetBondEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PetBondEntity)
}
