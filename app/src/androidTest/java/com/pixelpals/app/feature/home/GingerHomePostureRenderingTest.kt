package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Compares the real home renderer with explicit frames and an independent fixed camera. */
@RunWith(AndroidJUnit4::class)
class GingerHomePostureRenderingTest {
    @Test fun bothHeadingsRenderPlantedRiseAndSitWithoutDroppingTheEndpoints(): Unit = runBlocking {
        val fixture = Fixture(HomeLocomotion.load(context, PetType.GINGER))
        val sheet = Bitmap.createBitmap(160 * 10, 320, Bitmap.Config.ARGB_8888)
        try {
            for ((row, left) in listOf(false, true).withIndex()) {
                val target = if (left) 240f else 760f
                val motion = CompanionMotion(gingerPostures = true)
                motion.context = CompanionIntentContext(hasToy = true)
                motion.finishExcursion(left)
                advance(motion, target, .7f)
                assertTrue(motion.gingerIsSeated)
                motion.beginIntent(CompanionIntent.PLAY)
                val samples = listOf(.04f to 0, .20f to 16, .40f to 17, .50f to 18, .60f to 18)
                var previous = 0f
                for ((column, sample) in samples.withIndex()) {
                    advance(motion, target, sample.first - previous)
                    previous = sample.first
                    assertEquals(500f, motion.x, 0f)
                    fixture.compare(motion, sample.second, left, "rise $left at ${sample.first}")
                    Canvas(sheet).drawBitmap(fixture.actual, column * 160f, row * 160f, null)
                }
                var ticks = 0
                while (motion.activity != CompanionActivity.PLAY && ticks++ < 240) motion.advance(.05f, target, 500f, 1f, 80)
                assertEquals("Route must finish", CompanionActivity.PLAY, motion.activity)
                assertEquals(target, motion.x, .01f)
                previous = 0f
                for ((column, sample) in listOf(.04f to 18, .20f to 17, .40f to 16, .60f to 0, .80f to 0).withIndex()) {
                    advance(motion, target, sample.first - previous)
                    previous = sample.first
                    assertEquals(target, motion.x, .01f)
                    fixture.compare(motion, sample.second, left, "sit $left at ${sample.first}")
                    Canvas(sheet).drawBitmap(fixture.actual, (column + 5) * 160f, row * 160f, null)
                }
                assertTrue(motion.gingerIsSeated)
                assertNull(motion.gingerPostureTransition)
            }
            File(context.cacheDir, "ginger-home-postures.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { fixture.close(); sheet.recycle() }
    }

    @Test fun scheduledSleepUsesEntryPoseAndWakeEndsUprightIncludingReducedMotion(): Unit = runBlocking {
        val fixture = Fixture(HomeLocomotion.load(context, PetType.GINGER))
        try {
            val resting = CompanionMotion()
            resting.advanceScheduledRest(true, 0f)
            var previous = 0f
            for ((index, sample) in listOf(.04f to 18, .20f to 19, .40f to 20, .60f to 21, .80f to 21).withIndex()) {
                val (time, frame) = sample
                step(time - previous) { resting.advanceScheduledRest(true, it) }
                previous = time
                fixture.compare(resting, frame, false, "standing entry $time", startsSeated = false)
                fixture.compare(resting, listOf(0, 16, 19, 20, 21)[index], false, "seated entry $time", startsSeated = true)
            }
            fixture.compare(resting, 21, false, "reduced sleep", reduced = true)
            resting.advanceScheduledRest(false, 0f)
            assertEquals(CompanionActivity.WAKE, resting.activity)
            previous = 0f
            for ((time, frame) in listOf(.10f to 21, .25f to 20, .45f to 19, .65f to 18, .90f to 18, 1.10f to 18)) {
                step(time - previous) { resting.advanceScheduledWake(it) }
                previous = time
                fixture.compare(resting, frame, false, "wake $time")
            }
            fixture.compare(resting, 18, false, "reduced wake", reduced = true)
            fixture.compare(CompanionMotion(gingerPostures = true), 0, false, "reduced idle", reduced = true)
        } finally { fixture.close() }
    }

    private class Fixture(private val art: HomeLocomotion) {
        private val atlas = context.assets.open("pets/ginger/ginger_rest_v2.png").use {
            requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }))
        }
        private val frames = HomeSpriteFrames((0 until 22).map { index ->
            atlas to Rect(index % 4 * 192, index / 4 * 192, (index % 4 + 1) * 192, (index / 4 + 1) * 192)
        }, PointF(96f, 184f), .875f, List(22) { if (it == 0 || it == 1) .68f else if (it == 2) .70f else 1f })
        val actual = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        private val expected = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        private val target = RectF(0f, 0f, 160f, 160f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        init { assertTrue("Debug must load the optional posture bank", art.gingerPosturePack) }
        fun compare(motion: CompanionMotion, frame: Int, left: Boolean, label: String, reduced: Boolean = false, startsSeated: Boolean = true) {
            actual.eraseColor(0); expected.eraseColor(0)
            val actualCanvas = Canvas(actual)
            // HomeSceneView supplies the actor heading outside the bank.
            if (left) actualCanvas.scale(-1f, 1f, 80f, 160f)
            art.draw(actualCanvas, paint, target, motion, reduced, restStartsSeated = startsSeated)
            val expectedCanvas = Canvas(expected)
            if (!left) expectedCanvas.scale(-1f, 1f, 80f, 160f)
            frames.draw(expectedCanvas, paint, target, frame)
            if (!actual.sameAs(expected)) {
                val name: String = label.replace(Regex("[^a-zA-Z0-9]"), "_")
                for ((suffix, bitmap) in listOf("actual" to actual, "expected" to expected))
                    File(context.cacheDir, "ginger-$name-$suffix.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
            }
            assertTrue(label, actual.sameAs(expected))
        }
        fun close() { actual.recycle(); expected.recycle(); atlas.recycle() }
    }

    private fun advance(motion: CompanionMotion, target: Float, seconds: Float) = step(seconds) { motion.advance(it, target, 500f, 1f, 80) }
    private fun step(seconds: Float, tick: (Float) -> Unit) {
        var remaining = seconds
        while (remaining > .000001f) {
            val delta = remaining.coerceAtMost(.05f)
            tick(delta)
            remaining -= delta
        }
    }
    private companion object {
        val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    }
}
