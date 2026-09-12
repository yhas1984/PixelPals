package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders Patito home artwork from the packaged atlas for visual comparison. */
@RunWith(AndroidJUnit4::class)
class HomePatitoArtworkReviewTest {
    @Test
    fun exportsIdleWalkStepsAndScheduledRestFromPackagedArtwork(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = HomeLocomotion.load(context, PetType.PATITO)
        val sprites = HomeLocomotion::class.java.getDeclaredField("sprites")
            .apply { isAccessible = true }.get(home)
        val packagedFrames = sprites.javaClass.getDeclaredField("frames")
            .apply { isAccessible = true }.get(sprites) as List<*>
        assertEquals("Patito home JSON must load all 16 packaged atlas frames", 16, packagedFrames.size)

        val output = Bitmap.createBitmap(8 * 200, 250, Bitmap.Config.ARGB_8888)
        val sheet = Canvas(output)
        val backgroundColor = Color.rgb(250, 247, 239)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 14f
        }
        val target = RectF(0f, 0f, 200f, 200f)
        try {
            val motion = CompanionMotion()
            renderSample(sheet, home, motion, target, backgroundColor, label, 0, "idle", 0f)
            for (index in 0 until 6) {
                renderSample(sheet, home, motion, target, backgroundColor, label, index + 1,
                    "walk", (index * 130 + 1) / 1000f)
            }

            motion.settleWithoutMovement(true)
            repeat(40) { motion.advanceScheduledRest(true, .1f) }
            renderSample(sheet, home, motion, target, backgroundColor, label, 7,
                "scheduled-rest", 4f, poseClip = null)

            File(context.cacheDir, "patito-home-review.png").outputStream().use {
                assertTrue(output.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            output.recycle()
        }
    }

    private fun renderSample(
        sheet: Canvas,
        home: HomeLocomotion,
        motion: CompanionMotion,
        target: RectF,
        backgroundColor: Int,
        label: Paint,
        index: Int,
        phase: String,
        seconds: Float,
        poseClip: String? = phase,
    ): Unit {
        val tile = Bitmap.createBitmap(200, 250, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(tile)
            canvas.drawColor(backgroundColor)
            home.draw(canvas, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                target, motion, false, poseClip, seconds)
            assertTrue("Patito $phase sample $index must draw visible artwork", hasArtwork(tile, backgroundColor))
            canvas.drawLine(8f, 200f, 192f, 200f, label)
            canvas.drawText("$phase / ${seconds}s", 8f, 228f, label)
            sheet.drawBitmap(tile, index * 200f, 0f, null)
        } finally {
            tile.recycle()
        }
    }

    private fun hasArtwork(bitmap: Bitmap, backgroundColor: Int): Boolean {
        for (y in 0 until 200) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) != backgroundColor) return true
        }
        return false
    }
}
