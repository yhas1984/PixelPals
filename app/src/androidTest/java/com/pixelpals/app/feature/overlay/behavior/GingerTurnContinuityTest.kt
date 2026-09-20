package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercise actual movement requests, not only the turn timeline in isolation. */
@RunWith(AndroidJUnit4::class)
class GingerTurnContinuityTest {
    @Test fun walkAndStalkStandThenTurnInBothDirectionsWithoutMovingTheirSupport() = withPet { bridge, behavior ->
        val sheet = Bitmap.createBitmap(160 * 5, 320, Bitmap.Config.ARGB_8888)
        try {
            for (fps in listOf(30, 120)) for (action in listOf("startWalk", "startStalk"))
                for ((row, oldFacing) in listOf(-1f, 1f).withIndex()) {
                    prepare(bridge, behavior, oldFacing)
                    invoke(behavior, action)
                    assertEquals("STAND_UP", mode(behavior))
                    val x = bridge.windowX
                    var ticks = 0
                    while (mode(behavior) == "STAND_UP" && ticks++ < fps) {
                        behavior.updateIdle(1f / fps)
                        assertPlanted(bridge, x)
                        assertEquals(-oldFacing, bridge.animScaleX, 0f)
                    }
                    assertEquals("TURN", mode(behavior))
                    val poses = mutableListOf<Pair<Int, Float>>()
                    ticks = 0
                    while (mode(behavior) == "TURN" && ticks++ < fps) {
                        val pose = bridge.currentFrame to bridge.animScaleX
                        if (poses.lastOrNull() != pose) {
                            poses += pose
                            if (fps == 30 && action == "startWalk") {
                                val canvas = Canvas(sheet)
                                canvas.save()
                                canvas.translate((poses.lastIndex * 160).toFloat(), (row * 160).toFloat())
                                behavior.onDraw(canvas, 80f, 150f)
                                canvas.restore()
                            }
                        }
                        behavior.updateIdle(1f / fps)
                        assertPlanted(bridge, x)
                    }
                    assertEquals(listOf(18, 22, 23, 22, 18), poses.map { it.first })
                    assertEquals(listOf(-oldFacing, -oldFacing, -oldFacing, oldFacing, oldFacing), poses.map { it.second })
                    assertEquals(if (action == "startWalk") "WALK" else "STALK", mode(behavior))
                    repeat(fps / 3) { behavior.updateIdle(1f / fps) }
                    assertTrue("Movement follows the new heading", (bridge.windowX - x) * -oldFacing > 0)
                }
            val output = File(instrumentation.targetContext.filesDir, "ginger-turn-review").apply { mkdirs() }
            File(output, "desktop.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { sheet.recycle() }
    }

    @Test fun sameHeadingStillStandsButDoesNotTurnAndReducedMotionCommitsDirectly() = withPet { bridge, behavior ->
        for (reduced in listOf(false, true)) for (action in listOf("startWalk", "startStalk")) {
            prepare(bridge, behavior, 1f, x = 100)
            behavior.advanceScheduledRestTransition(0f, reduced)
            invoke(behavior, action)
            assertEquals("STAND_UP", mode(behavior))
            repeat(80) { if (mode(behavior) == "STAND_UP") behavior.updateIdle(1f / 120f) }
            assertEquals(if (action == "startWalk") "WALK" else "STALK", mode(behavior))
            assertEquals(-1f, bridge.animScaleX, 0f)
        }
        prepare(bridge, behavior, -1f, x = 100)
        behavior.advanceScheduledRestTransition(0f, true)
        invoke(behavior, "startWalk")
        behavior.advanceScheduledRestTransition(0f, true)
        assertEquals("WALK", mode(behavior))
        assertEquals(18, bridge.currentFrame)
        assertEquals(-1f, bridge.animScaleX, 0f)
    }

    @Test fun interruptingBeforeOrAfterCommitKeepsHeadingAndDropsPendingMovement() = withPet { bridge, behavior ->
        for (seconds in listOf(.15f, .35f)) for (input in listOf("drag", "fling", "reset", "touch")) {
            prepare(bridge, behavior, -1f, x = 100)
            invoke(behavior, "startWalk")
            repeat(80) { if (mode(behavior) == "STAND_UP") behavior.updateIdle(1f / 120f) }
            assertEquals("TURN", mode(behavior))
            behavior.updateIdle(seconds)
            val expectedFacing = if (seconds < .30f) -1f else 1f
            val x = bridge.windowX
            when (input) {
                "drag" -> behavior.updateDrag(0f)
                "fling" -> behavior.onFling(0f, -200f)
                "reset" -> behavior.reset()
                else -> behavior.onInteract()
            }
            assertEquals(x, bridge.windowX)
            if (input != "touch") assertEquals(-expectedFacing, bridge.animScaleX, 0f)
            assertNull(field("turnCompletion").get(behavior))
            assertNull(field("postureCompletion").get(behavior))
            repeat(24) {
                behavior.updateIdle(1f / 60f)
                assertNotEquals("A cancelled turn must not resume movement", "WALK", mode(behavior))
            }
        }
    }

    private fun prepare(bridge: TestPetBridge, behavior: GingerBehavior, facing: Float, x: Int = if (facing < 0) 100 else 700) {
        bridge.getWindowParams().apply { this.x = x; y = bridge.groundY }
        behavior.reset()
        behavior.advanceScheduledRestTransition(0f, false)
        field("facingDirection").setFloat(behavior, facing)
        bridge.animScaleX = -facing
    }

    private fun assertPlanted(bridge: TestPetBridge, x: Int) {
        assertEquals(x, bridge.windowX)
        assertEquals(bridge.groundY, bridge.windowY)
        assertEquals(1f, bridge.animScaleY, 0f)
        assertEquals(0f, bridge.animOffsetX, 0f)
        assertEquals(0f, bridge.animOffsetY, 0f)
        assertEquals(0f, bridge.animRotation, 0f)
    }
    private fun withPet(test: (TestPetBridge, GingerBehavior) -> Unit) {
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER, 160, .875f)
            behavior = GingerBehavior(bridge, object : PetRandom {
                override fun nextFloat() = .25f
                override fun nextInt(from: Int, until: Int) = from
            })
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            var ready = false
            repeat(100) {
                if (!ready) { instrumentation.runOnMainSync { ready = !loading.getBoolean(behavior) }; if (!ready) Thread.sleep(30) }
            }
            assertTrue("Ginger atlas must load", ready)
            instrumentation.runOnMainSync {
                val frames = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }.get(behavior) as List<*>
                assertEquals("Use the actual turn supplement", 24, frames.size)
                test(bridge, behavior)
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
    private fun field(name: String) = GingerBehavior::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun mode(behavior: GingerBehavior) = requireNotNull(field("mode").get(behavior)).toString()
    private fun invoke(behavior: GingerBehavior, name: String) { GingerBehavior::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(behavior) }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
}
