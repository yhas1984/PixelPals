package com.pixelpals.app.feature.home

import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.debug.HomeMotionPreviewActivity
import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/** Verifies a paused scene stays paused across async loading, editing, and reattachment. */
@RunWith(AndroidJUnit4::class)
class HomeScenePauseLifecycleTest {
    private var originalReducedMotion: Boolean = false
    @org.junit.Before fun enableMotionForAnimationChecks(): Unit {
        val preferences = CompanionPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        originalReducedMotion = preferences.reducedMotion
        preferences.reducedMotion = false
    }
    @org.junit.After fun restoreMotionPreference(): Unit {
        CompanionPreferences(InstrumentationRegistry.getInstrumentation().targetContext).reducedMotion = originalReducedMotion
    }
    @Test fun pausedSceneDoesNotAdvanceUntilExplicitResume(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE == "ranchu")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var scenario: ActivityScenario<HomeMotionPreviewActivity>
        lateinit var scene: HomeSceneView
        scenario = ActivityScenario.launch(HomeMotionPreviewActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                scene = HomeSceneView(activity)
                content.addView(scene, FrameLayout.LayoutParams(320, 240))
                runBlocking { scene.loadPet(PetType.GINGER) }
                scene.pause()
            }
            val pausedAt = activeTime(scenario, scene)

            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                content.removeView(scene)
                content.addView(scene, FrameLayout.LayoutParams(320, 240))
                scene.visibility = android.view.View.GONE
                scene.visibility = android.view.View.VISIBLE
                scene.isEditing = true
                scene.isEditing = false
                runBlocking { scene.loadPet(PetType.GINGER) }
            }
            Thread.sleep(300L)
            assertEquals("Paused scene advanced after lifecycle/loading changes", pausedAt, activeTime(scenario, scene))
            assertTrue("Paused scene scheduled a tick", !running(scenario, scene))

            scenario.onActivity { scene.resume() }
            Thread.sleep(300L)
            assertTrue("Resumed scene did not advance active time", activeTime(scenario, scene) > pausedAt)
        } finally {
            scenario.onActivity { activity ->
                (scene.parent as? ViewGroup)?.removeView(scene)
                scene.pause()
            }
            scenario.close()
        }
    }

    private fun activeTime(scenario: ActivityScenario<HomeMotionPreviewActivity>, scene: HomeSceneView): Long {
        var value = 0L
        scenario.onActivity {
            value = HomeSceneView::class.java.getDeclaredField("activeTime").apply { isAccessible = true }.getLong(scene)
        }
        return value
    }

    private fun running(scenario: ActivityScenario<HomeMotionPreviewActivity>, scene: HomeSceneView): Boolean {
        var value = false
        scenario.onActivity {
            value = HomeSceneView::class.java.getDeclaredField("running").apply { isAccessible = true }.getBoolean(scene)
        }
        return value
    }
}
