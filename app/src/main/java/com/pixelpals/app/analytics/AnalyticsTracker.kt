package com.pixelpals.app.analytics

interface AnalyticsTracker {
    fun track(event: String, properties: Map<String, String> = emptyMap())
}
