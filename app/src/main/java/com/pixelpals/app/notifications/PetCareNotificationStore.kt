package com.pixelpals.app.notifications

import android.content.Context
import com.pixelpals.app.core.care.CareReminderState
import com.pixelpals.app.core.care.CareReminderType
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.PetCareNotificationEntity

class PetCareNotificationStore(context: Context) {
    private val dao = AppDatabase.getDatabase(context.applicationContext).petCareNotificationDao()

    suspend fun getState(petId: String): CareReminderState {
        val entity: PetCareNotificationEntity = dao.getByPetId(petId)
            ?: PetCareNotificationEntity(petId)
        return CareReminderState(
            lastType = CareReminderType.entries.firstOrNull { it.name == entity.lastNotificationType },
            lastSentAt = entity.lastSentAt,
            sentDay = entity.sentDay,
            sentCount = entity.sentCount,
            snoozedUntil = entity.snoozedUntil,
        )
    }

    suspend fun recordSent(petId: String, type: CareReminderType, now: Long, todayKey: String) {
        val current: PetCareNotificationEntity = dao.getByPetId(petId)
            ?: PetCareNotificationEntity(petId)
        val count: Int = if (current.sentDay == todayKey) current.sentCount + 1 else 1
        dao.upsert(
            current.copy(
                lastNotificationType = type.name,
                lastSentAt = now,
                sentDay = todayKey,
                sentCount = count,
                snoozedUntil = 0L,
            )
        )
    }

    suspend fun snooze(petId: String, until: Long) {
        val current: PetCareNotificationEntity = dao.getByPetId(petId)
            ?: PetCareNotificationEntity(petId)
        dao.upsert(current.copy(snoozedUntil = until))
    }

    suspend fun clear(petId: String) {
        dao.deleteByPetId(petId)
    }
}
