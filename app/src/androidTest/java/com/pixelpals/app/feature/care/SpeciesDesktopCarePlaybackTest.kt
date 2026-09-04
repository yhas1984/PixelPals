package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneCoordinator
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetStatusSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Safe species desktop playback checks: fake effects, offscreen canvas, no database access. */
@RunWith(AndroidJUnit4::class)
class SpeciesDesktopCarePlaybackTest {
    private val speciesPets: List<PetType> = PetType.entries.filter { it != PetType.CORGI }

    @Test
    fun everySpeciesDesktopActionRendersAndCommitsExactlyOnce(): Unit = runBlocking {
        for (pet: PetType in speciesPets) {
            for (action: CareSceneAction in CareSceneAction.entries) {
                val baseline: PetStatusSnapshot = createSnapshot(pet)
                val status: PetStatusSnapshot = if (action == CareSceneAction.MEDICINE) {
                    baseline.copy(condition = PetCondition.SICK, medicineAvailableAt = 0L)
                } else {
                    baseline
                }
                val result: PlaybackResult = play(pet, action, status)
                assertEquals("$pet ${action.name}", 1, result.effects)
                assertEquals("$pet ${action.name}", 1, result.finished)
                assertTrue("$pet ${action.name}", result.outcome is CareSceneResult.Completed)
                assertTrue("$pet ${action.name}", result.renderedFrames > 0)
            }
        }
    }

    @Test
    fun unavailableMedicineAndCancellationNeverApplyCare(): Unit = runBlocking {
        for (pet: PetType in speciesPets) {
            val unavailable: PlaybackResult = play(pet, CareSceneAction.MEDICINE, createSnapshot(pet))
            assertEquals(pet.name, 0, unavailable.effects)
            assertEquals(pet.name, 0, unavailable.renderedFrames)
            assertEquals(pet.name, CareSceneResult.Unavailable, unavailable.outcome)
            val cancelled: PlaybackResult = play(pet, CareSceneAction.PLAY, createSnapshot(pet), cancel = true)
            assertEquals(pet.name, 0, cancelled.effects)
            assertEquals(pet.name, 1, cancelled.finished)
            assertNull(pet.name, cancelled.outcome)
        }
        val baseline: PetStatusSnapshot = createSnapshot(PetType.DIABLILLO)
        for (action: CareSceneAction in CareSceneAction.entries) {
            val status: PetStatusSnapshot = baseline.copy(condition = PetCondition.SICK, medicineAvailableAt = 0L)
            val cancelled: PlaybackResult = play(PetType.DIABLILLO, action, status, cancel = true)
            assertEquals(action.name, 0, cancelled.effects)
            assertEquals(1, cancelled.finished)
            assertNull(cancelled.outcome)
        }
    }

    @Test
    fun desktopRendererSupportsBothDirectionsAndReducedMotion(): Unit = runBlocking {
        val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
        val bitmap: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        try {
            for (pet: PetType in speciesPets) {
                val pack: CarePosePack = CarePoseLoader.load(
                    InstrumentationRegistry.getInstrumentation().targetContext.assets,
                    pet,
                )
                try {
                    for (action: CareSceneAction in CareSceneAction.entries) {
                        val timing = pack.spec.timings.getValue(action)
                        for (reduced: Boolean in listOf(false, true)) {
                            val scene = com.pixelpals.app.core.care.scene.CareSceneController(
                                action,
                                com.pixelpals.app.core.care.scene.CareSceneMode.AUTOMATIC,
                                timing,
                            )
                            scene.advance(timing.durationMs / 2L)
                            for (facingLeft: Boolean in listOf(false, true)) {
                                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                                val canvas: Canvas = Canvas(bitmap)
                                canvas.save()
                                if (facingLeft) canvas.scale(-1f, 1f, 160f, 160f)
                                renderer.draw(canvas, pack, scene, reduced, gentle = false, desktopSize = 160)
                                canvas.restore()
                                assertFalse("$pet $action reduced=$reduced left=$facingLeft", bitmap.isAllTransparent())
                            }
                        }
                    }
                } finally {
                    pack.bitmap.recycle()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun play(
        pet: PetType,
        action: CareSceneAction,
        status: PetStatusSnapshot,
        cancel: Boolean = false,
    ): PlaybackResult = withContext(Dispatchers.Main) {
        val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var effects: Int = 0
        var finished: Int = 0
        var renderedFrames: Int = 0
        var outcome: CareSceneResult? = null
        val bitmap: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val coordinator: CareSceneCoordinator = CareSceneCoordinator(scope, { status }, {
            effects++
            CareSceneResult.Completed(status, status)
        })
        val care: SpeciesDesktopCare = SpeciesDesktopCare(
            InstrumentationRegistry.getInstrumentation().targetContext,
            scope,
            pet,
            coordinator,
        ) { _, result: CareSceneResult? ->
            finished++
            outcome = result
        }
        try {
            care.start(action, facingLeft = false)
            withTimeout(10_000L) {
                while (care.isActive && coordinator.session.value == null) delay(1L)
            }
            yield()
            if (cancel) {
                care.advance(.2f)
                care.cancel()
            }
            withTimeout(10_000L) {
                while (care.isActive) {
                    care.advance(.1f)
                    if (care.draw(Canvas(bitmap), 160)) renderedFrames++
                    delay(1L)
                }
            }
            repeat(5) { care.advance(1f) }
            PlaybackResult(effects, finished, renderedFrames, outcome)
        } finally {
            care.cancel()
            scope.cancel()
            bitmap.recycle()
        }
    }

    private fun createSnapshot(pet: PetType): PetStatusSnapshot = PetStatusSnapshot(
        pet.name.lowercase(), 90, 50, 50, 50, 30,
        PetMood.HAPPY, 1, 10, CareAction.FEED, 1,
    )

    private fun Bitmap.isAllTransparent(): Boolean {
        val pixels: IntArray = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.all { android.graphics.Color.alpha(it) == 0 }
    }

    private data class PlaybackResult(
        val effects: Int,
        val finished: Int,
        val renderedFrames: Int,
        val outcome: CareSceneResult?,
    )
}
