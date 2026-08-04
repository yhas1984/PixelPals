package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EquippedAccessoryDao {
    @Query("SELECT * FROM equipped_accessory WHERE petId = :petId LIMIT 1")
    suspend fun getByPetId(petId: String): EquippedAccessoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EquippedAccessoryEntity)

    @Query("DELETE FROM equipped_accessory WHERE petId = :petId")
    suspend fun clearForPet(petId: String)
}
