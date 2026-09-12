package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JellyHomeRenderingTest {
    @Test fun homeUsesOriginalMaterialAndOneCameraAcrossActiveStates(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val art = HomeLocomotion.load(context, PetType.JELLY)
        val original = BitmapFactory.decodeResource(context.resources, R.drawable.jelly_0,
            BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 })
        val expected = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try {
            val width = 256f * .82f
            val ground = 256f * .96f
            val left = (256f - width) / 2f
            val top = ground - width * 718f / 768f
            Canvas(expected).drawBitmap(original, null, RectF(left, top, left + width, top + width), paint)
            val reference = pixels(expected)
            val motion = CompanionMotion()
            val activity = CompanionMotion::class.java.getDeclaredField("activity").apply { isAccessible = true }
            for (state in listOf(CompanionActivity.OBSERVE, CompanionActivity.GREET,
                    CompanionActivity.APPROACH_TOY, CompanionActivity.APPROACH_BED,
                    CompanionActivity.EXPLORE, CompanionActivity.PLAY)) {
                activity.set(motion, state)
                for (reduced in listOf(false, true)) {
                    actual.eraseColor(0)
                    val drawnBounds = RectF()
                    val canvas = object : Canvas(actual) {
                        override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
                            drawnBounds.set(dst)
                            super.drawBitmap(bitmap, src, dst, paint)
                        }
                    }
                    art.draw(canvas, paint, RectF(0f, 0f, 256f, 256f), motion, reduced)
                    assertEquals(left, drawnBounds.left, .001f)
                    assertEquals(top, drawnBounds.top, .001f)
                    assertEquals(left + width, drawnBounds.right, .001f)
                    assertEquals(top + width, drawnBounds.bottom, .001f)
                    val rendered = pixels(actual)
                    // Android's subpixel rasterization can change individual
                    // edge samples despite the same camera within 0.001 pixels.
                    // Compare visible color error, not bit equality at that edge.
                    var channelDifference = 0L
                    for (i in reference.indices) for (shift in listOf(0, 8, 16, 24)) {
                        channelDifference += kotlin.math.abs(visibleChannel(reference[i], shift) - visibleChannel(rendered[i], shift))
                    }
                    val meanError = channelDifference.toDouble() / (reference.size * 4)
                    assertTrue("$state reduced=$reduced changed Jelly's artwork (mean error $meanError)", meanError < .01)
                }
            }
        } finally { original.recycle(); expected.recycle(); actual.recycle() }
    }

    @Test fun restAndWakeKeepTheHomeBodyAreaAndSupport(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val art = HomeLocomotion.load(context, PetType.JELLY)
        val motion = CompanionMotion()
        val activity = CompanionMotion::class.java.getDeclaredField("activity").apply { isAccessible = true }
        val elapsed = CompanionMotion::class.java.getDeclaredField("elapsed").apply { isAccessible = true }
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val target = RectF(0f, 0f, 256f, 256f)
        fun render(state: CompanionActivity, seconds: Float, reduced: Boolean): IntArray {
            activity.set(motion, state); elapsed.setFloat(motion, seconds)
            bitmap.eraseColor(0)
            art.draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG), target, motion, reduced)
            return pixels(bitmap)
        }
        try {
            val reference = render(CompanionActivity.OBSERVE, 0f, false)
            val area = reference.count { it ushr 24 >= 32 }
            val ground = reference.indices.last { reference[it] ushr 24 >= 32 } / 256 + 1
            for (reduced in listOf(false, true)) for (state in listOf(CompanionActivity.REST, CompanionActivity.WAKE)) {
                for (index in 0..36) {
                    val sample = render(state, index * 1.2f / 36f, reduced)
                    val count = sample.count { it ushr 24 >= 32 }
                    assertTrue("$state body area", kotlin.math.abs(count - area) <= area * .03f)
                    val support = sample.indices.last { sample[it] ushr 24 >= 32 } / 256 + 1
                    assertEquals("$state ground", ground.toFloat(), support.toFloat(), 2f)
                }
            }
            assertArrayEquals("held sleep does not cycle back to awake",
                render(CompanionActivity.REST, 1.2f, false), render(CompanionActivity.REST, 10f, false))
        } finally { bitmap.recycle() }
    }

    private fun visibleChannel(color: Int, shift: Int): Int {
        val alpha = color ushr 24
        return if (shift == 24) alpha else (color ushr shift and 255) * alpha / 255
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}
