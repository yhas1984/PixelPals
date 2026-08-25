package com.pixelpals.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pixelpals.app.core.care.CareReminderContext
import com.pixelpals.app.core.care.CareReminderDecision
import com.pixelpals.app.core.care.CareReminderPolicy
import com.pixelpals.app.core.care.CareReminderState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.status.PetStatusSnapshot
import java.time.LocalDate
import java.time.ZonedDateTime

class PetCareWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val preferences = PetCareReminderPreferences(applicationContext)
        val selectedPetStore = SelectedPetStore(applicationContext)
        if (!preferences.isEnabled() || !selectedPetStore.isPetEnabled()) return Result.success()
        val petType: PetType = selectedPetStore.load()
        val repository = AppServices.repository(applicationContext)
        val snapshot: PetStatusSnapshot = repository.getStatusSnapshot(petType)
        val notificationStore = PetCareNotificationStore(applicationContext)
        val reminderState: CareReminderState = notificationStore.getState(snapshot.petId)
        val now: Long = System.currentTimeMillis()
        val policy = CareReminderPolicy()
        val decision: CareReminderDecision? = policy.decide(
            CareReminderContext(
                snapshot = snapshot,
                state = reminderState,
                now = now,
                todayKey = LocalDate.now().toString(),
                localHour = ZonedDateTime.now().hour,
            )
        )
        val wasShown: Boolean = if (
            decision != null &&
            PetCareNotificationManager.canNotify(applicationContext)
        ) {
            PetCareNotificationManager.show(applicationContext, petType, decision)
        } else {
            false
        }
        if (decision != null && wasShown) {
            notificationStore.recordSent(snapshot.petId, decision.type, now, LocalDate.now().toString())
            AppServices.analytics(applicationContext).track(
                "care_notification_shown",
                mapOf("pet_id" to snapshot.petId, "type" to decision.type.name.lowercase()),
            )
        }
        val nextDelay: Long = policy.getNextEvaluationDelayMillis(snapshot, now)
        PetCareNotificationScheduler.scheduleAfterCurrent(applicationContext, nextDelay)
        return Result.success()
    }
}
