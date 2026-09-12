package com.pixelpals.app.feature.overlay.behavior

import android.app.Instrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Verifies Ginger's distance based stalk motion at several render rates. */
@RunWith(AndroidJUnit4::class)
class GingerStalkMotionTest {
    @Test
    fun stalkUsesBoundedSmootherstepMotionInBothDirections(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (direction in listOf(1f, -1f)) {
            for (fps in listOf(30, 60, 120)) {
                lateinit var bridge: TestPetBridge
                lateinit var behavior: GingerBehavior
                instrumentation.runOnMainSync {
                    bridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER, 160, 1f)
                    bridge.getWindowParams()!!.apply { x = 500; bridge.updateWindowLayout(this) }
                    behavior = GingerBehavior(bridge, deterministicRandom())
                    setMode(behavior, "STALK")
                    setFloat(behavior, "moveStartX", 500f)
                    setFloat(behavior, "moveTargetX", 500f + direction * 64f)
                    setFloat(behavior, "modeDuration", 2f)
                    setFloat(behavior, "modeTimer", 0f)
                    setFloat(behavior, "facingDirection", direction)
                }
                try {
                    waitForAssets(behavior, instrumentation)
                    instrumentation.runOnMainSync {
                        val positions = ArrayList<Int>()
                        val frames = linkedSetOf<Int>()
                        var previous = bridge.windowX
                        var firstStep: Int? = null
                        var lastStep = 0
                        repeat(fps * 3) {
                            if (modeName(behavior) != "STALK") return@repeat
                            behavior.updateIdle(1f / fps)
                            val x = bridge.windowX
                            val step = x - previous
                            if (firstStep == null) firstStep = abs(step)
                            lastStep = abs(step)
                            assertTrue("stalk must move monotonically at ${fps}fps", step * direction >= 0)
                            assertTrue("stalk exceeded its 64px target", abs(x - 500) <= 64)
                            assertEquals("Ginger body scale changed", 1f, abs(bridge.animScaleX), .001f)
                            assertEquals("Ginger vertical scale changed", 1f, bridge.animScaleY, .001f)
                            positions += x
                            if (modeName(behavior) == "STALK") frames += bridge.currentFrame
                            previous = x
                        }
                        assertEquals("stalk must finish in pounce coil", "POUNCE_COIL", modeName(behavior))
                        assertEquals("stalk must enter coil on its ending draw", 11, bridge.currentFrame)
                        assertTrue("first stalk step should be <= 1px", (firstStep ?: 99) <= 1)
                        assertTrue("last stalk step should be <= 1px", lastStep <= 1)
                        assertTrue("stalk should reach the target", positions.last() == 500 + direction.toInt() * 64)
                        assertEquals("stalk should expose frames 8, 9 and 10", setOf(8, 9, 10), frames)
                    }
                } finally {
                    instrumentation.runOnMainSync { behavior.destroy() }
                }
            }
        }
    }

    @Test
    fun zeroDistanceStalkHoldsStartFrameUntilCoil(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER, 160, 1f)
            bridge.getWindowParams()!!.apply { x = 500; bridge.updateWindowLayout(this) }
            behavior = GingerBehavior(bridge, deterministicRandom())
            setMode(behavior, "STALK")
            setFloat(behavior, "moveStartX", 500f)
            setFloat(behavior, "moveTargetX", 500f)
            setFloat(behavior, "modeDuration", 2f)
            setFloat(behavior, "modeTimer", 0f)
            setFloat(behavior, "facingDirection", 1f)
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                repeat(60 * 3) {
                    if (modeName(behavior) != "STALK") return@repeat
                    behavior.updateIdle(1f / 60f)
                    if (modeName(behavior) == "STALK") {
                        assertEquals(500, bridge.windowX)
                        assertEquals("zero-distance stalk must hold frame 8", 8, bridge.currentFrame)
                    }
                }
                assertEquals("POUNCE_COIL", modeName(behavior))
                assertEquals(11, bridge.currentFrame)
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun waitForAssets(behavior: GingerBehavior, instrumentation: Instrumentation) {
        val loading = GingerBehavior::class.java.superclass!!.getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (!loading.getBoolean(behavior)) return
            Thread.sleep(50)
        }
        assertTrue("Ginger atlas did not load", !loading.getBoolean(behavior))
    }

    private fun modeName(behavior: GingerBehavior): String =
        behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }.get(behavior).toString()

    private fun setMode(behavior: GingerBehavior, name: String) {
        val field = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }
        field.set(behavior, field.type.enumConstants!!.first { it.toString() == name })
    }

    private fun setFloat(behavior: GingerBehavior, name: String, value: Float) {
        behavior.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
    }

    private fun deterministicRandom() = object : com.pixelpals.app.core.motion.PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }
}
