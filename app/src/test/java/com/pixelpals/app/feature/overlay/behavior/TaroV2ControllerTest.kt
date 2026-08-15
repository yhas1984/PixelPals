package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaroV2ControllerTest {
    @Test
    fun tapRunsTouchHidePeekFrontSocialThenIdle() {
        val controller = TaroV2Controller(FakeRandom())
        controller.reset()
        assertEquals("touch", controller.onTap().clipId)
        assertEquals("hide", controller.update(0f, 500f, 0f, 1_000f, true).clipId)
        assertEquals("peek", controller.update(0f, 500f, 0f, 1_000f, true).clipId)
        assertEquals("front_social", controller.update(0f, 500f, 0f, 1_000f, true).clipId)
        assertEquals("idle", controller.update(0f, 500f, 0f, 1_000f, true).clipId)
    }

    @Test
    fun turnChangesFacingOnlyAfterTurnClipFinishes() {
        val controller = TaroV2Controller(FakeRandom(0.1f, 0.1f))
        controller.reset()
        val turn = controller.update(4f, 900f, 0f, 1_000f, false)
        assertEquals("turn", turn.clipId)
        assertEquals(1f, turn.facing)
        val walk = controller.update(0f, 900f, 0f, 1_000f, true)
        assertEquals("walk", walk.clipId)
        assertEquals(-1f, walk.facing)
    }

    @Test
    fun idleCanReachCuriosityFrontSocialAndSleep() {
        assertEquals("curiosity", startAction(0.6f))
        assertEquals("front_social", startAction(0.8f))
        assertEquals("sleep", startAction(0.95f))
    }

    @Test
    fun dragAndFlingRecoverThroughPeek() {
        val controller = TaroV2Controller(FakeRandom())
        controller.reset()
        assertEquals("hide", controller.onDrag().clipId)
        val fling = controller.onFling(-500f)
        assertEquals("curiosity", fling.clipId)
        assertEquals(-1f, fling.facing)
        assertEquals("peek", controller.resetAfterDrop().clipId)
    }

    @Test
    fun walkOutputStaysInsideGroundBounds() {
        val controller = TaroV2Controller(FakeRandom(0.1f, 0.95f))
        controller.reset()
        var output = controller.update(4f, 100f, 0f, 1_000f, false)
        repeat(400) {
            output = controller.update(1f / 30f, output.positionX ?: 100f, 0f, 1_000f, false)
            output.positionX?.let { position ->
                assertTrue(position in 0f..1_000f)
            }
        }
        assertNotNull(output.clipId)
    }

    private fun startAction(choice: Float): String {
        val controller = TaroV2Controller(FakeRandom(choice))
        controller.reset()
        return controller.update(4f, 500f, 0f, 1_000f, false).clipId
    }

    private class FakeRandom(vararg values: Float) : PetRandom {
        private val floats = values.toMutableList()

        override fun nextFloat(): Float = floats.removeFirstOrNull() ?: 0f

        override fun nextInt(from: Int, until: Int): Int = from
    }
}
