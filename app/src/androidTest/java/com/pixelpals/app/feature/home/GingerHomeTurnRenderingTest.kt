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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GingerHomeTurnRenderingTest {
    @Test fun optionalAtlasTurnKeepsTheActorPlantedAndCommitsBothHeadings(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val art = HomeLocomotion.load(context, PetType.GINGER)
        assertEquals(.50f, art.turnDurationSeconds, 0f)
        assertEquals(.30f, art.turnCommitSeconds, 0f)
        val atlas = context.assets.open("pets/ginger/ginger_turn_v2.png").use {
            requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }))
        }
        val cell = atlas.width / 4
        val expected = HomeSpriteFrames((0 until 24).map { index ->
            atlas to Rect(index % 4 * cell, index / 4 * cell, (index % 4 + 1) * cell, (index / 4 + 1) * cell)
        }, PointF(cell / 2f, cell * 368f / 384f), .875f)
        val target = RectF(0f, 0f, 160f, 160f)
        val actualBitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val expectedBitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val review = Bitmap.createBitmap(160 * 6, 320, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val motion = CompanionMotion(CompanionIntentSelector(kotlin.random.Random(41)), .50f, .30f, gingerPostures = true)
        try {
            for ((row, left) in listOf(false, true).withIndex()) {
                startTurn(motion, left)
                val startX = motion.x
                val startY = motion.y
                val times = listOf(.05f, .15f, .25f, .35f, .45f, .50f)
                var previous = 0f
                times.forEachIndexed { column, seconds ->
                    motion.advance(seconds - previous, if (left) 860f else 140f, 650f, 1f, 0, toyFacesLeft = !left)
                    previous = seconds
                    assertEquals(startX, motion.x, .001f)
                    assertEquals(startY, motion.y, .001f)
                    actualBitmap.eraseColor(0); expectedBitmap.eraseColor(0)
                    val exteriorLeft = if (motion.isTurning) motion.turnFromFacingLeft else motion.isFacingLeft
                    val actualCanvas = Canvas(actualBitmap)
                    if (exteriorLeft) actualCanvas.scale(-1f, 1f, 80f, 160f)
                    art.draw(actualCanvas, paint, target, motion, false)
                    val index = ((seconds / .10f).toInt()).coerceIn(0, 5)
                    val expectedCanvas = Canvas(expectedBitmap)
                    // Ginger's source atlas faces left. HomeSceneView supplies the
                    // physical heading outside the bank; the turn pose adds only
                    // its relative mirror while the turn is still active.
                    val nativeFacesLeft = true
                    val relativeMirror = booleanArrayOf(false, false, false, true, true, false)[index]
                    if (exteriorLeft.xor(nativeFacesLeft).xor(relativeMirror))
                        expectedCanvas.scale(-1f, 1f, 80f, 160f)
                    expected.draw(expectedCanvas, paint, target, intArrayOf(18, 22, 23, 22, 18, 18)[index])
                    assertTrue("Ginger turn frame $seconds / left=$left", actualBitmap.sameAs(expectedBitmap))
                    Canvas(review).drawBitmap(actualBitmap, column * 160f, row * 160f, null)
                }
                assertEquals(!left, motion.isFacingLeft)
            }
            File(context.cacheDir, "ginger-home-turn.png").outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { actualBitmap.recycle(); expectedBitmap.recycle(); review.recycle(); atlas.recycle() }
    }

    @Test fun reducedMotionUsesTheStableRestingPoseAndSceneWiresGingerTiming(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val art = HomeLocomotion.load(context, PetType.GINGER)
        val motion = CompanionMotion(turnDurationSeconds = art.turnDurationSeconds, turnCommitSeconds = art.turnCommitSeconds, gingerPostures = true)
        val actual = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val target = RectF(0f, 0f, 160f, 160f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val sprites = HomeLocomotion::class.java.getDeclaredField("sprites").apply { isAccessible = true }.get(art) as HomeSpriteFrames
        try {
            startTurn(motion, false); repeat(5) { motion.advance(.1f, 140f, 650f, 1f, 0) }
            art.draw(Canvas(actual), paint, target, motion, true)
            val expectedCanvas = Canvas(expected)
            // The stable Ginger source frame still uses the bank's native left
            // orientation; reduced motion removes the relative turn animation.
            expectedCanvas.scale(-1f, 1f, 80f, 160f)
            sprites.draw(expectedCanvas, paint, target, 0)
            assertTrue("Reduced Ginger turn must use stable frame 0", actual.sameAs(expected))
        } finally { actual.recycle(); expected.recycle() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val scene = HomeSceneView(context)
            runBlocking { scene.loadPet(PetType.GINGER) }
            val field = HomeSceneView::class.java.getDeclaredField("motion").apply { isAccessible = true }
            val wired = field.get(scene) as CompanionMotion
            assertEquals(.50f, wired.turnDurationSeconds, 0f)
            val commit = CompanionMotion::class.java.getDeclaredField("turnCommitSeconds").apply { isAccessible = true }
            assertEquals(.30f, commit.getFloat(wired), 0f)
        }
    }

    private fun startTurn(motion: CompanionMotion, left: Boolean) {
        motion.finishExcursion(left)
        motion.context = CompanionIntentContext(hasToy = true)
        motion.beginIntent(CompanionIntent.PLAY)
        motion.advance(0f, if (left) 860f else 140f, 650f, 1f, 0, toyFacesLeft = !left)
        assertTrue(motion.isTurning)
        assertEquals(left, motion.turnFromFacingLeft)
    }
}
