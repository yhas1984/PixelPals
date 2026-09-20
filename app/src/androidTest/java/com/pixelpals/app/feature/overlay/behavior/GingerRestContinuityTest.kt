package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.home.CompanionMotion
import com.pixelpals.app.feature.home.HomeLocomotion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GingerRestContinuityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun sleepFoldsOnceAndHoldsItsBodyAtTheSameSupport(): Unit = withPet { bridge, behavior ->
        instrumentation.runOnMainSync {
            behavior.onScheduledSleepInterrupted(0)
            val x: Int = bridge.getWindowParams().x
            val seen: MutableSet<Int> = linkedSetOf(bridge.currentFrame)
            repeat(180) { tick ->
                behavior.updateIdle(1f / 60f)
                seen += bridge.currentFrame
                if (tick > 60) assertEquals(21, bridge.currentFrame)
                assertEquals(x, bridge.getWindowParams().x)
                assertEquals(bridge.groundY, bridge.getWindowParams().y)
                assertEquals(1f, bridge.animScaleY, 0f)
                assertEquals(0f, bridge.animOffsetY, 0f)
            }
            assertEquals(setOf(0, 16, 19, 20, 21), seen)
            exportReview(bridge, behavior)
        }
    }

    @Test fun tappingAnyRestPoseUnfoldsBeforeRespondingAndDoesNotRestart(): Unit = withPet { bridge, behavior ->
        instrumentation.runOnMainSync {
            for (start: Int in listOf(0, 16, 19, 20, 21)) {
                bridge.state = PetState.IDLE
                behavior.onScheduledSleepInterrupted(start)
                val x: Int = bridge.getWindowParams().x
                behavior.onInteract()
                assertEquals(PetState.INTERACTING, bridge.state)
                assertEquals(start, bridge.currentFrame)
                behavior.updateInteracting(.05f)
                val before: Float = field(behavior, "modeTimer").getFloat(behavior)
                behavior.onInteract()
                assertEquals(before, field(behavior, "modeTimer").getFloat(behavior), 0f)
                val seen: MutableSet<Int> = linkedSetOf(bridge.currentFrame)
                repeat(160) {
                    if (mode(behavior) != "TOUCH") {
                        behavior.updateInteracting(1f / 60f)
                        if (mode(behavior) == "WAKE") seen += bridge.currentFrame
                        assertEquals(x, bridge.getWindowParams().x)
                        assertEquals(bridge.groundY, bridge.getWindowParams().y)
                    }
                }
                assertTrue("Waking must finish upright before the seated reaction", 18 in seen)
                if (start < 20) assertFalse("Early input must not jump to the sleeping curl", 21 in seen)
                assertEquals("TOUCH", mode(behavior))
                assertEquals(PetState.INTERACTING, bridge.state)
            }
        }
    }

    @Test fun autonomousWakeDoesNotRepeatTheSitToStandSequence(): Unit = withPet { bridge, behavior ->
        instrumentation.runOnMainSync {
            behavior.onScheduledSleepInterrupted(21)
            field(behavior, "modeDuration").setFloat(behavior, .60f)
            repeat(5) { behavior.updateIdle(1f / 60f) }
            assertEquals("WAKE", mode(behavior))
            val seen: MutableSet<Int> = linkedSetOf(bridge.currentFrame)
            val x: Int = bridge.getWindowParams().x
            repeat(80) {
                if (mode(behavior) == "WAKE") {
                    behavior.updateIdle(1f / 60f)
                    seen += bridge.currentFrame
                    assertEquals(x, bridge.getWindowParams().x)
                }
            }
            assertEquals(setOf(21, 20, 19, 18), seen)
            // A wake ending toward the opposite destination now turns while
            // standing. It must never replay the seated-to-standing poses.
            repeat(40) {
                if (mode(behavior) == "TURN") {
                    behavior.updateIdle(1f / 60f)
                    assertTrue(bridge.currentFrame in setOf(18, 22, 23))
                    assertEquals(x, bridge.getWindowParams().x)
                }
            }
            assertEquals("WALK", mode(behavior))
        }
    }

    @Test fun scheduleAndInputRetainTheExactReachedPose(): Unit = withPet { bridge, behavior ->
        val home: HomeLocomotion = runBlocking { HomeLocomotion.load(context, PetType.GINGER) }
        instrumentation.runOnMainSync {
            for (frame: Int in listOf(0, 16, 19, 20, 21)) {
                behavior.onScheduledSleepInterrupted(frame)
                val seated: Boolean = behavior.isSeatedForScheduledRest
                val motion = CompanionMotion()
                motion.advanceScheduledRest(true, 0f)
                motion.retainScheduledRestPose(requireNotNull(home.restElapsedForFrame(frame, seated)))
                assertEquals(frame, home.restFrameForHandoff(motion, false, seated))
                val before: Bitmap = renderHome(home, motion, seated)
                motion.advanceScheduledRest(false, 0f)
                assertEquals(frame, home.restFrameForHandoff(motion, false, seated))
                val after: Bitmap = renderHome(home, motion, seated)
                assertTrue("Ending a schedule mid-entry must preserve the visible pose", before.sameAs(after))
                assertEquals(frame, bridge.currentFrame)
                before.recycle(); after.recycle()
            }
        }
    }

    private fun renderHome(home: HomeLocomotion, motion: CompanionMotion, seated: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        home.draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            RectF(20f, 20f, 300f, 300f), motion, false, restStartsSeated = seated)
        return bitmap
    }

    private fun exportReview(bridge: TestPetBridge, behavior: GingerBehavior) {
        val sheet = Bitmap.createBitmap(1280, 320, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(0xfffaf7ef.toInt())
        val canvas = Canvas(sheet)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff904454.toInt(); textSize = 16f }
        for ((column, frame) in (18..21).withIndex()) {
            bridge.currentFrame = frame
            canvas.save()
            canvas.translate(column * 320f, 0f)
            behavior.onDraw(canvas, 160f, 180f)
            val ground: Float = 180f + requireNotNull(behavior.careBaselineOffsetY)
            canvas.drawLine(0f, ground, 320f, ground, paint)
            canvas.drawText("Ginger $frame", 8f, 24f, paint)
            canvas.restore()
        }
        File(context.cacheDir, "ginger-rest-native.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        sheet.recycle()
    }

    private fun mode(behavior: GingerBehavior): String = field(behavior, "mode").get(behavior).toString()
    private fun field(instance: Any, name: String) = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun withPet(block: (TestPetBridge, GingerBehavior) -> Unit) {
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.GINGER, 240, PetArtworkScale.forPet(PetType.GINGER))
            bridge.getWindowParams().apply { x = 400; y = bridge.groundY }
            behavior = GingerBehavior(bridge, SeededPetRandom(17))
        }
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        try {
            val deadline: Long = SystemClock.elapsedRealtime() + 10_000L
            while (loading.getBoolean(behavior) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(30)
            assertFalse("Ginger atlas failed to load", loading.getBoolean(behavior))
            block(bridge, behavior)
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
