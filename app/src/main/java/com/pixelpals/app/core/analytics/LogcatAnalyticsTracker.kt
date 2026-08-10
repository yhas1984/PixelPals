package com.pixelpals.app.core.analytics

import android.util.Log

class LogcatAnalyticsTracker : AnalyticsTracker {
    override fun track(event: String, properties: Map<String, String>) {
        val props = properties.entries.joinToString(",") { (key, value) -> "$key=$value" }
        Log.d("PIXELPALS_ANALYTICS", if (props.isEmpty()) event else "$event $props")
    }
}

class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: String, properties: Map<String, String>) = Unit
}
