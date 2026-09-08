package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.TelaBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class TelaRestRouteTest {
    @Test fun reducedMotionRepositionsOnlyWhileInvisibleAndCanBeCancelled(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.TELA)
            val behavior = TelaBehavior(bridge, SeededPetRandom(12))
            try {
                val startY: Int = bridge.getWindowParams().y
                behavior.onScheduledRestRequested(true)
                behavior.advanceScheduledRestTransition(.05f, true)
                assertTrue(bridge.alpha < 1f)
                behavior.onScheduledRestRequested(false)
                assertEquals(1f, bridge.alpha, 0f)
                assertEquals(startY, bridge.getWindowParams().y)
                behavior.onScheduledRestRequested(true)
                var previousY: Int = startY
                var moves: Int = 0
                repeat(90) {
                    behavior.advanceScheduledRestTransition(1f / 60f, true)
                    if (bridge.getWindowParams().y != previousY) {
                        moves++
                        assertEquals(0f, bridge.alpha, 0f)
                    }
                    if (bridge.alpha < 1f) assertFalse(behavior.canStartScheduledSleep(true))
                    previousY = bridge.getWindowParams().y
                }
                assertEquals(1, moves)
                assertEquals(bridge.bounds.floor, bridge.getWindowParams().y)
                assertEquals(1f, bridge.alpha, 0f)
                assertTrue(behavior.canStartScheduledSleep(true))
            } finally { behavior.destroy() }
        }
    }

    @Test fun restRoutesReachTheFloorFromCeilingWallsAndSilk(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.TELA)
            val starts: List<Pair<Int, Int>> = listOf(
                bridge.bounds.left to bridge.bounds.top,
                bridge.bounds.right to 600,
                400 to bridge.bounds.top,
                400 to 900,
                400 to bridge.bounds.floor,
            )
            for ((x, y) in starts) {
                val behavior = TelaBehavior(bridge, SeededPetRandom(12))
                try {
                    bridge.getWindowParams().x = x
                    bridge.getWindowParams().y = y
                    val mode = TelaBehavior::class.java.getDeclaredField("mode").apply { isAccessible = true }
                    val timer = TelaBehavior::class.java.getDeclaredField("modeTimer").apply { isAccessible = true }
                    val decide = TelaBehavior::class.java.getDeclaredMethod("decideNext").apply { isAccessible = true }
                    val originalMode: Any = mode.get(behavior)
                    behavior.onScheduledRestRequested(true)
                    assertEquals("A request must not interrupt the current action", originalMode, mode.get(behavior))
                    decide.invoke(behavior)
                    var previousX: Int = x
                    var previousY: Int = y
                    for (tick: Int in 0 until 6000) {
                        if (behavior.canStartScheduledSleep(false)) break
                        val name: String = (mode.get(behavior) as Enum<*>).name
                        val methodName: String = when (name) {
                            "CLIMB" -> "updateClimb"
                            "CEILING" -> "updateCeiling"
                            "WEB_DESCEND" -> "updateWebDescend"
                            else -> error("Unexpected rest route phase $name")
                        }
                        timer.setFloat(behavior, timer.getFloat(behavior) + 1f / 60f)
                        TelaBehavior::class.java.getDeclaredMethod(methodName, Float::class.javaPrimitiveType)
                            .apply { isAccessible = true }.invoke(behavior, 1f / 60f)
                        val position = bridge.getWindowParams()
                        assertTrue("Continuous x", abs(position.x - previousX) <= bridge.petSpriteSize * .04f + 1f)
                        assertTrue("Continuous y", abs(position.y - previousY) <= bridge.petSpriteSize * .04f + 1f)
                        previousX = position.x
                        previousY = position.y
                    }
                    assertTrue("Must reach supported sleep from $x,$y", behavior.canStartScheduledSleep(false))
                    assertEquals(bridge.bounds.floor, bridge.getWindowParams().y)
                    assertEquals(0f, bridge.animRotation, 0f)
                    assertEquals(0f, bridge.animOffsetX, 0f)
                    assertEquals(1f, bridge.animScaleY, 0f)
                } finally { behavior.destroy() }
            }
        }
    }
}
