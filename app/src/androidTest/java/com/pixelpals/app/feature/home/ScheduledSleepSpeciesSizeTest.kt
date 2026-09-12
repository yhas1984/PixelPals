package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.TelaBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ScheduledSleepSpeciesSizeTest {
    @Test
    fun scheduledSleepUsesSpeciesSizeAndKeepsTheBodySupported(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val size: Float = 180f
        val ground: Float = 220f
        val sheet: Bitmap = Bitmap.createBitmap(960, 1280, Bitmap.Config.ARGB_8888)
        val sheetCanvas: Canvas = Canvas(sheet)
        sheet.eraseColor(Color.rgb(250, 247, 239))
        try {
            listOf(PetType.TELA, PetType.CORGI, PetType.TARO).forEachIndexed { index, pet ->
                val art: HomeLocomotion = HomeLocomotion.load(context, pet)
                val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val scheduled: ScheduledPetSleep = ScheduledPetSleep(context, pet, scope)
                try {
                    setPrivate(scheduled, "art", art)
                    setPrivate(scheduled, "bedLoaded", true)
                    val motion: CompanionMotion = getPrivate(scheduled, "motion")
                    repeat(100) { motion.advanceScheduledRest(true, .1f) }
                    val actual: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
                    val expected: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
                    try {
                        actual.eraseColor(Color.TRANSPARENT)
                        expected.eraseColor(Color.TRANSPARENT)
                        val tint = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)
                        scheduled.draw(Canvas(actual), size, ground - actual.height / 2f, false, tint)
                        val expectedSize: Float = size * PetArtworkScale.speciesSize(pet) * art.desktopSizeAdjustment
                        val expectedTarget: RectF = RectF(
                            actual.width / 2f - expectedSize / 2f,
                            ground - expectedSize,
                            actual.width / 2f + expectedSize / 2f,
                            ground,
                        ).apply { offset(0f, expectedSize * .04f) }
                        art.draw(Canvas(expected), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                            colorFilter = tint
                        }, expectedTarget, motion, false)
                        val actualBounds: Bounds = requireNotNull(magentaBounds(actual))
                        val expectedBounds: Bounds = requireNotNull(magentaBounds(expected))
                        assertTrue("${pet.name} body width", abs(expectedBounds.width() - actualBounds.width()) <= 2)
                        assertTrue("${pet.name} body height", abs(expectedBounds.height() - actualBounds.height()) <= 2)
                        assertTrue("${pet.name} body center", abs(expectedBounds.centerX() - actualBounds.centerX()) <= 2f)
                        assertEquals("${pet.name} keeps feet supported", expectedBounds.bottom, actualBounds.bottom)
                        sheetCanvas.drawBitmap(actual, index * 320f, 0f, null)
                        sheetCanvas.drawBitmap(expected, index * 320f, 320f, null)
                        sheetCanvas.drawText("${pet.name} scheduled", index * 320f + 8f, 20f, labelPaint)
                        sheetCanvas.drawText("${pet.name} expected ${PetArtworkScale.speciesSize(pet)}x", index * 320f + 8f, 340f, labelPaint)
                    } finally {
                        actual.recycle()
                        expected.recycle()
                    }
                } finally {
                    scope.cancel()
                }
            }
            val scheduledTile: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            val desktopTile: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            val scheduledScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val scheduledTela: ScheduledPetSleep = ScheduledPetSleep(context, PetType.TELA, scheduledScope)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            lateinit var bridge: TestPetBridge
            lateinit var behavior: TelaBehavior
            instrumentation.runOnMainSync {
                bridge = TestPetBridge(context, PetType.TELA, petSpriteSize = 180, spriteScale = PetArtworkScale.forPet(PetType.TELA))
                behavior = TelaBehavior(bridge, SeededPetRandom(38))
            }
            try {
                setPrivate(scheduledTela, "art", HomeLocomotion.load(context, PetType.TELA))
                setPrivate(scheduledTela, "bedLoaded", true)
                val scheduledMotion: CompanionMotion = getPrivate(scheduledTela, "motion")
                repeat(100) { scheduledMotion.advanceScheduledRest(true, .1f) }
                val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
                val deadline: Long = android.os.SystemClock.elapsedRealtime() + 10_000L
                while (loading.getBoolean(behavior) && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50)
                assertTrue("Tela desktop renderer loaded", !loading.getBoolean(behavior))
                instrumentation.runOnMainSync {
                    val scheduledArt: HomeLocomotion = getPrivate(scheduledTela, "art")
                    bridge.currentFrame = scheduledArt.restFrameForHandoff(scheduledMotion, false) ?: 39
                    val baseline: Float = requireNotNull(behavior.careBaselineOffsetY)
                    val tint = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)
                    for (tinted: Boolean in listOf(false, true)) {
                        scheduledTile.eraseColor(Color.TRANSPARENT)
                        desktopTile.eraseColor(Color.TRANSPARENT)
                        bridge.animColorFilter = if (tinted) tint else null
                        behavior.onDraw(Canvas(desktopTile), 160f, 160f)
                        scheduledTela.draw(Canvas(scheduledTile), size, baseline, false, if (tinted) tint else null)
                        val top: Float = if (tinted) 960f else 640f
                        sheetCanvas.drawBitmap(scheduledTile, 0f, top, null)
                        sheetCanvas.drawBitmap(desktopTile, 320f, top, null)
                        sheetCanvas.drawText("Tela scheduled / frame ${bridge.currentFrame}", 8f, top + 20f, labelPaint)
                        sheetCanvas.drawText("Tela desktop / frame ${bridge.currentFrame}", 328f, top + 20f, labelPaint)
                        if (tinted) {
                            val actual: Bounds = requireNotNull(magentaBounds(scheduledTile))
                            val reference: Bounds = requireNotNull(magentaBounds(desktopTile))
                            assertTrue("Same Tela pose keeps its desktop width", kotlin.math.abs(actual.width() - reference.width()) <= 2)
                            assertTrue("Same Tela pose keeps its desktop height", kotlin.math.abs(actual.height() - reference.height()) <= 2)
                            assertEquals("Same Tela pose keeps its ground contact", reference.bottom, actual.bottom)
                            assertEquals("Same Tela pose keeps its horizontal anchor", reference.centerX(), actual.centerX(), 2f)
                            File(context.cacheDir, "scheduled-sleep-tela-camera.csv").writeText(
                                "route,width,height,center_x,bottom\n" +
                                    "scheduled,${actual.width()},${actual.height()},${actual.centerX()},${actual.bottom}\n" +
                                    "desktop,${reference.width()},${reference.height()},${reference.centerX()},${reference.bottom}\n")
                        }
                    }
                }
            } finally {
                instrumentation.runOnMainSync { behavior.destroy() }
                scheduledScope.cancel()
                scheduledTile.recycle()
                desktopTile.recycle()
            }
            File(context.cacheDir, "scheduled-sleep-species-size-review.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            sheet.recycle()
        }
    }

    private fun <T> getPrivate(instance: Any, name: String): T = ScheduledPetSleep::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(instance) as T

    private fun setPrivate(instance: Any, name: String, value: Any?): Unit {
        ScheduledPetSleep::class.java.getDeclaredField(name).apply { isAccessible = true }.set(instance, value)
    }

    private fun magentaBounds(bitmap: Bitmap): Bounds? {
        var left: Int = bitmap.width
        var top: Int = bitmap.height
        var right: Int = -1
        var bottom: Int = -1
        for (y: Int in 0 until bitmap.height) for (x: Int in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) == Color.MAGENTA) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        return if (right < left) null else Bounds(left, top, right, bottom)
    }

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width(): Int = right - left + 1
        fun height(): Int = bottom - top + 1
        fun centerX(): Float = (left + right) / 2f
    }

    private companion object {
        val labelPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 16f
        }
    }
}
