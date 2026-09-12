package com.pixelpals.app.feature.overlay.behavior

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PiruDesktopMotionTest {
    @Test
    fun autonomousJumpKeepsTrajectoryWhenTappedAt30_60_120Fps(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rates: List<Int> = listOf(30, 60, 120)
        for (rate: Int in rates) {
            val dt: Float = 1f / rate
            lateinit var untouchedBridge: TestPetBridge
            lateinit var tappedBridge: TestPetBridge
            lateinit var untouched: PiruBehavior
            lateinit var tapped: PiruBehavior
            val predictable: PetRandom = object : PetRandom {
                override fun nextFloat(): Float = .5f
                override fun nextInt(from: Int, until: Int): Int = from
            }
            instrumentation.runOnMainSync {
                untouchedBridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
                tappedBridge = TestPetBridge(instrumentation.targetContext, PetType.PIRU)
                untouched = PiruBehavior(untouchedBridge, predictable)
                tapped = PiruBehavior(tappedBridge, predictable)
            }
            try {
                val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
                var attempts: Int = 0
                while (attempts++ < 40 && (loading.getBoolean(untouched) || loading.getBoolean(tapped))) {
                    Thread.sleep(50)
                    instrumentation.waitForIdleSync()
                }
                assertTrue("Piru assets not loaded at $rate fps", !loading.getBoolean(untouched) && !loading.getBoolean(tapped))
                instrumentation.runOnMainSync {
                    val startJump = PiruBehavior::class.java.getDeclaredMethod("startJump").apply { isAccessible = true }
                    untouchedBridge.getWindowParams().y = untouchedBridge.groundY
                    untouchedBridge.updateWindowLayout(untouchedBridge.getWindowParams())
                    tappedBridge.getWindowParams().y = tappedBridge.groundY
                    tappedBridge.updateWindowLayout(tappedBridge.getWindowParams())
                    startJump.invoke(untouched)
                    startJump.invoke(tapped)
                    val tapTick: Int = (rate / 5).coerceAtLeast(1)
                    var tick: Int = 0
                    var landed: Boolean = false
                    var interacted: Boolean = false
                    while (!landed && tick < rate * 2) {
                        if (tick == tapTick) {
                            assertTrue("Touch must occur in the air", tappedBridge.windowY < tappedBridge.groundY)
                            tappedBridge.state = PetState.INTERACTING
                            tapped.onInteract()
                            interacted = true
                            assertEquals(PetState.IDLE, tappedBridge.state)
                        }
                        untouched.updateIdle(dt)
                        tapped.updateIdle(dt)
                        assertEquals("x diverged at $rate fps tick $tick", untouchedBridge.windowX, tappedBridge.windowX)
                        assertEquals("y diverged at $rate fps tick $tick", untouchedBridge.windowY, tappedBridge.windowY)
                        landed = untouchedBridge.windowY == untouchedBridge.groundY && tappedBridge.windowY == tappedBridge.groundY
                        tick++
                    }
                    assertTrue("Jump finished before the test delivered affection", interacted)
                    assertTrue("Piru did not land at $rate fps", landed)
                }
            } finally {
                instrumentation.runOnMainSync {
                    untouched.destroy()
                    tapped.destroy()
                }
            }
        }
    }
}
