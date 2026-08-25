package com.pixelpals.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PetCareNotificationScheduler {
    private const val UNIQUE_WORK_NAME: String = "pixelpals_pet_care_reminder"
    private const val DEFAULT_DELAY_MILLIS: Long = 30L * 60L * 1_000L

    fun schedule(context: Context, delayMillis: Long = DEFAULT_DELAY_MILLIS) {
        enqueue(context, delayMillis, ExistingWorkPolicy.REPLACE)
    }

    fun scheduleAfterCurrent(context: Context, delayMillis: Long) {
        enqueue(context, delayMillis, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        PetCareNotificationManager.cancel(context)
    }

    private fun enqueue(context: Context, delayMillis: Long, policy: ExistingWorkPolicy) {
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<PetCareWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }
}
