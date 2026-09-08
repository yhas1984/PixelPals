package com.pixelpals.app

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real desktop renderer, including its asynchronously decoded original frames. */
@RunWith(AndroidJUnit4::class)
class CorgiScaleRenderingTest {
    @Test fun gaitKeepsStandingBodyScaleAndFloorInBothDirections(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            behavior = CorgiBehavior(bridge, SeededPetRandom(8))
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(100) { if (loading.getBoolean(behavior)) delay(50) }
            assertFalse("Original artwork loaded", loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                val frames = BaseBehavior::class.java.getDeclaredField("frames").apply { isAccessible = true }.get(behavior) as List<*>
                assertEquals(14, frames.size)
                assertTrue("Never substitute a sitting pose for an unloaded gait frame", frames.all { it != null })
                val bitmap: Bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                val review: Bitmap = Bitmap.createBitmap(800, 320, Bitmap.Config.ARGB_8888)
                review.eraseColor(Color.rgb(250, 247, 239))
                val bottoms: MutableList<Int> = mutableListOf()
                var standingHeight: Int = 0
                for (left: Boolean in listOf(false, true)) {
                    bridge.animScaleX = if (left) -1f else 1f
                    listOf(0, 10, 11, 12, 13).forEachIndexed { column, frame ->
                        bitmap.eraseColor(Color.TRANSPARENT)
                        bridge.currentFrame = frame
                        behavior.onDraw(Canvas(bitmap), 80f, 80f)
                        val pixels: IntArray = IntArray(160 * 160)
                        bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 160)
                        val opaque: List<Int> = pixels.indices.filter { Color.alpha(pixels[it]) >= 128 }
                        assertTrue(opaque.isNotEmpty())
                        val bottom: Int = opaque.maxOf { it / 160 }
                        val height: Int = bottom - opaque.minOf { it / 160 } + 1
                        if (frame == 0) standingHeight = height
                        // Allow real gait compression, but reject the old 20% camera zoom.
                        assertTrue("Frame $frame grew from $standingHeight to $height", height <= standingHeight * 1.15f)
                        assertTrue("Frame $frame shrank excessively", height >= standingHeight * .85f)
                        bottoms.add(bottom)
                        Canvas(review).drawBitmap(bitmap, column * 160f, if (left) 160f else 0f, Paint())
                    }
                }
                assertTrue("Feet stay planted: $bottoms", bottoms.max() - bottoms.min() <= 1)
                File(instrumentation.targetContext.cacheDir, "corgi-scale-review.png").outputStream().use {
                    review.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                review.recycle()
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
