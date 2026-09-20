package com.pixelpals.app.feature.overlay.behavior

import android.app.Instrumentation
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises Ginger's grounded pounce path and the reset used when desktop care ends. */
@RunWith(AndroidJUnit4::class)
class GingerDesktopMotionTest {
    @Test fun pounceAirborneLandingStaysInBoundsAndReturnsToGroundedMotion(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER)
            bridge.getWindowParams()?.y = bridge.groundY
            behavior = GingerBehavior(bridge, deterministicRandom())
            setMode(behavior, "POUNCE_COIL")
            setFloat(behavior, "modeTimer", 0f)
            setFloat(behavior, "modeDuration", .22f)
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                val observed = linkedSetOf<String>()
                var grounded = false
                var sawPounceTransform = false
                repeat(60 * 8) {
                    if (grounded) return@repeat
                    behavior.updateIdle(1f / 60f)
                    val mode = modeName(behavior)
                    observed += mode
                    assertWindowInBounds(bridge)
                    assertNeutralScale(bridge, mode)
                    assertTrue("Ginger facing flipped during one pounce", bridge.animScaleX > 0f)
                    if (mode == "POUNCE_COIL" || mode == "AIRBORNE" || mode == "LAND") {
                        sawPounceTransform = sawPounceTransform || kotlin.math.abs(bridge.animOffsetY) > .001f || kotlin.math.abs(bridge.animRotation) > .001f
                    }
                    if (mode == "SIT" && observed.contains("LAND")) grounded = true
                }
                assertTrue("Pounce path never became airborne: $observed", observed.contains("AIRBORNE"))
                assertTrue("Pounce path never reached landing: $observed", observed.contains("LAND"))
                assertTrue("Pounce path did not return to grounded motion: $observed", observed.contains("SIT"))
                assertTrue("Pounce path lost its offset/rotation expression", sawPounceTransform)
                assertEquals("Landed Ginger must be on the ground", bridge.groundY, bridge.windowY)
                assertNeutralScale(bridge, "SIT")
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test fun firstLandingDrawUsesImpactPoseWithoutAirborneRotation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER)
            behavior = GingerBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                for (fps in listOf(30, 60, 120)) {
                    setMode(behavior, "AIRBORNE")
                    setFloat(behavior, "airX", 500f)
                    setFloat(behavior, "airY", bridge.groundY - .1f)
                    setFloat(behavior, "airVelocityX", 0f)
                    setFloat(behavior, "airVelocityY", 100f)
                    bridge.currentFrame = 12
                    bridge.animRotation = 8f
                    behavior.updateIdle(1f / fps)
                    assertEquals("LAND", modeName(behavior))
                    assertEquals(bridge.groundY, bridge.windowY)
                    assertEquals("Contact replaces the flying pose immediately", 13, bridge.currentFrame)
                    assertEquals(0f, bridge.animRotation, 0f)
                    assertEquals(0f, bridge.animOffsetY, 0f)
                }
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }

    @Test fun resetAtGroundRestoresGroundedCareResumePose(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER)
            bridge.getWindowParams()?.y = bridge.groundY
            behavior = GingerBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                setMode(behavior, "LAND")
                setFloat(behavior, "modeTimer", .2f)
                setFloat(behavior, "facingDirection", 1f)
                bridge.animScaleX = -1f
                bridge.animScaleY = .94f
                bridge.animOffsetY = 2f
                bridge.animRotation = 3f
                behavior.reset()
                assertEquals("Care completion reset should return to SIT", "SIT", modeName(behavior))
                assertEquals(bridge.groundY, bridge.windowY)
                assertEquals("Care resume should preserve facing", -1f, bridge.animScaleX, .001f)
                assertEquals("Reset should restore vertical scale before idle animation", 1f, bridge.animScaleY, .001f)
                assertEquals("Reset should clear vertical offset", 0f, bridge.animOffsetY, .001f)
                assertEquals("Reset should clear rotation", 0f, bridge.animRotation, .001f)
                behavior.updateIdle(1f / 60f)
                assertEquals("Care completion should remain grounded", "SIT", modeName(behavior))
                assertEquals(bridge.groundY, bridge.windowY)
                assertEquals("Care resume should preserve facing", -1f, bridge.animScaleX, .001f)
                assertNeutralScale(bridge, "SIT")
                assertTrue("Idle breathing offset should remain bounded", kotlin.math.abs(bridge.animOffsetY) <= 2f)
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test fun groundedAnimationModesKeepStableBodyScale(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER)
            bridge.getWindowParams()?.y = bridge.groundY
            behavior = GingerBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                for (mode in listOf("SIT", "SLEEP", "WAKE", "POUNCE_COIL", "LAND")) {
                    setMode(behavior, mode)
                    setFloat(behavior, "modeTimer", 0f)
                    setFloat(behavior, "modeDuration", 10f)
                    repeat(30) {
                        behavior.updateIdle(1f / 60f)
                        assertNeutralScale(bridge, mode)
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun assertWindowInBounds(bridge: TestPetBridge) {
        assertTrue("Ginger x escaped bounds: ${bridge.windowX}", bridge.windowX in bridge.bounds.left..bridge.bounds.right)
        assertTrue("Ginger y escaped bounds: ${bridge.windowY}", bridge.windowY in bridge.bounds.top..bridge.bounds.floor)
    }

    @Test fun midairAffectionPreservesTheUntouchedTrajectory(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (fps: Int in listOf(30, 60, 120)) {
            val tappedBridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER)
            val controlBridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER)
            val tapped = GingerBehavior(tappedBridge, deterministicRandom())
            val control = GingerBehavior(controlBridge, deterministicRandom())
            try {
                waitForAssets(tapped, instrumentation)
                waitForAssets(control, instrumentation)
                instrumentation.runOnMainSync {
                    for ((bridge, behavior) in listOf(tappedBridge to tapped, controlBridge to control)) {
                        bridge.getWindowParams().apply { x = 500; y = bridge.groundY - 250 }
                        behavior.onFling(300f, -400f)
                    }
                    val delta: Float = 1f / fps
                    repeat(fps / 10) { tapped.updateIdle(delta); control.updateIdle(delta) }
                    val x: Int = tappedBridge.getWindowParams().x
                    val y: Int = tappedBridge.getWindowParams().y
                    tapped.onInteract()
                    assertEquals("Touch teleported X", x, tappedBridge.getWindowParams().x)
                    assertEquals("Touch teleported Y", y, tappedBridge.getWindowParams().y)
                    assertEquals("Touch froze the fall", com.pixelpals.app.core.domain.PetState.IDLE, tappedBridge.state)
                    assertEquals("AIRBORNE", modeName(tapped))
                    var landed: Boolean = false
                    repeat(fps * 5) {
                        if (!landed) {
                            tapped.updateIdle(delta)
                            control.updateIdle(delta)
                            assertEquals("$fps fps X trajectory changed", controlBridge.getWindowParams().x, tappedBridge.getWindowParams().x)
                            assertEquals("$fps fps Y trajectory changed", controlBridge.getWindowParams().y, tappedBridge.getWindowParams().y)
                            assertEquals(controlBridge.animScaleX, tappedBridge.animScaleX, .001f)
                            assertNeutralScale(tappedBridge, modeName(tapped))
                            landed = modeName(tapped) == "LAND"
                        }
                    }
                    assertTrue("Tapped Ginger never landed", landed)
                }
            } finally { instrumentation.runOnMainSync { tapped.destroy(); control.destroy() } }
        }
    }

    private fun assertNeutralScale(bridge: TestPetBridge, mode: String) {
        assertEquals("Ginger $mode scaleX changed body size", 1f, kotlin.math.abs(bridge.animScaleX), .001f)
        assertEquals("Ginger $mode scaleY changed body size", 1f, bridge.animScaleY, .001f)
    }

    private fun waitForAssets(behavior: GingerBehavior, instrumentation: Instrumentation) {
        val loading = GingerBehavior::class.java.superclass?.getDeclaredField("isLoading")?.apply { isAccessible = true }
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (loading?.getBoolean(behavior) == false) return
            Thread.sleep(50)
        }
        assertFalse("Ginger atlas did not load", loading?.getBoolean(behavior) ?: true)
    }

    private fun modeName(behavior: GingerBehavior): String =
        behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }.get(behavior).toString()

    private fun setMode(behavior: GingerBehavior, name: String) {
        val field = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }
        val value = field.type.enumConstants!!.first { it.toString() == name }
        field.set(behavior, value)
    }

    private fun setFloat(behavior: GingerBehavior, name: String, value: Float) {
        behavior.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
    }

    private fun deterministicRandom(): PetRandom = object : PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }
}
