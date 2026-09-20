package com.pixelpals.app.feature.home

import android.content.Context

class CompanionPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("pixelpals_companion", Context.MODE_PRIVATE)
    var userName: String
        get() = preferences.getString("user_name", "").orEmpty()
        set(value) { preferences.edit().putString("user_name", value).apply() }
    val firstHomePet: String? get() = preferences.getString("first_home_pet", null)
    val hasCompletedFirstHomeCare: Boolean get() = preferences.getBoolean("first_home_care", false)
    fun beginFirstHome(petId: String) {
        preferences.edit().putString("first_home_pet", petId).putBoolean("first_home_care", false).apply()
    }
    fun completeFirstHomeCare(petId: String) {
        if (firstHomePet == petId) preferences.edit().putBoolean("first_home_care", true).apply()
    }
    fun finishFirstHome() {
        preferences.edit().remove("first_home_pet").remove("first_home_care").apply()
    }
    var restSchedule: com.pixelpals.app.core.rest.PetRestSchedule
        get() = com.pixelpals.app.core.rest.PetRestSchedule(
            enabled = preferences.getBoolean("rest_schedule_enabled", true),
            followDoNotDisturb = preferences.getBoolean("rest_follow_dnd", true),
            startMinute = preferences.getInt("rest_start_minute", 23 * 60).coerceIn(0, 1439),
            endMinute = preferences.getInt("rest_end_minute", 7 * 60).coerceIn(0, 1439),
        )
        set(value) {
            preferences.edit().putBoolean("rest_schedule_enabled", value.enabled)
                .putBoolean("rest_follow_dnd", value.followDoNotDisturb)
                .putInt("rest_start_minute", value.startMinute)
                .putInt("rest_end_minute", value.endMinute).apply()
        }
    var sound: Boolean
        get() = preferences.getBoolean("sound", true)
        set(value) { preferences.edit().putBoolean("sound", value).apply() }
    var haptics: Boolean
        get() = preferences.getBoolean("haptics", true)
        set(value) { preferences.edit().putBoolean("haptics", value).apply() }
    var reducedMotion: Boolean
        get() = preferences.getBoolean("reduced_motion", false)
        set(value) { preferences.edit().putBoolean("reduced_motion", value).apply() }
    var hasSeenIntroduction: Boolean
        get() = preferences.getBoolean("introduction", false)
        set(value) { preferences.edit().putBoolean("introduction", value).apply() }
}
