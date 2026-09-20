package com.pixelpals.app.feature.home

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.lifecycle.ViewModelProvider
import com.pixelpals.app.CompanionSettingsActivity
import com.pixelpals.app.R
import com.pixelpals.app.feature.care.CareScenePanel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real home controls must reveal the scene they open from below the fold. */
class HomeContentVisibilityTest {
    @Test fun decoratingFromBelowTheRoomRevealsThePlacementScene(): Unit = withHome { scenario, fragment ->
        scrollToBottom(scenario, fragment)
        scenario.onActivity {
            val scene: View = fragment.requireView().findViewById(R.id.companionScene)
            assertFalse("Fixture starts with the room outside the viewport", scene.getGlobalVisibleRect(Rect()))
            descendants(fragment.requireView()).filterIsInstance<Button>()
                .single { it.text == it.context.getString(R.string.home_decorate) }.performClick()
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        assertRevealed(scenario, fragment) { it.findViewById(R.id.companionScene) }
    }

    @Test fun careFromBelowTheRoomRevealsItsPanel(): Unit = withHome { scenario, fragment ->
        scrollToBottom(scenario, fragment)
        scenario.onActivity { fragment.requireView().findViewById<Button>(R.id.companionCare).performClick() }
        assertRevealed(scenario, fragment) { root -> descendants(root).filterIsInstance<CareScenePanel>().single() }
    }

    private fun withHome(block: (ActivityScenario<CompanionSettingsActivity>, LivingHomeFragment) -> Unit): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences = CompanionPreferences(instrumentation.targetContext)
        val reduced: Boolean = preferences.reducedMotion
        val introduced: Boolean = preferences.hasSeenIntroduction
        preferences.reducedMotion = true
        preferences.hasSeenIntroduction = true
        try {
            ActivityScenario.launch(CompanionSettingsActivity::class.java).use { scenario ->
                val fragment = LivingHomeFragment()
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.companionSettings, fragment).commitNow()
                }
                var ready: Boolean = false
                val deadline: Long = SystemClock.elapsedRealtime() + 5_000L
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity {
                        val model = ViewModelProvider(fragment)[CompanionViewModel::class.java]
                        val scroll = fragment.requireView() as ScrollView
                        ready = model.world.value.home != null && model.status.value != null &&
                            scroll.getChildAt(0).height > scroll.height
                    }
                    if (!ready) SystemClock.sleep(40)
                }
                assertTrue("Home data and layout must be ready before the interaction", ready)
                // Keep the room genuinely below the fold on tall API 35
                // displays. Without this tail, scrolling to the measured
                // child height can clamp while the scene is still visible.
                scenario.onActivity { activity ->
                    val scroll = fragment.requireView() as ScrollView
                    val content = scroll.getChildAt(0) as ViewGroup
                    content.addView(View(activity), ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (scroll.height * 2).coerceAtLeast(1_200),
                    ))
                }
                instrumentation.waitForIdleSync()
                block(scenario, fragment)
            }
        } finally {
            preferences.reducedMotion = reduced
            preferences.hasSeenIntroduction = introduced
        }
    }

    private fun scrollToBottom(scenario: ActivityScenario<CompanionSettingsActivity>, fragment: LivingHomeFragment): Unit {
        scenario.onActivity {
            val scroll = fragment.requireView() as ScrollView
            scroll.scrollTo(0, scroll.getChildAt(0).height)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun assertRevealed(scenario: ActivityScenario<CompanionSettingsActivity>, fragment: LivingHomeFragment,
                               target: (View) -> View): Unit {
        var revealed: Boolean = false
        val deadline: Long = SystemClock.elapsedRealtime() + 5_000L
        while (!revealed && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity {
                val scroll: View = fragment.requireView()
                val child: View = target(scroll)
                val viewport = Rect()
                val visible = Rect()
                val location = IntArray(2)
                child.getLocationOnScreen(location)
                revealed = scroll.getGlobalVisibleRect(viewport) && child.getGlobalVisibleRect(visible) &&
                    location[1] >= viewport.top && location[1] <= viewport.top + 2
            }
            if (!revealed) SystemClock.sleep(40)
        }
        assertTrue("The opened scene starts at the visible top of the home", revealed)
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index: Int in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
