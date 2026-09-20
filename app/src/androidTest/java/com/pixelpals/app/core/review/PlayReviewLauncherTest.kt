package com.pixelpals.app.core.review

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.review.testing.FakeReviewManager
import com.pixelpals.app.MainActivity
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayReviewLauncherTest {
    @Test
    fun fakeManagerCompletesEligibleReviewFlowAndRecordsAttempt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stateStore = RecordingStateStore()
        val now = 1_800_000_000_000L
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                PlayReviewLauncher(
                    context = context,
                    stateStore = stateStore,
                    manager = FakeReviewManager(context),
                    versionCode = 24,
                    clock = { now },
                ).maybeLaunch(
                    activity,
                    ReviewPromptInput(
                        moment = ReviewMoment.EXPEDITION_REWARD,
                        adoptedAt = now - TimeUnit.DAYS.toMillis(8),
                        bond = 20,
                        careStreakDays = 3,
                        eventAt = now,
                    ),
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        assertEquals(1, stateStore.read().requestCount)
        assertEquals(24L, stateStore.read().lastVersionCode)
    }

    private class RecordingStateStore : ReviewPromptStateStore {
        private var state = ReviewPromptState()
        override fun read(): ReviewPromptState = state
        override fun recordRequest(at: Long, versionCode: Long) {
            state = ReviewPromptState(at, versionCode, state.requestCount + 1)
        }
    }
}
