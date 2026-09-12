package com.pixelpals.app.feature.home

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.MainActivity
import com.pixelpals.app.R
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.database.CompanionExpeditionEntity
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies countdown refreshes preserve travel controls until the action set changes. */
@RunWith(AndroidJUnit4::class)
class AdventuresTravelRenderingTest {
    @Test fun countdownRefreshKeepsControlsAndStateChangesRebuildThem(): Unit = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dao = AppServices.companions(context).dao
        val previous = dao.getExpedition()
        val preferences = CompanionPreferences(context)
        val introduction: Boolean = preferences.hasSeenIntroduction
        preferences.hasSeenIntroduction = true
        val now = System.currentTimeMillis()
        val boot = SystemClock.uptimeMillis()
        val first = CompanionExpeditionEntity(requestId = "stable-travel-a", petId = "corgi", destination = "meadow",
            startedAt = now, lastWallTime = now, lastUptime = boot, bootCount = 0, elapsedMs = 1_000L)
        dao.saveExpedition(first)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { (it as RootNavigator).navigate(PixelPalsDestination.ADVENTURES) }
                val initial = awaitTravelViews(scenario, context.getString(R.string.adventure_remaining, 5))
                val initialCancel = initial.cancel
                val initialCountdown = initial.remaining
                scenario.onActivity {
                    initialCancel.isFocusableInTouchMode = true
                    assertTrue("Travel control should accept focus", initialCancel.requestFocus())
                }

                dao.saveExpedition(first.copy(elapsedMs = 60_000L, lastWallTime = now, lastUptime = boot))
                val refreshed = awaitTravelViews(scenario, context.getString(R.string.adventure_remaining, 4))
                assertSame("Countdown-only refresh must preserve cancel button", initialCancel, refreshed.cancel)
                assertSame("Countdown-only refresh must preserve countdown view", initialCountdown, refreshed.remaining)
                scenario.onActivity { assertTrue("Cancel control focus should survive refresh", refreshed.cancel.hasFocus()) }

                dao.saveExpedition(first.copy(elapsedMs = ExpeditionDestination.MEADOW.durationMs, lastWallTime = now, lastUptime = boot))
                val ready = awaitTravelViews(scenario, context.getString(R.string.adventure_ready_hint))
                assertNotNull("Ready journey must expose return action", ready.returnButton)
                assertNotSame("Ready transition must rebuild the action set", initialCancel, ready.cancel)

                val second = first.copy(requestId = "stable-travel-b", elapsedMs = 60_000L)
                dao.saveExpedition(second)
                val identityChanged = awaitTravelViews(scenario, context.getString(R.string.adventure_remaining, 4))
                assertNotSame("Journey identity change must rebuild controls", ready.cancel, identityChanged.cancel)
                assertNull("Pending journey must remove return action", identityChanged.returnButton)
            }
        } finally {
            dao.clearExpedition(first.requestId)
            dao.clearExpedition("stable-travel-b")
            if (previous != null) dao.saveExpedition(previous)
            preferences.hasSeenIntroduction = introduction
        }
    }

    private data class TravelViews(val cancel: Button, val remaining: TextView?, val returnButton: Button?)

    private suspend fun awaitTravelViews(scenario: ActivityScenario<MainActivity>, remainingText: String): TravelViews {
        repeat(100) {
            var result: TravelViews? = null
            scenario.onActivity { activity ->
                val buttons = mutableListOf<Button>()
                val texts = mutableListOf<TextView>()
                collect(activity.window.decorView, buttons, texts)
                val cancel = buttons.firstOrNull { it.text == activity.getString(R.string.adventure_cancel) }
                val countdown = texts.firstOrNull { it.text == remainingText }
                val returnButton = buttons.firstOrNull { it.text == activity.getString(R.string.adventure_return) }
                if (cancel != null && countdown != null) result = TravelViews(cancel, countdown, returnButton)
            }
            if (result != null) return result!!
            delay(50)
        }
        throw AssertionError("Timed out waiting for travel card text: $remainingText")
    }

    private fun collect(view: View, buttons: MutableList<Button>, texts: MutableList<TextView>) {
        if (view is Button) buttons += view
        if (view is TextView && view !is Button) texts += view
        if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index), buttons, texts)
    }
}
