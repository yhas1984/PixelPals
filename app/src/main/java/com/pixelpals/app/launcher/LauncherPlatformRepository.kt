package com.pixelpals.app.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings

object LauncherPlatformRepository {
    private const val PREFS_NAME = "pixelpals_launcher_platforms"
    private const val KEY_RECTS = "rects"
    private const val KEY_TIMESTAMP = "timestamp"

    data class PlatformPoint(val x: Float, val y: Float)

    fun saveRects(context: Context, rects: List<Rect>) {
        if (rects.isEmpty()) return
        val encoded = rects.joinToString(";") { rect ->
            "${rect.left},${rect.top},${rect.right},${rect.bottom}"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECTS, encoded)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun loadPlatformPoints(
        context: Context,
        petSpriteSize: Int,
        freshnessMs: Long = 15_000L
    ): List<PlatformPoint> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > freshnessMs) return emptyList()

        val encoded = prefs.getString(KEY_RECTS, null).orEmpty()
        if (encoded.isBlank()) return emptyList()

        val standOffsetY = petSpriteSize * 0.62f
        return encoded
            .split(';')
            .mapNotNull { token ->
                val values = token.split(',')
                if (values.size != 4) return@mapNotNull null
                val left = values[0].toFloatOrNull() ?: return@mapNotNull null
                val top = values[1].toFloatOrNull() ?: return@mapNotNull null
                val right = values[2].toFloatOrNull() ?: return@mapNotNull null
                val centerX = (left + right) / 2f
                PlatformPoint(
                    x = centerX - petSpriteSize / 2f,
                    y = top - standOffsetY
                )
            }
    }

    fun isServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expected = ComponentName(context, serviceClass).flattenToString()
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun buildAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
