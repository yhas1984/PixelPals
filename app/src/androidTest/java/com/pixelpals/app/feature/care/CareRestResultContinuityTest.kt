package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixelpals.app.core.ads.AppOpenAdController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.PetStatusEntity
import com.pixelpals.app.status.PetDashboardActivity
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.pixelpals.app.data.prefs.SelectedPetStore
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class CareRestResultContinuityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val coordinator = AppServices.careScenes(context)

    @Before fun requireDisposableEmulator(): Unit = assumeTrue(
        "Care panel test requires an emulator", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")

    @Test fun completedRestKeepsFinalStageUntilNextActionAndCancelStopsIt(): Unit = runCompletedActionContinuity(CareSceneAction.REST)

    @Test fun completedMedicineKeepsFinalStageUntilNextActionAndCancelStopsIt(): Unit = runBlocking {
        val database: AppDatabase = AppDatabase.getDatabase(context)
        val statusDao = database.petStatusDao()
        val previous: PetStatusEntity? = statusDao.getByPetId("jelly")
        try {
            statusDao.upsert((previous ?: PetStatusEntity("jelly")).copy(
                energy = 15, satiety = 15, condition = "SICK", lastMedicineAt = 0L,
                lastUpdatedAt = System.currentTimeMillis()))
            runCompletedActionContinuity(CareSceneAction.MEDICINE)
        } finally {
            previous?.let { statusDao.upsert(it) }
        }
    }

    private fun runCompletedActionContinuity(action: CareSceneAction): Unit {
        val selection = SelectedPetStore(context)
        val previousPet = selection.load()
        val resultCount = AtomicInteger()
        val finalImage = AtomicReference<Bitmap>()
        try {
            selection.save(PetType.JELLY)
            ActivityScenario.launch(PetDashboardActivity::class.java).use { activity ->
                // Exercise care after dismissing any opening test ad, just as a user would.
                activity.onActivity { AppOpenAdController.getInstance(context).onUserInteraction() }
                if (AppOpenAdController.getInstance(context).isShowingAd) {
                    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    val closeAd = device.wait(Until.findObject(By.desc("Interstitial close button")), 15_000L)
                    requireNotNull(closeAd) { "Opening test ad did not expose its close button" }.click()
                }
                await { stage(activity)?.pack != null && coordinator.session.value == null }
                activity.onActivity { current ->
                    val panel = requireNotNull(findPanel(current.window.decorView))
                    val originalCallback = panel.onResult
                    panel.onResult = { result ->
                        if (result is CareSceneResult.Completed) {
                            resultCount.incrementAndGet()
                            finalImage.set(requireNotNull(findStage(current.window.decorView)).captureFrame())
                        }
                        originalCallback?.invoke(result)
                    }
                }
                tool(action).check(matches(isEnabled())).perform(click())
                await { coordinator.session.value?.request?.action == action }
                // API 35 can deliver the final frame on a delayed choreographer tick
                // when the full suite has disabled/paused animations.  Observe the
                // result first, then allow the panel's asynchronous cancel/refresh
                // to publish the detached session state.
                await(30_000L) { resultCount.get() == 1 }
                await(5_000L) { coordinator.session.value == null }
                // Let the session-null emission and normal idle frames render.
                Thread.sleep(300L)
                var actionController: CareSceneController? = null
                activity.onActivity { current ->
                    val view = requireNotNull(findStage(current.window.decorView))
                    actionController = view.readController()
                    assertEquals(action, actionController?.action)
                    assertTrue("$action must be complete", actionController?.isComplete == true)
                    val afterRelease = view.captureFrame()
                    try {
                        assertTrue("result must preserve the final pose and position", finalImage.get().sameAs(afterRelease))
                    } finally { afterRelease.recycle() }
                }
                tool(CareSceneAction.FEED).perform(click())
                await { coordinator.session.value?.request?.action == CareSceneAction.FEED }
                activity.onActivity { current ->
                    val next = requireNotNull(findStage(current.window.decorView)).readController()
                    assertEquals(CareSceneAction.FEED, next?.action)
                    assertNotSame("new action must replace the retained stage", actionController, next)
                }
                onView(withText(context.getString(R.string.care_scene_close))).perform(click())
                await(5_000L) { coordinator.session.value == null }
                activity.onActivity { current ->
                    assertNull("cancel must clear the stage", requireNotNull(findStage(current.window.decorView)).readController())
                }
                assertEquals("$action callback must not repeat", 1, resultCount.get())
            }
        } finally {
            selection.save(previousPet)
            finalImage.get()?.recycle()
        }
    }

    @Test fun finishedManualStageDoesNotCaptureNewGestures(): Unit {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = CareStageView(context)
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
            try {
                val scene = CareSceneController(CareSceneAction.REST, CareSceneMode.MANUAL, CareSceneTiming(100L, 50L))
                view.start(scene)
                assertTrue("active manual stage accepts gestures", view.onTouchEvent(event))
                scene.movePointer(CarePoint(.5f, .5f), CarePoint(.5f, .5f), true)
                scene.advance(100L)
                scene.advance(1L)
                assertTrue(scene.isComplete)
                assertFalse("completed stage must release gestures", view.onTouchEvent(event))
                val cancelled = CareSceneController(CareSceneAction.REST, CareSceneMode.MANUAL, CareSceneTiming(100L, 50L))
                view.start(cancelled)
                cancelled.cancel()
                assertFalse("cancelled stage must release gestures", view.onTouchEvent(event))
            } finally { event.recycle(); view.stop() }
        }
    }

    private fun tool(action: CareSceneAction) = onView(allOf(
        isAssignableFrom(Button::class.java),
        withText(context.getString(CareScenePanel.label(action))), isDisplayed()))

    private fun stage(activity: ActivityScenario<PetDashboardActivity>): CareStageView? {
        var found: CareStageView? = null
        activity.onActivity { found = findStage(it.window.decorView) }
        return found
    }

    private fun findPanel(view: View): CareScenePanel? {
        if (view is CareScenePanel) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPanel(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun findStage(view: View): CareStageView? {
        if (view is CareStageView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findStage(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun CareStageView.readController(): CareSceneController? {
        val field = CareStageView::class.java.getDeclaredField("controller").apply { isAccessible = true }
        return field.get(this) as? CareSceneController
    }

    private fun CareStageView.captureFrame(): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    private fun await(timeoutMs: Long = 10_000L, predicate: () -> Boolean): Unit {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50L)
        assertTrue("condition was not reached", predicate())
    }
}
