package com.pixelpals.app.core.rest

import android.app.NotificationManager
import android.content.Context
import java.time.LocalTime

/** Reads the current system state without changing the user's interruption policy. */
class DeviceRestState(context: Context) {
    private val notificationManager: NotificationManager? =
        context.applicationContext.getSystemService(NotificationManager::class.java)

    private var lastRead: Long = -1L
    private var cachedFilter: Int = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    fun shouldRest(schedule: PetRestSchedule, time: LocalTime = LocalTime.now()): Boolean {
        val now: Long = android.os.SystemClock.elapsedRealtime()
        if (lastRead < 0L || now - lastRead >= 1000L) {
            cachedFilter = readFilter()
            lastRead = now
        }
        val filter: Int = cachedFilter
        val doNotDisturb: Boolean = filter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            filter == NotificationManager.INTERRUPTION_FILTER_ALARMS
        return schedule.shouldRest(time.hour * 60 + time.minute, doNotDisturb)
    }

    private fun readFilter(): Int = try {
            notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        } catch (_: SecurityException) {
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
}
