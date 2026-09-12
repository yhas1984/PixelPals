package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Inspects the real Canvas transforms, independent of atlas dimensions and poses. */
@RunWith(AndroidJUnit4::class)
class HomeRigidAnatomyTest {
    @Test fun homeAnticipationAndBreathingDoNotStretchRigidSpecies(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences: CompanionPreferences = CompanionPreferences(context)
        val previous: Boolean = preferences.reducedMotion
        val bitmap: Bitmap = Bitmap.createBitmap(500, 380, Bitmap.Config.ARGB_8888)
        val soft: Set<PetType> = setOf(PetType.BLOOP, PetType.NUBE_MICHI, PetType.JELLY)
        try {
            preferences.reducedMotion = false
            for (pet: PetType in PetType.entries) instrumentation.runOnMainSync {
                val scene: HomeSceneView = HomeSceneView(context).apply { reviewSeed = 42 }
                runBlocking { scene.loadPet(pet) }
                scene.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(380, View.MeasureSpec.EXACTLY))
                scene.layout(0, 0, 500, 380)
                scene.requestMotionReview(CompanionIntent.EXPLORE)
                val canvas: ScaleRecordingCanvas = ScaleRecordingCanvas(bitmap)
                repeat(12) {
                    scene.advanceScene(50)
                    scene.draw(canvas)
                }
                if (pet in soft) assertTrue("$pet should retain its soft secondary motion", canvas.deformed)
                else assertFalse("$pet must use its drawings rather than stretch its anatomy", canvas.deformed)
                scene.pause()
            }
        } finally {
            preferences.reducedMotion = previous
            bitmap.recycle()
        }
    }

    private class ScaleRecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        var deformed: Boolean = false
        override fun scale(sx: Float, sy: Float): Unit {
            // Uniform scene fitting and direction mirroring preserve anatomy.
            if (abs(abs(sx) - abs(sy)) > .0001f) deformed = true
            super.scale(sx, sy)
        }
    }
}
