package com.pixelpals.app.motion

import com.pixelpals.app.core.motion.PetGestureConfig
import com.pixelpals.app.core.motion.PetGestureRecognizer
import com.pixelpals.app.core.motion.PetGestureType
import org.junit.Assert.assertEquals
import org.junit.Test

class PetGestureRecognizerTest {
    private val recognizer = PetGestureRecognizer(
        PetGestureConfig(touchSlopPx = 8f, minimumFlingVelocityPxPerSecond = 500f)
    )

    @Test
    fun movementInsideSlopRemainsAReleaseCandidate() {
        recognizer.onDown(100f, 100f)
        assertEquals(PetGestureType.NONE, recognizer.onMove(105f, 105f).type)
        assertEquals(PetGestureType.TAP, recognizer.onUp(0f, 0f).type)
    }

    @Test
    fun movementBeyondSlopStartsAndContinuesDrag() {
        recognizer.onDown(100f, 100f)
        assertEquals(PetGestureType.DRAG_STARTED, recognizer.onMove(110f, 100f).type)
        assertEquals(PetGestureType.DRAGGED, recognizer.onMove(130f, 100f).type)
        assertEquals(PetGestureType.RELEASE, recognizer.onUp(20f, 10f).type)
    }

    @Test
    fun fastReleaseIsClassifiedAsFling() {
        recognizer.onDown(100f, 100f)
        recognizer.onMove(120f, 100f)
        val result = recognizer.onUp(600f, -100f)
        assertEquals(PetGestureType.FLING, result.type)
        assertEquals(600f, result.velocityX, 0.01f)
        assertEquals(-100f, result.velocityY, 0.01f)
    }

    @Test
    fun cancelResetsTheNextGestureToTap() {
        recognizer.onDown(100f, 100f)
        recognizer.onMove(120f, 100f)
        assertEquals(PetGestureType.CANCEL, recognizer.onCancel().type)
        recognizer.onDown(100f, 100f)
        assertEquals(PetGestureType.TAP, recognizer.onUp(0f, 0f).type)
    }
}
