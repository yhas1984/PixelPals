package com.pixelpals.app.core.rest

import org.junit.Assert.*
import org.junit.Test

class RestFadeTransitionTest {
    @Test fun relocationOccursOnceAtZeroOpacityAndFinishesVisible() {
        for (delta: Float in listOf(1f / 120f, 1f / 60f, .1f)) {
            val transition: RestFadeTransition = RestFadeTransition()
            transition.start()
            var moves: Int = 0
            repeat(100) {
                val previousOpacity: Float = transition.opacity
                if (transition.advance(delta)) {
                    moves++
                    assertEquals("One invisible frame before moving", 0f, previousOpacity, 0f)
                    assertEquals(0f, transition.opacity, 0f)
                    assertTrue(transition.active)
                }
                assertTrue(transition.opacity in 0f..1f)
            }
            assertEquals(1, moves)
            assertFalse(transition.active)
            assertEquals(1f, transition.opacity, 0f)
        }
    }

    @Test fun cancellationRestoresVisibilityAndInvalidTimeCannotRelocate() {
        val transition: RestFadeTransition = RestFadeTransition()
        transition.start()
        for (delta: Float in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            assertFalse(transition.advance(delta))
            assertEquals(1f, transition.opacity, 0f)
        }
        transition.advance(.05f)
        transition.cancel()
        assertFalse(transition.active)
        assertEquals(1f, transition.opacity, 0f)
        assertFalse(transition.advance(.05f))
    }
}
