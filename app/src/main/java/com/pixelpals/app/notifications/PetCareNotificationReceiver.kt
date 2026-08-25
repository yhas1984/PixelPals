package com.pixelpals.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pixelpals.app.core.care.CareReminderPolicy
import com.pixelpals.app.core.services.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PetCareNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PetCareNotificationManager.ACTION_SNOOZE) return
        val petId: String = intent.getStringExtra(PetCareNotificationManager.EXTRA_PET_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val until: Long = System.currentTimeMillis() + CareReminderPolicy.SNOOZE_MILLIS
                PetCareNotificationStore(context).snooze(petId, until)
                PetCareNotificationManager.cancel(context)
                PetCareNotificationScheduler.schedule(context, CareReminderPolicy.SNOOZE_MILLIS)
                AppServices.analytics(context).track("care_notification_snoozed", mapOf("pet_id" to petId))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
