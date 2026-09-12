package com.pixelpals.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.core.motion.TelaRestPose
import com.pixelpals.app.feature.home.CompanionMotion
import com.pixelpals.app.feature.home.HomeLocomotion
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.PetClipSpec
import com.pixelpals.app.feature.overlay.behavior.TelaBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TelaSleepContinuityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun homeAndDesktopFoldOnceThenHoldTheSameRestingPose(): Unit = withBehavior { bridge, behavior ->
        val home: HomeLocomotion = runBlocking { HomeLocomotion.load(context, PetType.TELA) }
        @Suppress("UNCHECKED_CAST")
        val clips = field(home, "clips").get(home) as Map<String, PetClipSpec>
        assertEquals(TelaRestPose.restFrames, clips.getValue("sleep").frames)
        assertEquals(TelaRestPose.wakeFrames, clips.getValue("wake").frames)
        assertFalse(clips.getValue("sleep").loop)
        instrumentation.runOnMainSync {
            enterSleep(behavior)
            val seen: MutableSet<Int> = linkedSetOf()
            repeat(180) { tick ->
                behavior.updateIdle(1f / 60f)
                seen += bridge.currentFrame
                if (tick >= 72) assertEquals(42, bridge.currentFrame)
                assertEquals(0f, bridge.animOffsetY, 0f)
            }
            assertEquals(TelaRestPose.restFrames.toSet(), seen)
            exportReview(bridge, behavior)
        }
    }

    @Test fun earlyTouchUnfoldsFromItsActualPoseAndRepeatedTouchDoesNotRestart(): Unit = withBehavior { bridge, behavior ->
        instrumentation.runOnMainSync {
            enterSleep(behavior)
            repeat(21) { behavior.updateIdle(1f / 60f) }
            assertEquals(40, bridge.currentFrame)
            val x: Int = bridge.getWindowParams().x
            val y: Int = bridge.getWindowParams().y
            behavior.onInteract()
            assertEquals(40, bridge.currentFrame)
            repeat(8) { behavior.updateInteracting(1f / 60f) }
            val elapsed: Float = field(behavior, "modeTimer").getFloat(behavior)
            behavior.onInteract()
            assertEquals(elapsed, field(behavior, "modeTimer").getFloat(behavior), 0f)
            val seen: MutableSet<Int> = linkedSetOf(bridge.currentFrame)
            repeat(80) {
                if (mode(behavior) == "WAKE") {
                    behavior.updateInteracting(1f / 60f)
                    if (mode(behavior) == "WAKE") seen += bridge.currentFrame
                    assertEquals(x, bridge.getWindowParams().x)
                    assertEquals(y, bridge.getWindowParams().y)
                }
            }
            assertEquals(setOf(40, 39), seen)
            assertEquals("TOUCH", mode(behavior))
        }
    }

    @Test fun autonomousWakeCompletesAndTapDuringItStillReceivesAReaction(): Unit = withBehavior { bridge, behavior ->
        instrumentation.runOnMainSync {
            enterSleep(behavior)
            field(behavior, "modeDuration").setFloat(behavior, 1.3f)
            repeat(81) { behavior.updateIdle(1f / 60f) }
            assertEquals("WAKE", mode(behavior))
            val x: Int = bridge.getWindowParams().x
            val y: Int = bridge.getWindowParams().y
            behavior.onInteract()
            val seen: MutableSet<Int> = linkedSetOf(bridge.currentFrame)
            repeat(80) {
                if (mode(behavior) == "WAKE") {
                    behavior.updateInteracting(1f / 60f)
                    if (mode(behavior) == "WAKE") seen += bridge.currentFrame
                    assertEquals(x, bridge.getWindowParams().x)
                    assertEquals(y, bridge.getWindowParams().y)
                }
            }
            assertEquals(TelaRestPose.wakeFrames.toSet(), seen)
            assertEquals("TOUCH", mode(behavior))
        }
    }

    @Test fun interruptedHomeRestAndDesktopHandoffKeepTheVisibleFrame(): Unit = withBehavior { bridge, behavior ->
        val home: HomeLocomotion = runBlocking { HomeLocomotion.load(context, PetType.TELA) }
        instrumentation.runOnMainSync {
            for (ticks: Int in listOf(6, 21, 35, 54, 90)) {
                val motion = CompanionMotion()
                repeat(ticks) { motion.advanceScheduledRest(true, 1f / 60f) }
                val frame: Int = requireNotNull(home.restFrameForHandoff(motion, false))
                val before: Bitmap = renderHome(home, motion)
                motion.advanceScheduledRest(false, 0f)
                val after: Bitmap = renderHome(home, motion)
                assertTrue("Schedule cancellation must not jump to the fully curled pose", before.sameAs(after))
                assertEquals(frame, home.restFrameForHandoff(motion, false))
                behavior.onScheduledSleepInterrupted(frame)
                assertEquals(frame, bridge.currentFrame)
                behavior.onInteract()
                assertEquals("Direct input must retain the displayed scheduled pose", frame, bridge.currentFrame)
                before.recycle(); after.recycle()
            }
        }
    }

    private fun renderHome(home: HomeLocomotion, motion: CompanionMotion): Bitmap {
        val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        home.draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            RectF(20f, 20f, 220f, 220f), motion, false)
        return bitmap
    }

    private fun exportReview(bridge: TestPetBridge, behavior: TelaBehavior) {
        val sheet = Bitmap.createBitmap(320 * 4, 320, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(0xfffaf7ef.toInt())
        val canvas = Canvas(sheet)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff904454.toInt(); textSize = 16f }
        TelaRestPose.restFrames.forEachIndexed { column, frame ->
            bridge.currentFrame = frame
            canvas.save()
            canvas.translate(column * 320f, 0f)
            behavior.onDraw(canvas, 160f, 180f)
            val ground: Float = 180f + requireNotNull(behavior.careBaselineOffsetY)
            canvas.drawLine(0f, ground, 320f, ground, paint)
            canvas.drawText("Tela $frame", 8f, 24f, paint)
            canvas.restore()
        }
        File(context.cacheDir, "tela-rest-review.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        sheet.recycle()
    }

    private fun enterSleep(behavior: TelaBehavior) {
        val mode = field(behavior, "mode")
        mode.set(behavior, mode.type.enumConstants!!.first { it.toString() == "SLEEP" })
        field(behavior, "modeTimer").setFloat(behavior, 0f)
        field(behavior, "modeDuration").setFloat(behavior, 60f)
        field(behavior, "activeClipId").set(behavior, null)
    }

    private fun mode(behavior: TelaBehavior): String = field(behavior, "mode").get(behavior).toString()
    private fun field(instance: Any, name: String) = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun withBehavior(block: (TestPetBridge, TelaBehavior) -> Unit) {
        val bridge = TestPetBridge(context, PetType.TELA, 320, PetArtworkScale.forPet(PetType.TELA))
        val behavior = TelaBehavior(bridge, SeededPetRandom(17))
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        try {
            val deadline: Long = SystemClock.elapsedRealtime() + 10_000L
            while (loading.getBoolean(behavior) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(30)
            assertFalse("Tela atlas failed to load", loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                bridge.getWindowParams().x = 400
                bridge.getWindowParams().y = bridge.bounds.floor
            }
            block(bridge, behavior)
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
