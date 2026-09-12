package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.R
import java.io.File
import com.pixelpals.app.core.motion.SeededPetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real desktop renderer without starting overlays or touching saved pets. */
@RunWith(AndroidJUnit4::class)
class DesktopSpriteScaleTest {
    @Test
    fun originalCorgiWalkKeepsWidthAndFeetAcrossFourFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val bridge = TestPetBridge(context, PetType.CORGI)
            bridge.spriteIdleContentFraction = .8047f
            bridge.spriteFrameContentFractions = floatArrayOf(.8099f, .8542f, .7956f, .8281f)
            val sourceFrames = listOf(R.drawable.corgi_10, R.drawable.corgi_11,
                R.drawable.corgi_12, R.drawable.corgi_13).map {
                BitmapFactory.decodeResource(context.resources, it, BitmapFactory.Options().apply { inScaled = false })
            }
            val behavior = object : BaseBehavior(bridge, SeededPetRandom(1)) {
                override val resourceIds: List<Int> = emptyList()
                init { frames.addAll(sourceFrames); isLoading = false }
            }
            val strip = Bitmap.createBitmap(640, 160, Bitmap.Config.ARGB_8888)
            try {
                val bounds = sourceFrames.indices.map { index ->
                    bridge.currentFrame = index
                    val output = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                    behavior.onDraw(Canvas(output), 80f, 80f)
                    Canvas(strip).drawBitmap(output, index * 160f, 0f, null)
                    opaqueBounds(output).also { output.recycle() }
                }
                assertTrue("Walk width pulses", bounds.maxOf { it.width() } - bounds.minOf { it.width() } <= 1)
                assertTrue("Walk feet float", bounds.maxOf { it.bottom } - bounds.minOf { it.bottom } <= 1)
                File(context.getExternalFilesDir(null), "desktop-corgi-scale.png").outputStream().use {
                    strip.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                behavior.destroy()
                strip.recycle()
            }
        }
    }

    @Test
    fun raisingHeadPreservesBodyWidthAndGroundContact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            bridge.spriteIdleContentFraction = .5f
            bridge.spriteFrameContentFractions = floatArrayOf(.5f, .8f, .3f)
            val sourceFrames = listOf(40f, 16f, 56f).map { top ->
                Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
                    Canvas(this).drawRect(16f, top, 64f, 80f, Paint().apply { color = Color.WHITE })
                }
            }
            val behavior = object : BaseBehavior(bridge, SeededPetRandom(1)) {
                override val resourceIds: List<Int> = emptyList()
                init {
                    frames.addAll(sourceFrames)
                    isLoading = false
                }
            }
            try {
                val bounds = sourceFrames.indices.map { index ->
                    bridge.currentFrame = index
                    val output = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                    behavior.onDraw(Canvas(output), 80f, 80f)
                    val bounds = opaqueBounds(output)
                    output.recycle()
                    bounds
                }
                bounds.forEach { pose ->
                    assertEquals("Body width must survive pose changes", bounds.first().width(), pose.width())
                    assertEquals("Feet must stay planted", bounds.first().bottom, pose.bottom)
                }
                assertTrue(bounds[1].height() > bounds[0].height())
                assertTrue(bounds[2].height() < bounds[0].height())
            } finally {
                behavior.destroy()
            }
        }
    }

    private fun opaqueBounds(bitmap: Bitmap): Rect {
        val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 127) {
                bounds.left = minOf(bounds.left, x)
                bounds.top = minOf(bounds.top, y)
                bounds.right = maxOf(bounds.right, x + 1)
                bounds.bottom = maxOf(bounds.bottom, y + 1)
            }
        }
        assertTrue("Renderer must produce a visible pet", !bounds.isEmpty)
        return bounds
    }
}
