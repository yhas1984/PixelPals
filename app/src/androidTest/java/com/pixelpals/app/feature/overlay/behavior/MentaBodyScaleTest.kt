package com.pixelpals.app.feature.overlay.behavior

import android.app.Instrumentation
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Ensures Menta's expressive modes keep the same body size through locomotion. */
@RunWith(AndroidJUnit4::class)
class MentaBodyScaleTest {
    @Test fun coilHappyAndTouchKeepNeutralScaleForOneSecond(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: MentaBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.MENTA)
            bridge.getWindowParams()?.y = bridge.groundY
            behavior = MentaBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                for (mode in listOf("COIL", "HAPPY")) {
                    setMode(behavior, mode)
                    setFloat(behavior, "modeTimer", 0f)
                    setFloat(behavior, "modeDuration", 2f)
                    repeat(60) {
                        behavior.updateIdle(1f / 60f)
                        assertNeutralScale(bridge, mode)
                    }
                }

                setMode(behavior, "TOUCH")
                setFloat(behavior, "modeTimer", 0f)
                setFloat(behavior, "modeDuration", 2f)
                repeat(60) {
                    behavior.updateInteracting(1f / 60f)
                    assertNeutralScale(bridge, "TOUCH")
                }

                setFloat(behavior, "modeTimer", 2f)
                behavior.updateInteracting(1f / 60f)
                val locomotionMode = modeName(behavior)
                assertFalse("Touch should hand control back to locomotion", locomotionMode == "TOUCH")
                repeat(60) {
                    behavior.updateIdle(1f / 60f)
                    assertNeutralScale(bridge, locomotionMode)
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun assertNeutralScale(bridge: TestPetBridge, mode: String): Unit {
        assertEquals("Menta $mode scaleX changed body size", 1f, kotlin.math.abs(bridge.animScaleX), .001f)
        assertEquals("Menta $mode scaleY changed body size", 1f, bridge.animScaleY, .001f)
    }

    private fun waitForAssets(behavior: MentaBehavior, instrumentation: Instrumentation): Unit {
        val loading = MentaBehavior::class.java.superclass?.getDeclaredField("isLoading")?.apply { isAccessible = true }
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (loading?.getBoolean(behavior) == false) return
            Thread.sleep(50)
        }
        assertFalse("Menta atlas did not load", loading?.getBoolean(behavior) ?: true)
    }

    private fun modeName(behavior: MentaBehavior): String =
        behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }.get(behavior).toString()

    private fun setMode(behavior: MentaBehavior, name: String): Unit {
        val field = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }
        field.set(behavior, field.type.enumConstants!!.first { it.toString() == name })
    }

    private fun setFloat(behavior: MentaBehavior, name: String, value: Float): Unit {
        behavior.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
    }

    private fun deterministicRandom(): PetRandom = object : PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }
}
