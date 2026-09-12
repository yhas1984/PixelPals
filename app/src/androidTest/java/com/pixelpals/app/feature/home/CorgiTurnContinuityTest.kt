package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.CorgiArtworkScale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CorgiTurnContinuityTest {
    @Test fun realAtlasTurnUsesTheSameCameraAndRelativeMirrorsInBothDirections(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val art = HomeLocomotion.load(context, PetType.CORGI)
        assertEquals(.56f, art.turnDurationSeconds, 0f)
        assertEquals(.56f, art.turnCommitSeconds, 0f)
        val atlas = context.assets.open("pets/corgi/corgi_motion_v2.png").use {
            requireNotNull(android.graphics.BitmapFactory.decodeStream(it))
        }
        val cell = 256
        val expected = HomeSpriteFrames((0 until 23).map { index ->
            atlas to Rect(index % 4 * cell, index / 4 * cell, (index % 4 + 1) * cell, (index / 4 + 1) * cell)
        }, PointF(cell / 2f, cell * CorgiArtworkScale.ORIGINAL_GROUND), CorgiArtworkScale.ORIGINAL_CELL,
            List(23) { 1f })
        val target = RectF(0f, 0f, 160f, 160f)
        val actualBitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val expectedBitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val review = Bitmap.createBitmap(160 * 8, 160 * 2, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val motion = CompanionMotion(CompanionIntentSelector(kotlin.random.Random(41)), .56f, .56f)
        try {
            for (left in listOf(false, true)) {
                startTurn(motion, left)
                val startX = motion.x
                val startY = motion.y
                val times = listOf(.04f, .12f, .20f, .28f, .36f, .44f, .52f, .56f)
                var previous = 0f
                times.forEachIndexed { column, seconds ->
                    motion.advance(seconds - previous, if (left) 860f else 140f, 650f, 1f, 0, toyFacesLeft = !left)
                    previous = seconds
                    assertEquals("Turn keeps X planted", startX, motion.x, .001f)
                    assertEquals("Turn keeps Y planted", startY, motion.y, .001f)
                    actualBitmap.eraseColor(0)
                    expectedBitmap.eraseColor(0)
                    val exteriorLeft = if (motion.isTurning) motion.turnFromFacingLeft else motion.isFacingLeft
                    val actualCanvas = Canvas(actualBitmap)
                    if (exteriorLeft) actualCanvas.scale(-1f, 1f, target.centerX(), target.bottom)
                    art.draw(actualCanvas, paint, target, motion, false)
                    val expectedPose = expectedPose(seconds)
                    val totalMirror = left.xor(expectedPose.second)
                    val expectedCanvas = Canvas(expectedBitmap)
                    if (totalMirror) expectedCanvas.scale(-1f, 1f, target.centerX(), target.bottom)
                    expected.draw(expectedCanvas, paint, target, expectedPose.first)
                    assertTrue("Real turn frame at $seconds s / left=$left", actualBitmap.sameAs(expectedBitmap))
                    if (seconds >= .56f) {
                        assertEquals("Turn commits the opposite heading at the endpoint", !left, motion.isFacingLeft)
                        assertEquals("Endpoint returns to the original standing frame", 0, expectedPose.first)
                    }
                    Canvas(review).drawBitmap(actualBitmap, column * 160f, if (left) 160f else 0f, null)
                }
            }
            File(context.cacheDir, "corgi-home-turn.png").outputStream().use {
                review.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            actualBitmap.recycle(); expectedBitmap.recycle(); review.recycle(); atlas.recycle()
        }
    }

    @Test fun reducedMotionUsesOriginalIdleFrameAndNeverTurnFrames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val art = HomeLocomotion.load(context, PetType.CORGI)
        val motion = CompanionMotion(turnDurationSeconds = art.turnDurationSeconds, turnCommitSeconds = art.turnCommitSeconds)
        val actual = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val target = RectF(0f, 0f, 160f, 160f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val sprites = HomeLocomotion::class.java.getDeclaredField("sprites").apply { isAccessible = true }.get(art) as HomeSpriteFrames
        try {
            for (left in listOf(false, true)) {
                startTurn(motion, left)
                repeat(4) { motion.advance(.1f, if (left) 860f else 140f, 650f, 1f, 0) }
                actual.eraseColor(0); expected.eraseColor(0)
                val actualCanvas = Canvas(actual)
                if (left) actualCanvas.scale(-1f, 1f, target.centerX(), target.bottom)
                art.draw(actualCanvas, paint, target, motion, true)
                val expectedCanvas = Canvas(expected)
                if (left) expectedCanvas.scale(-1f, 1f, target.centerX(), target.bottom)
                sprites.draw(expectedCanvas, paint, target, 0)
                assertTrue("Reduced turn must use original frame 0", actual.sameAs(expected))
            }
        } finally { actual.recycle(); expected.recycle() }
    }

    @Test fun loadingHomeSceneWiresTheExtendedTurnTiming() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val scene = HomeSceneView(instrumentation.targetContext)
            runBlocking { scene.loadPet(PetType.CORGI) }
            val motion = HomeSceneView::class.java.getDeclaredField("motion").apply { isAccessible = true }.get(scene) as CompanionMotion
            assertEquals(.56f, motion.turnDurationSeconds, 0f)
        }
    }

    private fun startTurn(motion: CompanionMotion, left: Boolean) {
        motion.finishExcursion(left)
        motion.context = CompanionIntentContext(hasToy = true)
        motion.beginIntent(CompanionIntent.PLAY)
        motion.advance(0f, if (left) 860f else 140f, 650f, 1f, 0)
        assertTrue(motion.isTurning)
        assertEquals(left, motion.turnFromFacingLeft)
        assertEquals(0f, motion.turnElapsed, 0f)
    }

    private fun expectedPose(seconds: Float): Pair<Int, Boolean> {
        val segment = (seconds / .08f).toInt().coerceIn(0, 6)
        val frames = intArrayOf(0, 20, 21, 22, 21, 20, 0)
        val mirrors = booleanArrayOf(false, false, false, false, true, true, true)
        return frames[segment] to mirrors[segment]
    }
}
