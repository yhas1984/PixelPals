package com.pixelpals.app.feature.care

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetStatusSnapshot
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real desktop controller and assets, isolated fake effects: never touches the user's database. */
@RunWith(AndroidJUnit4::class)
class CorgiDesktopCarePlaybackTest {
    private val snapshot: PetStatusSnapshot = PetStatusSnapshot("corgi", 90, 50, 50, 50, 30,
        PetMood.HAPPY, 1, 10, CareAction.FEED, 1)

    @Test fun everyDesktopActionFinishesAndCommitsExactlyOnce(): Unit = runBlocking {
        for (action: CareSceneAction in CareSceneAction.entries) {
            val status: PetStatusSnapshot = if (action == CareSceneAction.MEDICINE)
                snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = 0L) else snapshot
            val result: PlaybackResult = play(action, status)
            assertEquals("$action", 1, result.effects)
            assertEquals(1, result.finished)
            assertTrue(result.outcome is CareSceneResult.Completed)
            assertTrue(result.renderedFrames > 0)
        }
    }

    @Test fun unavailableMedicineNeverPlaysOrAppliesAnyEffect(): Unit = runBlocking {
        for (status: PetStatusSnapshot in listOf(snapshot,
            snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = Long.MAX_VALUE))) {
            val result: PlaybackResult = play(CareSceneAction.MEDICINE, status)
            assertEquals(0, result.effects)
            assertEquals(1, result.finished)
            assertEquals(0, result.renderedFrames)
            assertEquals(CareSceneResult.Unavailable, result.outcome)
        }
    }

    @Test fun cancellingAnyDesktopActionRemovesPlaybackWithoutCommitting(): Unit = runBlocking {
        for (action: CareSceneAction in CareSceneAction.entries) {
            val status: PetStatusSnapshot = snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = 0L)
            val result: PlaybackResult = play(action, status, cancel = true)
            assertEquals("$action", 0, result.effects)
            assertEquals(1, result.finished)
            assertNull(result.outcome)
        }
    }

    private suspend fun play(action: CareSceneAction, status: PetStatusSnapshot, cancel: Boolean = false): PlaybackResult =
        withContext(Dispatchers.Main) {
            val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            var effects: Int = 0
            var finished: Int = 0
            var renderedFrames: Int = 0
            var outcome: CareSceneResult? = null
            var lastFetchFrame: CorgiFetchFrame? = null
            val bitmap: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            val canvas: Canvas = Canvas(bitmap)
            val coordinator: CareSceneCoordinator = CareSceneCoordinator(scope, { status }, {
                effects++; CareSceneResult.Completed(status, status)
            })
            val care: CorgiDesktopCare = CorgiDesktopCare(
                InstrumentationRegistry.getInstrumentation().targetContext, scope,
                { lastFetchFrame = it }, coordinator,
            ) { _, result -> finished++; outcome = result }
            try {
                val fetch: CorgiFetchPlan? = if (action == CareSceneAction.PLAY) CorgiFetchMotion.createPlan(
                    CarePoint(0f, 600f), PetBounds(0, 800, 0, 600), 160, false, false) else null
                care.start(action, false, fetch)
                withTimeout(10_000L) {
                    while (care.isActive && coordinator.session.value == null) delay(1L)
                }
                yield()
                if (cancel) { care.advance(.2f); care.cancel() }
                withTimeout(10_000L) {
                    while (care.isActive) {
                        care.advance(.1f)
                        if (care.draw(canvas, 160)) renderedFrames++
                        delay(1L)
                    }
                }
                repeat(5) { care.advance(1f) }
                assertNull(lastFetchFrame)
                PlaybackResult(effects, finished, renderedFrames, outcome)
            } finally {
                care.cancel()
                scope.cancel()
                bitmap.recycle()
            }
        }

    private data class PlaybackResult(val effects: Int, val finished: Int, val renderedFrames: Int,
                                      val outcome: CareSceneResult?)
}
