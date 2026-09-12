package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.JellyElasticMotion
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

class JellyContinuityRenderingTest {
    @Test fun flingAndMidairTouchKeepTheExactVisibleHandoff() = withJelly { bridge, pet ->
        place(bridge, 400, 800)
        bridge.animScaleX = 1.1f; bridge.animScaleY = .9f
        bridge.animOffsetX = 2f; bridge.animOffsetY = 3f; bridge.animRotation = 8f
        val before = pixels(pet)
        pet.onFling(600f, -1200f)
        assertArrayEquals("Fling replaced the release pose", before, pixels(pet))
        pet.updateJumping(0f)
        assertArrayEquals("Zero-time sample changed the pose", before, pixels(pet))
        repeat(14) { pet.updateJumping(1f / 60f) }
        val airborne = pixels(pet)
        val x = bridge.windowX; val y = bridge.windowY
        pet.onInteract()
        assertArrayEquals(airborne, pixels(pet))
        pet.updateInteracting(0f)
        assertEquals(x, bridge.windowX); assertEquals(y, bridge.windowY)
        assertArrayEquals("Tap restarted the shape timeline", airborne, pixels(pet))
    }

    @Test fun touchingAnAscendingJellyPreservesMomentumUntilContact() = withJelly { bridge, pet ->
        for (fps in listOf(30, 60, 120)) {
            place(bridge, 400, 800)
            pet.onFling(500f, -1200f)
            repeat(fps / 10) { pet.updateJumping(1f / fps) }
            val beforeY = bridge.windowY
            pet.onInteract()
            pet.updateInteracting(1f / fps)
            assertTrue("Touch cancelled upward momentum", bridge.windowY < beforeY)
            var lastY = bridge.windowY
            var touchedFloor = false
            repeat(fps * 3) {
                if (bridge.state == PetState.INTERACTING) pet.updateInteracting(1f / fps)
                assertTrue("Contact escaped the floor", bridge.windowY <= bridge.groundY)
                assertTrue("Position jumped after touch", abs(bridge.windowY - lastY) < 120)
                if (bridge.windowY == bridge.groundY) touchedFloor = true
                lastY = bridge.windowY
            }
            assertTrue(touchedFloor)
            assertEquals(PetState.IDLE, bridge.state)
            assertEquals(1f, bridge.animScaleX, .001f)
            assertEquals(1f, bridge.animScaleY, .001f)
        }
    }

    @Test fun recoveryKeepsRenderedSupportAndAreaAtEveryFrame() = withJelly { bridge, pet ->
        place(bridge, 400, bridge.groundY)
        val neutral = pixels(pet)
        val support = bottom(neutral)
        val neutralArea = neutral.count { it ushr 24 >= 32 }
        val sheet = Bitmap.createBitmap(256 * 8, 256, Bitmap.Config.ARGB_8888)
        try {
            pet.onInteract()
            var sample = 0
            for (i in 0..120) {
                if (i > 0) pet.updateInteracting(JellyElasticMotion.TOUCH_SECONDS / 120f)
                val rendered = pixels(pet)
                assertEquals("The painted support moved", support.toFloat(), bottom(rendered).toFloat(), 2f)
                val areaRatio = rendered.count { it ushr 24 >= 32 }.toFloat() / neutralArea
                assertTrue("Body area changed with its camera: $areaRatio", areaRatio in .96f..1.04f)
                if (i in listOf(0, 15, 30, 45, 60, 80, 100, 120)) {
                    val bitmap = Bitmap.createBitmap(rendered, 256, 256, Bitmap.Config.ARGB_8888)
                    Canvas(sheet).drawBitmap(bitmap, (sample++ * 256).toFloat(), 0f, null)
                    bitmap.recycle()
                }
            }
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "jelly-elastic-recovery.png")
                .outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { sheet.recycle() }
    }

    @Test fun cancelledDragFallsFromItsPositionAndReducedMotionCanFinish() = withJelly { bridge, pet ->
        place(bridge, 400, 800)
        pet.onFling(500f, -1200f)
        repeat(6) { pet.updateJumping(1f / 60f) }
        val beforeX = bridge.windowX; val beforeY = bridge.windowY
        pet.reset()
        assertEquals(beforeX, bridge.windowX); assertEquals(beforeY, bridge.windowY)
        pet.updateJumping(1f / 60f)
        assertTrue("Cancel discarded horizontal momentum", bridge.windowX > beforeX)
        assertTrue("Cancel discarded upward momentum", bridge.windowY < beforeY)
        place(bridge, 250, 500)
        pet.reset()
        assertEquals(500, bridge.windowY)
        assertEquals(PetState.JUMPING, bridge.state)
        assertFalse(pet.canStartScheduledSleep(true))
        repeat(180) {
            pet.advanceScheduledRestTransition(1f / 60f, true)
            if (bridge.state == PetState.JUMPING) pet.updateJumping(1f / 60f)
        }
        assertEquals(bridge.groundY, bridge.windowY)
        assertTrue(pet.canStartScheduledSleep(true))
        assertEquals(1f, bridge.animScaleY, 0f)
        assertEquals(1f, bridge.animScaleX, 0f)
    }

    private fun withJelly(test: (TestPetBridge, JellyBehavior) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var pet: JellyBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.JELLY, 120, 1.302f)
            pet = JellyBehavior(bridge, object : PetRandom {
                override fun nextFloat(): Float = .5f
                override fun nextInt(from: Int, until: Int): Int = from
            })
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            for (i in 0..40) {
                instrumentation.waitForIdleSync()
                if (!loading.getBoolean(pet)) break
                Thread.sleep(50)
            }
            assertFalse(loading.getBoolean(pet))
            instrumentation.runOnMainSync { test(bridge, pet) }
        } finally { instrumentation.runOnMainSync { pet.destroy() } }
    }

    private fun place(bridge: TestPetBridge, x: Int, y: Int) {
        bridge.getWindowParams().apply { this.x = x; this.y = y; bridge.updateWindowLayout(this) }
    }

    private fun pixels(pet: JellyBehavior): IntArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        return try {
            pet.onDraw(Canvas(bitmap), 128f, 128f)
            IntArray(256 * 256).also { bitmap.getPixels(it, 0, 256, 0, 0, 256, 256) }
        } finally { bitmap.recycle() }
    }

    private fun bottom(pixels: IntArray): Int = pixels.indices.last { pixels[it] ushr 24 >= 32 } / 256
}
