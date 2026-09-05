package com.pixelpals.app.feature.home

import android.content.Context

class CompanionPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("pixelpals_companion", Context.MODE_PRIVATE)
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
