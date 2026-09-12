package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.CorgiBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CorgiFlingMotionTest {
    @Test fun oppositeFlingTurnsOnPlantedFeetBeforeAnticipationAndTravel(): Unit = withActor { bridge, behavior ->
        val expected = listOf(0 to false, 20 to false, 21 to false, 22 to false, 21 to true, 20 to true, 0 to true)
        for (fps in listOf(30, 60, 120)) for (left in listOf(false, true)) {
            prepare(bridge, behavior, left)
            val target = if (left) 1f else -1f
            behavior.onFling(target * 1_000f, 0f)
            val seen = mutableListOf<Pair<Int, Boolean>>()
            var elapsed = 0f
            while (bridge.state == PetState.INTERACTING && elapsed < 2f) {
                if (elapsed < .559f) {
                    val pose = bridge.currentFrame to ((bridge.animScaleX < 0f) != left)
                    if (seen.lastOrNull() != pose) seen += pose
                }
                if (elapsed < .719f) assertEquals("Turn and anticipation cannot slide", 500, bridge.getWindowParams().x)
                val previousX = bridge.getWindowParams().x
                behavior.updateInteracting(1f / fps)
                elapsed += 1f / fps
                assertTrue("Travel cannot reverse", (bridge.getWindowParams().x - previousX) * target >= 0f)
                assertEquals(bridge.groundY, bridge.getWindowParams().y)
                assertEquals(1f, kotlin.math.abs(bridge.animScaleX), 0f)
                assertEquals(1f, bridge.animScaleY, 0f)
                assertEquals(0f, bridge.animRotation, 0f)
                assertEquals(0f, bridge.animOffsetY, 0f)
            }
            assertEquals("fps=$fps left=$left", expected, seen)
            assertTrue("Turn + anticipation + burst must finish on time: $elapsed", elapsed in 1.459f..1.5f)
            assertEquals(PetState.IDLE, bridge.state)
            assertEquals((500 + target * bridge.petSpriteSize * .28f).toInt(), bridge.getWindowParams().x)
            assertEquals(target, bridge.animScaleX, 0f)
            assertEquals(0, bridge.currentFrame)
        }
    }

    @Test fun irregularStepsKeepTurnRemainderAndFinishAtTheSameEndpoint(): Unit = withActor { bridge, behavior ->
        for (left in listOf(false, true)) {
            val target = if (left) 1f else -1f
            prepare(bridge, behavior, left)
            behavior.onFling(target * 1_000f, 0f)
            behavior.updateInteracting(0f)
            behavior.updateInteracting(.6f)
            assertEquals("The .04 seconds after turning belong to anticipation", 500, bridge.getWindowParams().x)
            assertEquals(0, bridge.currentFrame)
            assertEquals(target, bridge.animScaleX, 0f)
            behavior.updateInteracting(.1f)
            assertEquals(500, bridge.getWindowParams().x)
            behavior.updateInteracting(.2f)
            assertTrue((bridge.getWindowParams().x - 500) * target > 0f)
            behavior.updateInteracting(.6f)
            assertEquals(PetState.IDLE, bridge.state)
            val endpoint = bridge.getWindowParams().x
            prepare(bridge, behavior, left)
            behavior.onFling(target * 1_000f, 0f)
            behavior.updateInteracting(2f)
            assertEquals(PetState.IDLE, bridge.state)
            assertEquals(endpoint, bridge.getWindowParams().x)
            assertEquals(0, bridge.currentFrame)
        }
    }

    @Test fun interruptsAndReducedMotionDiscardThePreviousBurst(): Unit = withActor { bridge, behavior ->
        for (left in listOf(false, true)) for (seconds in listOf(.12f, .4f, .9f)) {
            for (input in listOf("touch", "drag", "reset", "reduced", "fling")) {
                prepare(bridge, behavior, left)
                behavior.onFling(if (left) 1_000f else -1_000f, 0f)
                behavior.updateInteracting(seconds)
                val visibleDirection = bridge.animScaleX
                val x = bridge.getWindowParams().x
                when (input) {
                    "touch" -> behavior.onInteract()
                    "drag" -> { bridge.state = PetState.DRAGGING; behavior.updateDrag(.01f) }
                    "reset" -> behavior.reset()
                    "reduced" -> behavior.advanceScheduledRestTransition(.01f, true)
                    else -> behavior.onFling(visibleDirection * 1_000f, 0f)
                }
                assertEquals("$input must preserve current position", x, bridge.getWindowParams().x)
                assertEquals("$input must preserve the visible side", visibleDirection, bridge.animScaleX, 0f)
                assertEquals(0f, field("zoomTurnSeconds").getFloat(behavior), 0f)
                assertEquals(input == "fling", field("isZooming").getBoolean(behavior))
                if (input == "reduced") {
                    assertEquals(PetState.IDLE, bridge.state)
                    assertTrue(behavior.canStartScheduledSleep(true))
                    repeat(90) { behavior.advanceScheduledRestTransition(1f / 60f, true) }
                    assertEquals(x, bridge.getWindowParams().x)
                    assertEquals(0, bridge.currentFrame)
                }
                if (input == "fling") {
                    behavior.updateInteracting(.91f)
                    assertEquals(PetState.IDLE, bridge.state)
                    assertEquals(visibleDirection, bridge.animScaleX, 0f)
                    assertTrue((bridge.getWindowParams().x - x) * visibleDirection > 0f)
                }
            }
        }
    }

    private fun prepare(bridge: TestPetBridge, behavior: CorgiBehavior, left: Boolean) {
        behavior.resumeAfterCare(left, false)
        bridge.getWindowParams().x = 500
        bridge.getWindowParams().y = bridge.groundY
        bridge.state = PetState.DRAGGING
    }

    private fun field(name: String) = CorgiBehavior::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun withActor(block: (TestPetBridge, CorgiBehavior) -> Unit): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI, 160)
            behavior = CorgiBehavior(bridge, SeededPetRandom(53))
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(100) { if (loading.getBoolean(behavior)) delay(50) }
            assertFalse(loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                val rects = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }.get(behavior) as List<*>
                assertEquals(23, rects.size)
                block(bridge, behavior)
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
