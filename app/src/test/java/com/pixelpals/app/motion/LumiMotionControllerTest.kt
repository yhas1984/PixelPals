package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumiMotionControllerTest {
    @Test
    fun interactionsAlternateBetweenSocialAndMagic() {
        val controller = controller()

        assertEquals("front_social", controller.startInteraction())
        controller.update(1f, shouldSleep = false)
        assertEquals("magic", controller.startInteraction())
    }

    @Test
    fun autonomousMovementStaysInsideViewport() {
        val controller = controller()
        controller.setPosition(120f, 700f)

        repeat(600) {
            controller.update(1f / 60f, shouldSleep = false)
            val pose = controller.getPose()
            assertTrue(pose.x in 40f..1_040f)
            assertTrue(pose.y in 40f..2_360f)
            assertTrue(pose.frameIndex in 0..39)
        }
    }

    private fun controller(): LumiMotionController {
        val controller = LumiMotionController(FixedRandom(0.7f))
        controller.setSpec(
            LumiMotionSpec(
                clips = listOf(
                    LumiMotionClip("idle", listOf(0, 1), loop = true, frameDurationSeconds = 0.2f),
                    LumiMotionClip("walk", listOf(2, 3), loop = true, frameDurationSeconds = 0.1f),
                    LumiMotionClip("turn", listOf(4, 5), loop = false, frameDurationSeconds = 0.1f),
                    LumiMotionClip("hop_up", listOf(6), loop = false, frameDurationSeconds = 0.1f),
                    LumiMotionClip("hop_down", listOf(7), loop = false, frameDurationSeconds = 0.1f),
                    LumiMotionClip("pounce", listOf(8), loop = false, frameDurationSeconds = 0.1f),
                    LumiMotionClip("front_social", listOf(9), loop = false, frameDurationSeconds = 0.1f),
                    LumiMotionClip("sleep", listOf(10), loop = true, frameDurationSeconds = 0.2f),
                    LumiMotionClip("magic", listOf(11), loop = false, frameDurationSeconds = 0.1f),
                ).associateBy { it.id },
            )
        )
        controller.updateViewport(1_080, 2_400, 80f, 0, 0)
        return controller
    }

    private class FixedRandom(private val value: Float) : PetRandom {
        override fun nextFloat(): Float = value

        override fun nextInt(from: Int, until: Int): Int = from
    }
}
