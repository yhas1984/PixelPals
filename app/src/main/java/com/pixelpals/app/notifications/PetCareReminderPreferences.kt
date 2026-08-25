package com.pixelpals.app.notifications

import android.content.Context

class PetCareReminderPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(isEnabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, isEnabled).apply()
    }

    private companion object {
        const val PREFS_NAME: String = "pixelpals_care_reminders"
        const val KEY_ENABLED: String = "enabled"
    }
}
