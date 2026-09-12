package com.pixelpals.app

import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.BloopBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BloopFlightSubpixelTest {
    @Test fun ghostFlightHasStableDistanceAtCommonFrameRatesAndHonorsZeroDt() {
        for (fps in listOf(30, 60, 120)) withGhost { behavior, bridge ->
            setFloat(behavior, "velX", 7.5f, BaseBehavior::class.java)
            setFloat(behavior, "velY", -7.5f, BaseBehavior::class.java)
            repeat(fps) {
                invokeFlight(behavior, 1f / fps)
                val before = bridge.windowX to bridge.windowY
                invokeFlight(behavior, 0f)
                assertEquals("Paused frames must retain fractional travel", before, bridge.windowX to bridge.windowY)
            }
            assertEquals("One second X at $fps FPS", 307.5f, bridge.windowX.toFloat(), 1f)
            assertEquals("One second Y at $fps FPS", 592.5f, bridge.windowY.toFloat(), 1f)
        }
    }

    @Test fun nearlyVerticalEscapeDoesNotInjectMinorAxisJumpsOrOvershoot() {
        val finalPositions = mutableListOf<Pair<Int, Int>>()
        for (fps in listOf(30, 60, 120)) withGhost { behavior, bridge ->
            behavior.onInteract()
            setFloat(behavior, "escapeTargetX", 302f)
            setFloat(behavior, "escapeTargetY", 1_200f)
            var previous = bridge.windowX to bridge.windowY
            repeat(fps * 2) {
                behavior.updateInteracting(1f / fps)
                assertTrue("Minor-axis jump at $fps FPS", abs(bridge.windowX - previous.first) <= 1)
                assertTrue("Escape must approach without overshooting", bridge.windowX in 300..302 && bridge.windowY in previous.second..1_200)
                previous = bridge.windowX to bridge.windowY
                behavior.updateInteracting(0f)
                assertEquals("Paused escape must stay in place", previous, bridge.windowX to bridge.windowY)
            }
            assertTrue("Loaded behavior must actually travel", bridge.windowY > 1_100)
            finalPositions += bridge.windowX to bridge.windowY
        }
        assertTrue("Escape distance must be stable: $finalPositions", finalPositions.maxOf { it.second } - finalPositions.minOf { it.second } <= 2)
    }

    @Test fun nearbyEscapeArrivesWithoutOscillatingPastItsTarget() = withGhost { behavior, bridge ->
        behavior.onInteract()
        setFloat(behavior, "escapeTargetX", 302f)
        setFloat(behavior, "escapeTargetY", 604f)
        repeat(60) {
            behavior.updateInteracting(1f / 60f)
            assertTrue(bridge.windowX in 300..302 && bridge.windowY in 600..604)
        }
        assertEquals(302, bridge.windowX)
        assertEquals(604, bridge.windowY)
    }

    @Test fun dragClearsFloatingOffsetsImmediately() = withGhost { behavior, bridge ->
        behavior.updateIdle(1f / 60f)
        assertTrue("Start from a real floating pose", abs(bridge.animOffsetX) + abs(bridge.animOffsetY) > 0f)
        behavior.updateDrag(1f / 60f)
        assertEquals(0f, bridge.animOffsetX, 0f)
        assertEquals(0f, bridge.animOffsetY, 0f)
    }

    private fun withGhost(block: (BloopBehavior, TestPetBridge) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: BloopBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.BLOOP)
            behavior = BloopBehavior(bridge, SeededPetRandom(12))
        }
        try {
            val frames = BaseBehavior::class.java.getDeclaredField("frames").apply { isAccessible = true }
            var loaded = false
            for (attempt in 0 until 80) {
                instrumentation.runOnMainSync {
                    val values = frames.get(behavior) as List<*>
                    loaded = values.size == 7 && values.none { it == null }
                }
                if (loaded) break
                Thread.sleep(50)
            }
            assertTrue("Load actual ghost artwork before exercising its behavior", loaded)
            instrumentation.runOnMainSync { block(behavior, bridge) }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }

    private fun invokeFlight(behavior: BloopBehavior, dt: Float) {
        BloopBehavior::class.java.getDeclaredMethod("applyGhostFlight", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(behavior, dt)
    }

    private fun setFloat(behavior: BloopBehavior, name: String, value: Float, owner: Class<*> = BloopBehavior::class.java) {
        owner.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
    }
}
