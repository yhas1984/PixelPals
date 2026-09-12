package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class SubpixelTravelTest {
    @Test fun movementIsStableAtThirtySixtyAndOneHundredTwentyHertz(): Unit {
        for (rate: Int in listOf(30, 60, 120)) for (direction: Int in listOf(-1, 1)) {
            val travel: SubpixelTravel = SubpixelTravel()
            var position: Int = 0
            repeat(rate * 10) { position += travel.advance(direction * 36f / rate) }
            assertEquals("Movement at $rate Hz", direction * 360f, position.toFloat(), 1f)
        }
    }
    @Test fun changingDirectionDoesNotLeaveAStaleFractionAfterReset(): Unit {
        val travel: SubpixelTravel = SubpixelTravel()
        travel.advance(.9f)
        travel.reset()
        assertEquals(0, travel.advance(-.5f))
        assertEquals(-1, travel.advance(-.5f))
    }
}
