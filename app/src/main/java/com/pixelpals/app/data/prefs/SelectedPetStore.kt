package com.pixelpals.app.data.prefs

import android.content.Context
import com.pixelpals.app.core.domain.PetType

class SelectedPetStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(type: PetType) {
        prefs.edit().putString(KEY_SELECTED_PET, type.name).apply()
    }

    fun load(default: PetType = PetType.CORGI): PetType {
        val raw = prefs.getString(KEY_SELECTED_PET, null) ?: return default
        return runCatching { PetType.valueOf(raw) }.getOrElse { default }
    }

    companion object {
        private const val PREFS_NAME = "pixelpals_selection"
        private const val KEY_SELECTED_PET = "selected_pet"
    }
}
