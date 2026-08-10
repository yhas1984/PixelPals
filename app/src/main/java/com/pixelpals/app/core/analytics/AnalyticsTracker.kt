package com.pixelpals.app.core.analytics

interface AnalyticsTracker {
    fun track(event: String, properties: Map<String, String> = emptyMap())
}
