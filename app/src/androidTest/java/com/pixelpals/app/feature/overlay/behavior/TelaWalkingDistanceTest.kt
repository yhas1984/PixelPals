package com.pixelpals.app.feature.overlay.behavior

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelaWalkingDistanceTest {
    @Test
    fun walkingFrameAndPositionAreStableAcrossFrameRatesAndDirections() {
        val forward = listOf(30, 60, 120).map { runHalfWalk(it, 300f, 500f) }
        val reverse = listOf(30, 60, 120).map { runHalfWalk(it, 500f, 300f) }

        assertTrue("forward x drifted across frame rates", forward.maxOf { it.x } - forward.minOf { it.x } <= 2)
        assertTrue("reverse x drifted across frame rates", reverse.maxOf { it.x } - reverse.minOf { it.x } <= 2)
        assertEquals("forward walk must use the canonical facing sign", -1f, forward.first().scaleX, 0f)
        assertEquals("reverse walk must use the canonical facing sign", 1f, reverse.first().scaleX, 0f)
        (forward + reverse).forEach { assertEquals("halfway position", 400f, it.x.toFloat(), 1f) }
        assertEquals("forward frame changed with frame rate", forward.first().frame, forward[1].frame)
        assertEquals("forward frame changed with frame rate", forward.first().frame, forward[2].frame)
        assertEquals("reverse frame changed with frame rate", reverse.first().frame, reverse[1].frame)
        assertEquals("reverse frame changed with frame rate", reverse.first().frame, reverse[2].frame)
    }

    @Test
    fun zeroDistanceWalkDoesNotCycleByElapsedTimeBeforeItsMinimumDuration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bridge = TestPetBridge(instrumentation.targetContext, PetType.TELA, 160, PetArtworkScale.forPet(PetType.TELA))
        val behavior = TelaBehavior(bridge, SeededPetRandom(3))
        try {
            awaitLoaded(behavior)
            startWalk(behavior, 2f, 300f, 300f)
            val observed = linkedSetOf<Int>()
            repeat(48) {
                behavior.updateIdle(1f / 60f)
                observed += bridge.currentFrame
            }
            assertEquals("zero-distance walk must hold its entry frame", 1, observed.size)
        } finally {
            behavior.destroy()
        }
    }

    private fun runHalfWalk(fps: Int, fromX: Float, toX: Float): Sample {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bridge = TestPetBridge(instrumentation.targetContext, PetType.TELA, 160, PetArtworkScale.forPet(PetType.TELA))
        val behavior = TelaBehavior(bridge, SeededPetRandom(9))
        try {
            awaitLoaded(behavior)
            startWalk(behavior, 2f, fromX, toX)
            val duration = TelaBehavior::class.java.getDeclaredField("modeDuration").apply { isAccessible = true }.getFloat(behavior)
            val steps = kotlin.math.ceil(duration * .5f * fps).toInt()
            repeat(steps) { behavior.updateIdle(duration * .5f / steps) }
            return Sample(bridge.getWindowParams().x, bridge.currentFrame, bridge.animScaleX)
        } finally {
            behavior.destroy()
        }
    }

    private fun awaitLoaded(behavior: TelaBehavior) {
        val field = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (field.getBoolean(behavior) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50L)
        assertTrue("Tela atlas did not load", !field.getBoolean(behavior))
    }

    @Test
    fun exportWalkingSequencesWithMovingGroundReference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (forward in listOf(true, false)) {
            val bridge = TestPetBridge(context, PetType.TELA, 320, PetArtworkScale.forPet(PetType.TELA))
            val behavior = TelaBehavior(bridge, SeededPetRandom(9))
            val bitmap = android.graphics.Bitmap.createBitmap(400, 260, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val folder = java.io.File(context.getExternalFilesDir(null), "tela-walk-sequence/${if (forward) "forward" else "reverse"}").apply { mkdirs() }
            try {
                awaitLoaded(behavior)
                bridge.getWindowParams().x = if (forward) 300 else 500
                bridge.animOffsetX = 3f
                bridge.animOffsetY = 2f
                bridge.animRotation = 3f
                startWalk(behavior, 4f, if (forward) 300f else 500f, if (forward) 500f else 300f)
                assertEquals(0f, bridge.animOffsetX, 0f)
                assertEquals(0f, bridge.animOffsetY, 0f)
                assertEquals(0f, bridge.animRotation, 0f)
                assertEquals(if (forward) -1f else 1f, bridge.animScaleX, 0f)
                repeat(120) { sample ->
                    instrumentation.runOnMainSync {
                        if (sample > 0) repeat(2) { behavior.updateIdle(1f / 60f) }
                        bitmap.eraseColor(0xfffaf7ef.toInt())
                        paint.color = 0xffbac5b2.toInt()
                        paint.strokeWidth = 2f
                        canvas.drawLine(0f, 230f, 400f, 230f, paint)
                        for (worldX in 0..1000 step 30) {
                            val x = worldX - bridge.getWindowParams().x + 200f
                            canvas.drawLine(x, 230f, x, 240f, paint)
                        }
                        behavior.onDraw(canvas, 200f, 230f)
                        paint.color = android.graphics.Color.DKGRAY
                        paint.textSize = 15f
                        canvas.drawText("${if (forward) "Right" else "Left"} / t=${sample}/30 / x=${bridge.getWindowParams().x} / frame=${bridge.currentFrame}", 8f, 20f, paint)
                    }
                    java.io.File(folder, "frame_%03d.png".format(java.util.Locale.ROOT, sample)).outputStream().use {
                        assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                }
            } finally {
                instrumentation.runOnMainSync { behavior.destroy() }
                bitmap.recycle()
            }
        }
    }

    @Test
    fun stationaryWallAndCeilingPosesDoNotKeepSteppingOrInheritSway() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (mode in listOf("CLIMB", "CEILING")) {
            val bridge = TestPetBridge(context, PetType.TELA, 160, PetArtworkScale.forPet(PetType.TELA))
            val behavior = TelaBehavior(bridge, SeededPetRandom(4))
            try {
                awaitLoaded(behavior)
                bridge.animOffsetX = 3f
                bridge.animOffsetY = 2f
                bridge.animRotation = 3f
                startWalk(behavior, 2f, 300f, 300f, mode, bridge.bounds.top.toFloat(), bridge.bounds.top.toFloat())
                assertEquals("entry horizontal sway", 0f, bridge.animOffsetX, 0f)
                assertEquals("entry vertical sway", 0f, bridge.animOffsetY, 0f)
                assertEquals("entry rotation", 0f, bridge.animRotation, 0f)
                val frames = linkedSetOf<Int>()
                repeat(48) { behavior.updateIdle(1f / 60f); frames += bridge.currentFrame }
                assertEquals("$mode steps without travel", 1, frames.size)
                assertEquals("$mode moves away from contact", 0f, bridge.animOffsetY, 0f)
            } finally { behavior.destroy() }
        }
    }

    @Test
    fun fullWallTravelRespectsSpeedAndBoundsAtAllFrameRates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (fps in listOf(30, 60, 120)) for (rightWall in listOf(false, true)) for (up in listOf(false, true)) {
            val bridge = TestPetBridge(context, PetType.TELA, 160, PetArtworkScale.forPet(PetType.TELA))
            val behavior = TelaBehavior(bridge, SeededPetRandom(4))
            try {
                awaitLoaded(behavior)
                val x = if (rightWall) bridge.bounds.right else bridge.bounds.left
                val start = if (up) bridge.bounds.floor else bridge.bounds.top
                val end = if (up) bridge.bounds.top else bridge.bounds.floor
                bridge.getWindowParams().x = x
                bridge.getWindowParams().y = start
                startWalk(behavior, 3.6f, x.toFloat(), x.toFloat(), "CLIMB", start.toFloat(), end.toFloat())
                val duration = TelaBehavior::class.java.getDeclaredField("modeDuration").apply { isAccessible = true }.getFloat(behavior)
                val steps = kotlin.math.ceil(duration * fps).toInt()
                val dt = duration / steps
                val maximumStep = bridge.petSpriteSize * bridge.spriteScale * 2.2f * dt + 1.01f
                var previous = start
                val frames = linkedSetOf<Int>()
                repeat(steps - 1) {
                    behavior.updateIdle(dt)
                    val position = bridge.getWindowParams()
                    assertEquals("left the wall", x, position.x)
                    assertTrue("outside screen bounds", position.y in bridge.bounds.top..bridge.bounds.floor)
                    assertTrue("speed exceeds body-relative limit", kotlin.math.abs(position.y - previous) <= maximumStep)
                    assertTrue("travel reversed", if (up) position.y <= previous else position.y >= previous)
                    frames += bridge.currentFrame
                    previous = position.y
                }
                assertTrue("failed to reach wall endpoint", kotlin.math.abs(previous - end) <= 2)
                assertEquals("did not exercise all climbing poses", setOf(12, 13, 14, 15), frames)
            } finally { behavior.destroy() }
        }
    }

    private fun startWalk(behavior: TelaBehavior, duration: Float, fromX: Float, toX: Float,
                          modeName: String = "WALK", fromY: Float = 600f, toY: Float = 600f) {
        val modeType = behavior.javaClass.getDeclaredField("mode").type
        val walk = modeType.enumConstants!!.first { it.toString() == modeName }
        behavior.javaClass.getDeclaredMethod(
            "startMode", modeType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(behavior, walk, duration, fromX, fromY, toX, toY)
    }

    private data class Sample(val x: Int, val frame: Int, val scaleX: Float)
}
