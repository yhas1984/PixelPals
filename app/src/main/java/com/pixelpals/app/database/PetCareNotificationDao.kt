package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PetCareNotificationDao {
    @Query("SELECT * FROM pet_care_notification_state WHERE petId = :petId LIMIT 1")
    suspend fun getByPetId(petId: String): PetCareNotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PetCareNotificationEntity)

    @Query("DELETE FROM pet_care_notification_state WHERE petId = :petId")
    suspend fun deleteByPetId(petId: String)
}
