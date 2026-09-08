package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundGaitTest {
    @Test fun longAndShortWalksRespectPeakSpeedAndStartAndEndAtRest(): Unit {
        for (distance: Float in listOf(10f, 300f, 1200f)) {
            val duration: Float = GroundGait.duration(distance, 100f, 2f)
            var previous: Float = 0f
            repeat(1000) { index ->
                val current: Float = GroundGait.progress((index + 1) * duration / 1000, duration) * distance
                val speed: Float = (current - previous) / (duration / 1000)
                assertTrue("Negative or excessive speed: $speed", speed in -.1f..100.2f)
                if (index == 0 || index == 999) assertTrue("Abrupt endpoint speed: $speed", speed < .5f)
                previous = current
            }
            assertEquals(distance, previous, .001f)
        }
    }

    @Test fun framePhaseDependsOnBodyDistanceInsteadOfRefreshRateOrDensity(): Unit {
        for (density: Float in listOf(1f, 2f, 3f)) {
            for (rate: Int in listOf(30, 60, 120)) {
                val duration: Float = GroundGait.duration(300f * density, 100f * density, 2f)
                val distance: Float = GroundGait.progress((rate * 2).toFloat() / rate, duration) * 300f * density
                assertEquals(GroundGait.phase(GroundGait.progress(2f, duration) * 300f, 40f),
                    GroundGait.phase(distance, 40f * density), .0001f)
            }
        }
    }
}
