package com.pixelpals.app.feature.overlay.behavior

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class PiruScheduledRestTest {
    @Test
    fun restRequestOnGroundShowsYawnThenSleep(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: PiruBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
            bridge.getWindowParams().y = bridge.groundY
            bridge.updateWindowLayout(bridge.getWindowParams())
            behavior = PiruBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                behavior.onScheduledRestRequested(true)
                behavior.updateIdle(1f / 60f)
                assertEquals("Piru must begin resting with a yawn", 14, bridge.currentFrame)
                assertTrue("Piru became sleep-eligible before settling", !behavior.canStartScheduledSleep(false))
                repeat(22) { behavior.updateIdle(1f / 60f) }
                assertEquals("Piru must hold the sleep pose after settling", 15, bridge.currentFrame)
                assertTrue("Piru did not become sleep-eligible after settling", behavior.canStartScheduledSleep(false))
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test
    fun restRequestDuringJumpDoesNotChangeTrajectoryOrAddLandingStep(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var controlBridge: TestPetBridge
        lateinit var requestedBridge: TestPetBridge
        lateinit var control: PiruBehavior
        lateinit var requested: PiruBehavior
        instrumentation.runOnMainSync {
            controlBridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
            requestedBridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
            controlBridge.getWindowParams().y = controlBridge.groundY
            controlBridge.updateWindowLayout(controlBridge.getWindowParams())
            requestedBridge.getWindowParams().y = requestedBridge.groundY
            requestedBridge.updateWindowLayout(requestedBridge.getWindowParams())
            control = PiruBehavior(controlBridge, deterministicRandom())
            requested = PiruBehavior(requestedBridge, deterministicRandom())
        }
        try {
            waitForAssets(control, instrumentation)
            waitForAssets(requested, instrumentation)
            instrumentation.runOnMainSync {
                val startJump = PiruBehavior::class.java.getDeclaredMethod("startJump").apply { isAccessible = true }
                startJump.invoke(control)
                startJump.invoke(requested)
                requested.onScheduledRestRequested(true)
                var landed: Boolean = false
                repeat(60) {
                    if (!landed) {
                        control.updateIdle(1f / 60f)
                        requested.updateIdle(1f / 60f)
                        assertEquals("Rest request altered Piru jump trajectory", controlBridge.windowY, requestedBridge.windowY)
                        assertEquals(controlBridge.windowX, requestedBridge.windowX)
                        landed = controlBridge.windowY == controlBridge.groundY
                    }
                }
                assertTrue("Piru jump did not land", landed)
                assertEquals("Rest request added a landing position step", controlBridge.windowY, requestedBridge.windowY)
                repeat(22) { requested.updateIdle(1f / 60f) }
                assertTrue("Piru was not eligible after post-landing settling", requested.canStartScheduledSleep(false))
            }
        } finally {
            instrumentation.runOnMainSync {
                control.destroy()
                requested.destroy()
            }
        }
    }

    @Test
    fun waddleRestWaitsForRouteAndCancellationPreventsSleep(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: PiruBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
            behavior = PiruBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                behavior.updateIdle(1f / 60f)
                behavior.onScheduledRestRequested(true)
                val target: Int = getFloat(behavior, "swimTargetX").roundToInt()
                val routeTicks: Int = kotlin.math.ceil(getFloat(behavior, "swimDuration") * 60f).toInt() + 2
                repeat(routeTicks) {
                    if (modeName(behavior) == "WADDLE") behavior.updateIdle(1f / 60f)
                }
                assertEquals("Piru rest cut its waddle route", target, bridge.windowX)
                assertEquals("Piru never entered rest after finishing waddle", "SLEEP", modeName(behavior))

                behavior.onScheduledRestRequested(false)
                behavior.reset()
                behavior.onScheduledRestRequested(true)
                behavior.updateIdle(1f / 60f)
                behavior.onScheduledRestRequested(false)
                repeat(240) {
                    behavior.updateIdle(1f / 60f)
                    assertNotEquals("Cancelled rest entered sleep", "SLEEP", modeName(behavior))
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test
    fun wakingSleepKeepsEligibilityUntilScheduledSleepOwnsWake(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: PiruBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
            behavior = PiruBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                setMode(behavior, "SLEEP")
                setFloat(behavior, "modeTimer", .35f)
                behavior.onScheduledRestRequested(false)
                assertTrue("Piru lost sleep eligibility before wake playback", behavior.canStartScheduledSleep(false))
                behavior.updateIdle(1f / 60f)
                assertEquals("Piru did not return to waddle after wake ownership ended", "WADDLE", modeName(behavior))
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun waitForAssets(behavior: PiruBehavior, instrumentation: android.app.Instrumentation): Unit {
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (!loading.getBoolean(behavior)) return
            Thread.sleep(50)
        }
        assertTrue("Piru artwork did not load", !loading.getBoolean(behavior))
    }

    private fun modeName(behavior: PiruBehavior): String = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }.get(behavior).toString()

    private fun setMode(behavior: PiruBehavior, name: String): Unit {
        val field = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }
        field.set(behavior, field.type.enumConstants!!.first { it.toString() == name })
    }

    private fun setFloat(behavior: PiruBehavior, name: String, value: Float): Unit {
        behavior.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
    }

    private fun getFloat(behavior: PiruBehavior, name: String): Float =
        behavior.javaClass.getDeclaredField(name).apply { isAccessible = true }.getFloat(behavior)

    private fun deterministicRandom(): PetRandom = object : PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }
}
