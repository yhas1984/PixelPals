package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.CorgiFetchMotion
import com.pixelpals.app.core.care.scene.CorgiFetchPlan
import com.pixelpals.app.core.care.scene.CorgiFetchPose
import com.pixelpals.app.core.motion.PetBounds
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Canvas-only checks; safe on the user's phone, no database or preference mutations. */
@RunWith(AndroidJUnit4::class)
class CorgiDesktopFeedRendererTest {
    @Test fun caughtBallFollowsTheMouthAsTheHeadLiftsInBothDirections(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.CORGI)
        val renderer: CorgiDesktopCareRenderer = CorgiDesktopCareRenderer()
        val painter: CarePropPainter = CarePropPainter()
        val output: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val review: Bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888)
        review.eraseColor(Color.rgb(238, 232, 245))
        for (left: Boolean in listOf(false, true)) {
            val plan: CorgiFetchPlan = CorgiFetchMotion.createPlan(CarePoint(400f, 200f),
                PetBounds(0, 800, 0, 200), 160, left, false)
            var previousY: Float = Float.MAX_VALUE
            listOf(0L, 150L, 300L).forEachIndexed { column, elapsed ->
                val pose: CorgiFetchPose = CorgiFetchMotion.getPose(plan, plan.catchMs + elapsed)
                val frame: CorgiFetchFrame = CorgiFetchFrame.fromPose(plan, pose, pack.spec.anchors[pose.careFrame])
                assertTrue(pose.isCaught)
                assertNull(frame.regularFrame)
                assertTrue("The held ball must rise with the mouth", frame.ball.y < previousY)
                assertTrue((frame.ball.x - frame.pet.x - 80f) * plan.direction > 0f)
                assertEquals(0f, frame.rotation, 0f)
                previousY = frame.ball.y
                output.eraseColor(Color.TRANSPARENT)
                val canvas: Canvas = Canvas(output)
                renderer.draw(canvas, pack, 160, plan.catchMs + elapsed, left, false,
                    CareSceneAction.PLAY, pose.careFrame)
                painter.draw(canvas, CareSceneAction.PLAY, frame.ball.x - frame.pet.x + 80f,
                    frame.ball.y - frame.pet.y + 80f, 160f * .30f * .86f)
                Canvas(review).drawBitmap(output, column * 320f, if (left) 320f else 0f, Paint())
            }
        }
        val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
        File(directory, "corgi-fetch-mouth.png").outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
        output.recycle()
        review.recycle()
        pack.bitmap.recycle()
    }

    @Test fun feetAndBowlStayAnchoredAcrossBitesAndFacingDirections(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.CORGI)
        val renderer: CorgiDesktopCareRenderer = CorgiDesktopCareRenderer()
        val bitmap: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val review: Bitmap = Bitmap.createBitmap(1_600, 640, Bitmap.Config.ARGB_8888)
        review.eraseColor(Color.rgb(238, 232, 245))
        val phases: List<Long> = listOf(0L, 400L, 700L, 4_100L, 5_000L)
        val bottoms: MutableList<Int> = mutableListOf()
        for (left: Boolean in listOf(false, true)) {
            phases.forEachIndexed { column, elapsed ->
                bitmap.eraseColor(Color.TRANSPARENT)
                renderer.draw(Canvas(bitmap), pack, 160, elapsed, left, reducedMotion = false)
                val pixels: IntArray = IntArray(320 * 320)
                bitmap.getPixels(pixels, 0, 320, 0, 0, 320, 320)
                val opaque: List<Int> = pixels.indices.filter { Color.alpha(pixels[it]) > 128 }
                assertTrue(opaque.size > 4_000)
                // Only the original sprite footprint and bowl, not a filled card/stage.
                assertTrue(opaque.all { it % 320 in 70..250 && it / 320 in 85..240 })
                bottoms.add(opaque.maxOf { it / 320 })
                Canvas(review).drawBitmap(bitmap, column * 320f, if (left) 320f else 0f, Paint())
            }
        }
        assertTrue("Feet must not jump while chewing: $bottoms", bottoms.max() - bottoms.min() <= 3)
        val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
        File(directory, "corgi-desktop-feed.png").outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        review.recycle()
        pack.bitmap.recycle()
    }

    @Test fun reducedMotionKeepsThePetStationaryWhileFoodDecreases(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.CORGI)
        val renderer: CorgiDesktopCareRenderer = CorgiDesktopCareRenderer()
        val before: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val after: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(before), pack, 160, 500L, false, reducedMotion = true)
        renderer.draw(Canvas(after), pack, 160, 5_000L, false, reducedMotion = true)
        assertFalse(before.sameAs(after))
        // Away from the bowl, every pixel stays still.
        for (y: Int in 0 until 320) for (x: Int in 0 until 170) {
            assertEquals(before.getPixel(x, y), after.getPixel(x, y))
        }
        before.recycle()
        after.recycle()
        pack.bitmap.recycle()
    }

    @Test fun cloudActionsRenderOnTheSameTransparentDesktopFootprint(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.CORGI)
        val renderer: CorgiDesktopCareRenderer = CorgiDesktopCareRenderer()
        val output: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val review: Bitmap = Bitmap.createBitmap(1_280, 1_920, Bitmap.Config.ARGB_8888)
        review.eraseColor(Color.rgb(238, 232, 245))
        CareSceneAction.entries.forEachIndexed { row, action ->
            listOf(0L, 1_000L, 2_000L, 3_500L).forEachIndexed { column, elapsed ->
                output.eraseColor(Color.TRANSPARENT)
                renderer.draw(Canvas(output), pack, 160, elapsed, false, false, action)
                val pixels: IntArray = IntArray(320 * 320)
                output.getPixels(pixels, 0, 320, 0, 0, 320, 320)
                val opaque: List<Int> = pixels.indices.filter { Color.alpha(pixels[it]) > 128 }
                assertTrue("$action must remain visible", opaque.size > 3_000)
                assertTrue("$action must not clip at the overlay edges", opaque.all { it % 320 in 20..300 && it / 320 in 20..300 })
                Canvas(review).drawBitmap(output, column * 320f, row * 320f, Paint())
            }
        }
        val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
        File(directory, "corgi-desktop-actions.png").outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
        output.recycle()
        review.recycle()
        pack.bitmap.recycle()
    }

    @Test fun additionalDesktopActionsStayVisibleAndUnclippedInBothDirections(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.CORGI)
        val renderer: CorgiDesktopCareRenderer = CorgiDesktopCareRenderer()
        val output: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        for (action: CareSceneAction in com.pixelpals.app.core.care.scene.CorgiAdditionalCareMotion.actions) {
            for (left: Boolean in listOf(false, true)) for (reduced: Boolean in listOf(false, true)) {
                for (elapsed: Long in 0L..7_000L step 250L) {
                    output.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(output), pack, 160, elapsed, left, reduced, action)
                    val pixels: IntArray = IntArray(320 * 320)
                    output.getPixels(pixels, 0, 320, 0, 0, 320, 320)
                    val opaque: List<Int> = pixels.indices.filter { Color.alpha(pixels[it]) > 128 }
                    assertTrue("$action must remain visible at $elapsed", opaque.size > 3_000)
                    assertTrue("$action must not clip at $elapsed", opaque.all { it % 320 in 20..300 && it / 320 in 20..300 })
                }
            }
        }
        output.recycle()
        pack.bitmap.recycle()
    }
}
