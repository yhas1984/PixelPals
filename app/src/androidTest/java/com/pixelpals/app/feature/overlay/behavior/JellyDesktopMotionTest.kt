package com.pixelpals.app.feature.overlay.behavior

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.graphics.Canvas
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.motion.JellyElasticMotion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class JellyDesktopMotionTest {
    @Test fun releasePreservesPositionAndTravelsContinuouslyToTheFloor(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: JellyBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.JELLY)
            behavior = JellyBehavior(bridge, object : PetRandom {
                override fun nextFloat(): Float = .5f
                override fun nextInt(from: Int, until: Int): Int = from
            })
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(40) {
                instrumentation.waitForIdleSync()
                if (loading.getBoolean(behavior)) Thread.sleep(50)
            }
            assertFalse("Jelly artwork did not load", loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                for (fps: Int in listOf(30, 60, 120)) {
                    for (startY: Int in listOf(bridge.bounds.top, 700, bridge.groundY)) {
                        val params = bridge.getWindowParams()
                        params.x = 400
                        params.y = startY
                        bridge.updateWindowLayout(params)
                        behavior.onFling(600f, -1200f)
                        assertEquals("Release teleported vertically", startY, bridge.windowY)
                        assertEquals("Release teleported horizontally", 400, bridge.windowX)
                        behavior.updateIdle(0f)
                        assertEquals(startY, bridge.windowY)
                        var previousY: Int = startY
                        for (step in 0 until (fps * 3)) {
                            behavior.updateIdle(1f / fps)
                            assertTrue("Jelly left the top edge", bridge.windowY >= bridge.bounds.top)
                            assertTrue("Jelly fell below the floor", bridge.windowY <= bridge.groundY)
                            assertTrue("Discontinuous vertical step", abs(bridge.windowY - previousY) <= 120)
                            previousY = bridge.windowY
                            if (bridge.windowY == bridge.groundY) break
                        }
                        assertEquals("Jelly never landed", bridge.groundY, bridge.windowY)
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test fun landingRecoversNeutralShapeBeforeIdle(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: JellyBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.JELLY)
            bridge.getWindowParams().y = bridge.groundY
            bridge.updateWindowLayout(bridge.getWindowParams())
            behavior = JellyBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                val atlasScale = getFloat(behavior, "spriteAtlasDrawScale")
                val contact = (746f / 768f - .5f) * bridge.petSpriteSize * bridge.spriteScale * atlasScale
                for (fps in listOf(30, 60, 120)) {
                    setMode(behavior, "LANDING")
                    setFloat(behavior, "modeTimer", 0f)
                    bridge.animScaleX = 1f; bridge.animScaleY = 1f; bridge.animOffsetY = 0f
                    var previousX = 1f
                    var previousY = 1f
                    var compressed = false
                    var reachedIdle = false
                    for (step in 0 until ((JellyElasticMotion.LAND_SECONDS * fps).toInt() + 2)) {
                        behavior.updateIdle(1f / fps)
                        assertEquals("Jelly left the landing ground", bridge.groundY, bridge.windowY)
                        assertEquals("Landing moved Jelly's contact point", contact,
                            contact * bridge.animScaleY + bridge.animOffsetY, .8f)
                        assertEquals("Landing preserves canvas area", 1f, abs(bridge.animScaleX * bridge.animScaleY), .025f)
                        compressed = compressed || bridge.animScaleY < .99f
                        assertTrue("Landing scaleX jumped", abs(bridge.animScaleX - previousX) <= .13f)
                        assertTrue("Landing scaleY jumped", abs(bridge.animScaleY - previousY) <= .13f)
                        previousX = bridge.animScaleX; previousY = bridge.animScaleY
                        if (modeName(behavior) == "IDLE") { reachedIdle = true; break }
                    }
                    assertTrue("Jelly landing never compressed", compressed)
                    assertTrue("Jelly landing never reached idle", reachedIdle)
                    assertEquals(1f, bridge.animScaleX, .001f)
                    assertEquals(1f, bridge.animScaleY, .001f)
                    assertEquals(0f, bridge.animOffsetY, .001f)
                    behavior.updateIdle(1f / fps)
                    assertTrue("Idle wobble ramp was abrupt", abs(bridge.animScaleX - 1f) <= .04f)
                    assertTrue("Idle wobble ramp was abrupt", abs(bridge.animScaleY - 1f) <= .04f)
                }
            }
            exportLandingSheet(behavior, bridge, instrumentation)
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }

    private fun exportLandingSheet(behavior: JellyBehavior, bridge: TestPetBridge, instrumentation: android.app.Instrumentation) {
        val sheet = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        try {
            instrumentation.runOnMainSync {
                for (index in 0 until 9) {
                    setMode(behavior, "LANDING"); setFloat(behavior, "modeTimer", index * JellyElasticMotion.LAND_SECONDS / 8f)
                    bridge.animScaleX = 1f; bridge.animScaleY = 1f; bridge.animOffsetY = 0f
                    behavior.updateIdle(0f)
                    val canvas = Canvas(sheet)
                    behavior.onDraw(canvas, 80f + (index % 3) * 160f, 80f + (index / 3) * 160f)
                }
            }
            val file = java.io.File(instrumentation.targetContext.cacheDir, "jelly-landing-review.png")
            file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { sheet.recycle() }
    }

    private fun waitForAssets(behavior: JellyBehavior, instrumentation: android.app.Instrumentation) {
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(40) {
            instrumentation.waitForIdleSync()
            if (!loading.getBoolean(behavior)) return
            Thread.sleep(50)
        }
        assertFalse("Jelly artwork did not load", loading.getBoolean(behavior))
    }

    private fun deterministicRandom(): PetRandom = object : PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }

    private fun modeName(behavior: JellyBehavior): String = (behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }.get(behavior) as Enum<*>).name

    private fun setMode(behavior: JellyBehavior, name: String) {
        val field = behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }
        field.set(behavior, field.type.enumConstants!!.first { it.toString() == name })
    }

    private fun setFloat(behavior: JellyBehavior, name: String, value: Float) {
        behavior.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
    }

    private fun getFloat(behavior: JellyBehavior, name: String): Float {
        var owner: Class<*>? = behavior.javaClass
        while (owner != null) {
            try {
                return owner.getDeclaredField(name).apply { isAccessible = true }.getFloat(behavior)
            } catch (_: NoSuchFieldException) {
                owner = owner.superclass
            }
        }
        error("Missing float field $name in Jelly behavior hierarchy")
    }
}
