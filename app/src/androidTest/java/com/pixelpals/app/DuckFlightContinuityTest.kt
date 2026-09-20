package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.DuckBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DuckFlightContinuityTest {
    @Test fun takeoffUsesGroundAnticipationButAirborneLaunchKeepsWingsAt30And120Fps(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (size in intArrayOf(160, 240)) for (fps in intArrayOf(30, 120)) {
            lateinit var bridge: TestPetBridge
            lateinit var behavior: DuckBehavior
            instrumentation.runOnMainSync {
                bridge = TestPetBridge(instrumentation.targetContext, PetType.PATITO, size)
                behavior = DuckBehavior(bridge, SeededPetRandom(size + fps))
            }
            try {
                val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
                var ready = false
                for (attempt in 0 until 80) {
                    instrumentation.runOnMainSync { ready = !loading.getBoolean(behavior) }
                    if (ready) break
                    Thread.sleep(25)
                }
                assertTrue("Patito assets must load", ready)
                instrumentation.runOnMainSync {
                    val dt = 1f / fps
                    val params = bridge.getWindowParams()
                    params.y = bridge.groundY
                    bridge.updateWindowLayout(params)
                    behavior.onInteract()
                    assertEquals("ground launch starts neutral", 8, bridge.currentFrame)
                    assertEquals("ground entry scale is facing-neutral", 1f, kotlin.math.abs(bridge.animScaleX), 0f)
                    assertEquals("ground entry has no rotation", 0f, bridge.animRotation, 0f)
                    assertEquals("ground entry has no offset", 0f, bridge.animOffsetY, 0f)
                    repeat(((.10f / dt).toInt() - 1).coerceAtLeast(0)) { behavior.updateInteracting(dt) }
                    assertEquals("ground anticipation holds neutral", 8, bridge.currentFrame)
                    assertEquals("ground anticipation stays planted", bridge.groundY, bridge.windowY)
                    behavior.updateInteracting(dt)
                    assertTrue("ground launch opens wings after anticipation", bridge.currentFrame == 4 || bridge.currentFrame == 9)
                    assertEquals("body scale remains neutral", 1f, bridge.animScaleY, 0f)

                    params.y = bridge.groundY - size
                    bridge.updateWindowLayout(params)
                    behavior.onInteract()
                    assertEquals("airborne touch starts with wings", 4, bridge.currentFrame)
                    assertEquals("airborne touch keeps height before tick", bridge.groundY - size, bridge.windowY)
                    assertEquals("airborne touch has no rotation", 0f, bridge.animRotation, 0f)
                    behavior.updateInteracting(dt)
                    assertTrue("airborne launch does not show standing pose", bridge.currentFrame == 4 || bridge.currentFrame == 9)
                    assertEquals("airborne body scale remains neutral", 1f, bridge.animScaleY, 0f)

                    behavior.onFling(240f, -180f)
                    assertEquals("airborne fling starts with wings", 4, bridge.currentFrame)
                    assertEquals("airborne fling has no rotation", 0f, bridge.animRotation, 0f)
                    assertEquals("airborne fling body scale remains neutral", 1f, bridge.animScaleY, 0f)
                }
            } finally {
                instrumentation.runOnMainSync { behavior.destroy() }
            }
        }
    }

    @Test fun flightReturnsToGroundWithoutResettingTakeoffHeight(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: DuckBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PATITO)
            behavior = DuckBehavior(bridge, SeededPetRandom(12))
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            var ready = false
            for (attempt in 0 until 80) {
                instrumentation.runOnMainSync { ready = !loading.getBoolean(behavior) }
                if (ready) break
                Thread.sleep(50)
            }
            assertTrue("Real duck assets must load", ready)
            instrumentation.runOnMainSync {
                val mode = DuckBehavior::class.java.getDeclaredField("mode").apply { isAccessible = true }
                bridge.getWindowParams().y = bridge.groundY
                behavior.onInteract()
                val phases: MutableSet<String> = mutableSetOf()
                var previousY: Int = bridge.getWindowParams().y
                var previousX: Int = bridge.getWindowParams().x
                var reachedRest: Boolean = false
                for (tick: Int in 0 until 900) {
                    behavior.updateInteracting(1f / 60f)
                    val phase: String = (mode.get(behavior) as Enum<*>).name
                    phases.add(phase)
                    val position = bridge.getWindowParams()
                    assertTrue("No vertical discontinuity in $phase", abs(position.y - previousY) <= bridge.petSpriteSize * .08f + 1f)
                    assertTrue("Bounded horizontal flight in $phase", abs(position.x - previousX) <= bridge.petSpriteSize * .05f + 1f)
                    previousX = position.x
                    previousY = position.y
                    if (behavior.canStartScheduledSleep(false)) {
                        assertEquals("QUACK", phase)
                        assertEquals(bridge.groundY, position.y)
                        reachedRest = true
                        break
                    }
                }
                assertTrue("A flight must reach a sleep-safe pause", reachedRest)
                assertTrue(phases.containsAll(setOf("TAKEOFF", "FLUTTER", "LANDING", "LAND_END", "QUACK")))
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
