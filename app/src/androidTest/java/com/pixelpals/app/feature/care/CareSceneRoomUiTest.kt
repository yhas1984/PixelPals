package com.pixelpals.app.feature.care

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.status.PetDashboardActivity
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Real room smoke tests are emulator-only: opening the room performs daily check-in. */
@RunWith(AndroidJUnit4::class)
class CareSceneRoomUiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val coordinator = AppServices.careScenes(context)

    @Before fun requireDisposableEmulator(): Unit {
        assumeTrue("Never exercise installed personal care state", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
    }

    @Test fun tapRunsAutomaticSceneAndLeavingRoomReleasesOwnership(): Unit {
        ActivityScenario.launch(PetDashboardActivity::class.java).use { activity ->
            awaitReady(activity)
            tool(CareSceneAction.FEED).perform(click())
            await { coordinator.session.value?.request?.mode == CareSceneMode.AUTOMATIC }
            Thread.sleep(900L)
            capture("room-feed")
            await { coordinator.session.value?.result is CareSceneResult.Completed }
            await { coordinator.session.value == null }
            Thread.sleep(200L)
            capture("room-result")
            assertTrue(coordinator.roomOwners.value.isNotEmpty())
            activity.moveToState(Lifecycle.State.CREATED)
            await { coordinator.roomOwners.value.isEmpty() }
            activity.moveToState(Lifecycle.State.RESUMED)
            awaitReady(activity)
            onView(withText(context.getString(com.pixelpals.app.R.string.care_scene_hint)))
                .check(matches(isDisplayed()))
        }
    }

    @Test fun dragFromToolReachesMouthWithoutLosingInitialInput(): Unit {
        ActivityScenario.launch(PetDashboardActivity::class.java).use { activity ->
            awaitReady(activity)
            tool(CareSceneAction.FEED).perform(GeneralSwipeAction(Swipe.SLOW,
                { view -> val position = IntArray(2); view.getLocationOnScreen(position); floatArrayOf(position[0] + view.width / 2f, position[1] + view.height / 2f) },
                { view ->
                    val stage: CareStageView = findStage(view.rootView)!!
                    val pack: CarePosePack = stage.pack!!
                    val scene: CareSceneController = CareSceneController(CareSceneAction.FEED, CareSceneMode.MANUAL, pack.spec.timings.getValue(CareSceneAction.FEED))
                    val target: CarePoint = CareSceneRenderer().getTarget(pack, scene, stage.width.toFloat(), stage.height.toFloat())
                    val position: IntArray = IntArray(2); stage.getLocationOnScreen(position)
                    floatArrayOf(position[0] + target.x * stage.width, position[1] + target.y * stage.height)
                }, Press.FINGER))
            await { coordinator.session.value?.request?.mode == CareSceneMode.MANUAL }
            await { coordinator.session.value?.result is CareSceneResult.Completed }
        }
    }

    @Test fun recreationCancelsUncommittedSceneAndDoesNotReplay(): Unit {
        ActivityScenario.launch(PetDashboardActivity::class.java).use { activity ->
            awaitReady(activity)
            tool(CareSceneAction.REST).perform(click())
            await { coordinator.session.value?.phase == CareScenePhase.READY }
            activity.recreate()
            await { coordinator.session.value == null }
            awaitReady(activity)
            assertNull(coordinator.session.value)
        }
    }

    private fun tool(action: CareSceneAction) = onView(allOf(isAssignableFrom(Button::class.java), withText(context.getString(CareScenePanel.label(action))), isDisplayed()))

    private fun awaitReady(activity: ActivityScenario<PetDashboardActivity>): Unit = await {
        var ready: Boolean = false
        activity.onActivity { current ->
            val stage: CareStageView? = findStage(current.window.decorView)
            ready = stage?.pack != null && coordinator.session.value == null
        }
        ready
    }

    private fun findStage(view: View): CareStageView? {
        if (view is CareStageView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findStage(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun await(predicate: () -> Boolean): Unit {
        val deadline: Long = SystemClock.elapsedRealtime() + 10_000L
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50L)
        assertTrue("UI condition was not reached", predicate())
    }

    private fun capture(name: String): Unit {
        val directory: File = requireNotNull(context.getExternalFilesDir("care-ui"))
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(directory, "$name.png"))
    }
}
