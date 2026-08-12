package com.pixelpals.app

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopForegroundHelperTest {

    @Test
    fun latestForegroundEventWins() {
        val events = listOf(
            DesktopForegroundHelper.ForegroundEvent("com.example.other", UsageEvents.Event.ACTIVITY_RESUMED),
            DesktopForegroundHelper.ForegroundEvent("com.android.launcher", UsageEvents.Event.ACTIVITY_RESUMED),
        )

        assertEquals(
            "com.android.launcher",
            DesktopForegroundHelper.resolveLatestForegroundPackage(events, null),
        )
    }

    @Test
    fun missingEventsKeepLastKnownForegroundPackage() {
        assertEquals(
            "com.example.other",
            DesktopForegroundHelper.resolveLatestForegroundPackage(
                emptyList(),
                "com.example.other",
            ),
        )
    }

    @Test
    fun backgroundEventsDoNotReplaceLastForegroundPackage() {
        val events = listOf(
            DesktopForegroundHelper.ForegroundEvent(
                "com.example.other",
                UsageEvents.Event.ACTIVITY_PAUSED,
            ),
        )

        assertEquals(
            "com.android.launcher",
            DesktopForegroundHelper.resolveLatestForegroundPackage(events, "com.android.launcher"),
        )
    }
}
