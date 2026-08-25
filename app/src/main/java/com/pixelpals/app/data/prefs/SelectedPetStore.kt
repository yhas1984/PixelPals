package com.pixelpals.app.data.prefs

import android.content.Context
import com.pixelpals.app.core.domain.PetType

class SelectedPetStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(type: PetType) {
        val previous: String? = prefs.getString(KEY_SELECTED_PET, null)
        prefs.edit().apply {
            putString(KEY_SELECTED_PET, type.name)
            if (previous != type.name) putLong(KEY_SELECTED_AT, System.currentTimeMillis())
        }.apply()
    }

    fun setPetEnabled(enabled: Boolean) {
        val wasEnabled: Boolean = isPetEnabled()
        prefs.edit().apply {
            putBoolean(KEY_PET_ENABLED, enabled)
            if (enabled) {
                val now: Long = System.currentTimeMillis()
                putLong(KEY_PET_ENABLED_AT, now)
                if (!wasEnabled) putLong(KEY_SELECTED_AT, now)
            } else {
                remove(KEY_PET_ENABLED_AT)
            }
        }.commit()
    }

    fun isPetEnabled(): Boolean {
        if (prefs.contains(KEY_PET_ENABLED)) return prefs.getBoolean(KEY_PET_ENABLED, false)
        return prefs.contains(KEY_SELECTED_PET)
    }

    fun getPetEnabledAt(): Long? {
        if (!prefs.contains(KEY_PET_ENABLED_AT)) return null
        return prefs.getLong(KEY_PET_ENABLED_AT, 0L).takeIf { it > 0L }
    }

    fun getSelectedAt(): Long? {
        if (!prefs.contains(KEY_SELECTED_AT)) return null
        return prefs.getLong(KEY_SELECTED_AT, 0L).takeIf { it > 0L }
    }

    fun load(default: PetType = PetType.CORGI): PetType {
        val raw = prefs.getString(KEY_SELECTED_PET, null) ?: return default
        return runCatching { PetType.valueOf(raw) }.getOrElse { default }
    }

    companion object {
        private const val PREFS_NAME = "pixelpals_selection"
        private const val KEY_SELECTED_PET = "selected_pet"
        private const val KEY_PET_ENABLED = "pet_enabled"
        private const val KEY_PET_ENABLED_AT = "pet_enabled_at"
        private const val KEY_SELECTED_AT = "selected_at"
    }
}
