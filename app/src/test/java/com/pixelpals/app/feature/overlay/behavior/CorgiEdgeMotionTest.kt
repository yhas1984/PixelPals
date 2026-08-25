package com.pixelpals.app.feature.overlay.behavior

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorgiEdgeMotionTest {
    @Test
    fun zeroPixelStepAtLeftEdgeDoesNotReverseWhenFacingInward() {
        assertFalse(
            CorgiEdgeMotion.shouldReverse(
                positionX = 0,
                proposedX = 0,
                maxX = 900,
                direction = 1f,
            ),
        )
    }

    @Test
    fun zeroPixelStepAtEitherEdgeReversesOnlyWhenFacingOutward() {
        assertTrue(CorgiEdgeMotion.shouldReverse(0, 0, 900, -1f))
        assertTrue(CorgiEdgeMotion.shouldReverse(900, 900, 900, 1f))
        assertFalse(CorgiEdgeMotion.shouldReverse(900, 900, 900, -1f))
    }

    @Test
    fun overshootReversesAwayFromTheBoundary() {
        assertTrue(CorgiEdgeMotion.shouldReverse(1, -2, 900, -1f))
        assertTrue(CorgiEdgeMotion.shouldReverse(899, 902, 900, 1f))
    }
}
