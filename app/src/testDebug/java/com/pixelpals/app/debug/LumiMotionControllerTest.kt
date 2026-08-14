package com.pixelpals.app.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumiMotionControllerTest {
    private val controller = LumiMotionController(density = 1f)

    @Test
    fun walkDoesNotOvershootTarget() {
        assertEquals(420f, controller.walkX(100f, 420f, 1f, 500f, 1f), 0.01f)
        assertEquals(100f, controller.walkX(420f, 100f, -1f, 500f, 1f), 0.01f)
    }

    @Test
    fun hopRisesAboveStartAndLandsAtTargetHeight() {
        val start = controller.hopPoint(0f, 100f, 160f, 600f, 500f)
        val apex = controller.hopPoint(0.62f, 100f, 160f, 600f, 500f)
        val end = controller.hopPoint(1f, 100f, 160f, 600f, 500f)
        assertTrue(apex.y < start.y)
        assertEquals(500f, end.y, 0.01f)
        assertEquals(160f, end.x, 0.01f)
    }

    @Test
    fun hopCanDescendToLowerTarget() {
        val apex = controller.hopPoint(0.62f, 100f, 160f, 500f, 700f)
        val end = controller.hopPoint(1f, 100f, 160f, 500f, 700f)
        assertTrue(apex.y < 700f)
        assertTrue(apex.y > 500f)
        assertEquals(700f, end.y, 0.01f)
    }
}
