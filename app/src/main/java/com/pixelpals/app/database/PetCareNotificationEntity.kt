package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pet_care_notification_state")
data class PetCareNotificationEntity(
    @PrimaryKey val petId: String,
    val lastNotificationType: String = "",
    val lastSentAt: Long = 0L,
    val sentDay: String = "",
    val sentCount: Int = 0,
    val snoozedUntil: Long = 0L,
)
