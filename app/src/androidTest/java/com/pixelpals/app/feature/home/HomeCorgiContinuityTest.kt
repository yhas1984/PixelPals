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
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.CorgiArtworkScale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCorgiContinuityTest {
    @Test fun editingKeepsTheCurrentWalkingPoseAndResumesWithoutASnap(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val scene: HomeSceneView = HomeSceneView(instrumentation.targetContext)
            runBlocking { scene.loadPet(PetType.CORGI) }
            val motion: CompanionMotion = HomeSceneView::class.java.getDeclaredField("motion")
                .apply { isAccessible = true }.get(scene) as CompanionMotion
            motion.context = CompanionIntentContext(hasToy = true)
            motion.beginIntent(CompanionIntent.PLAY)
            repeat(10) { motion.advance(.1f, 800f, 500f, 1f, 0) }
            assertTrue("Exercise a moving pose", motion.speed > 0f)
            val draw = HomeSceneView::class.java.getDeclaredMethod("drawCompanion", Canvas::class.java).apply { isAccessible = true }
            val reference: Bitmap = Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888)
            val edited: Bitmap = Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888)
            try {
                draw.invoke(scene, Canvas(reference))
                scene.isEditing = true
                draw.invoke(scene, Canvas(edited))
                assertTrue("Entering decoration must freeze the displayed pose", reference.sameAs(edited))
                scene.isEditing = false
                edited.eraseColor(0)
                draw.invoke(scene, Canvas(edited))
                assertTrue("Leaving decoration must preserve the pose until time advances", reference.sameAs(edited))
            } finally { reference.recycle(); edited.recycle() }
        }
    }

    @Test fun anticipationAndIdleDoNotRescaleTheBody(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val scene: HomeSceneView = HomeSceneView(instrumentation.targetContext)
            runBlocking { scene.loadPet(PetType.CORGI) }
            scene.requestMotionReview(CompanionIntent.PLAY)
            val motion: CompanionMotion = HomeSceneView::class.java.getDeclaredField("motion").apply {
                isAccessible = true
            }.get(scene) as CompanionMotion
            val draw = HomeSceneView::class.java.getDeclaredMethod("drawCompanion", Canvas::class.java).apply { isAccessible = true }
            val first: Bitmap = Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888)
            val second: Bitmap = Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888)
            try {
                draw.invoke(scene, Canvas(first))
                CompanionMotion::class.java.getDeclaredField("elapsed").apply { isAccessible = true }
                    .setFloat(motion, CompanionMotion.ANTICIPATION_SECONDS / 2f)
                HomeSceneView::class.java.getDeclaredField("activeTime").apply { isAccessible = true }.setLong(scene, 785L)
                draw.invoke(scene, Canvas(second))
                assertTrue("The planted anticipation must not inflate or flatten Corgi", first.sameAs(second))
                assertTrue("A pet was actually rendered", (0 until first.width).any { first.getPixel(it, 590) ushr 24 > 0 })
            } finally { first.recycle(); second.recycle() }
        }
    }

    @Test fun walkingUsesTheDesktopStrideAtDifferentActorSizes(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locomotion: HomeLocomotion = HomeLocomotion.load(context, PetType.CORGI)
        val resources: List<Int> = listOf(R.drawable.corgi_10, R.drawable.corgi_11, R.drawable.corgi_12, R.drawable.corgi_13)
        val hasPostures = "corgi_motion_v2.json" in context.assets.list("pets/corgi").orEmpty()
        val atlas: Bitmap? = if (hasPostures) context.assets.open("pets/corgi/corgi_motion_v2.png").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        } else null
        val sources: List<Bitmap> = if (atlas != null) (10..13).map { index ->
            Bitmap.createBitmap(atlas, index % 4 * 256, index / 4 * 256, 256, 256)
        } else resources.map { BitmapFactory.decodeResource(context.resources, it,
            BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 }) }
        atlas?.recycle()
        val width: Float = sources.first().width.toFloat()
        // Python verifies that atlas cells retain the original complete canvases.
        // This renderer check verifies stride order, phase and camera on both sizes.
        val expected: HomeSpriteFrames = HomeSpriteFrames(sources.map { it to Rect(0, 0, it.width, it.height) },
            PointF(width / 2f, width * CorgiArtworkScale.ORIGINAL_GROUND), CorgiArtworkScale.ORIGINAL_CELL,
            List(4) { CorgiArtworkScale.originalFrame(10 + it) })
        val motion: CompanionMotion = CompanionMotion()
        val distance = CompanionMotion::class.java.getDeclaredField("distanceTravelled").apply { isAccessible = true }
        val actual: Bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val reference: Bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            for (size: Float in listOf(80f, 290f)) for (index: Int in 0..3) {
                // Midpoint of each quarter stride, away from floating-point boundaries.
                distance.setFloat(motion, size * .44f * (index + .5f) / 4f)
                val target: RectF = RectF(0f, 0f, size, size)
                actual.eraseColor(0); reference.eraseColor(0)
                locomotion.draw(Canvas(actual), Paint(), target, motion, false, "walk")
                expected.draw(Canvas(reference), Paint(), target, index)
                assertTrue("Stride contact $index must match the desktop at size $size", actual.sameAs(reference))
            }
        } finally { actual.recycle(); reference.recycle(); sources.forEach { it.recycle() } }
    }
}
