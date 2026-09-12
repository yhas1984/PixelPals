package com.pixelpals.app.feature.treasure

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.TreasureItem
import com.pixelpals.app.status.PetDashboardActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreasureAlbumUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val database: AppDatabase = AppDatabase.getDatabase(context)
    private var albumScenario: ActivityScenario<TreasureAlbumActivity>? = null
    private var dashboardScenario: ActivityScenario<PetDashboardActivity>? = null

    @Before
    fun setUp(): Unit = runBlocking {
        database.clearAllTables()
        SelectedPetStore(context).apply {
            save(PetType.CORGI)
            setPetEnabled(true)
        }
    }

    @After
    fun tearDown(): Unit {
        albumScenario?.close()
        dashboardScenario?.close()
    }

    @Test
    fun albumAlwaysShowsNineteenSlotsAndConfirmsFavoriteGift(): Unit = runBlocking {
        database.treasureDao().insertTreasure(TreasureItem("🦴", 1, 100L, 100L, 1))
        albumScenario = ActivityScenario.launch(TreasureAlbumActivity::class.java)
        awaitRecyclerItemCount(19)
        onView(withText(context.getString(R.string.treasure_collection_progress, 1, 19)))
            .check(matches(isDisplayed()))
        albumScenario!!.onActivity { activity ->
            val recyclerView: RecyclerView = activity.findViewById(R.id.recyclerViewTreasures)
            recyclerView.findViewHolderForAdapterPosition(2)?.itemView?.performClick()
        }
        onView(withText(containsString(context.getString(R.string.treasure_name_lucky_bone))))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText(containsString(context.getString(R.string.treasure_gift_reward_favorite))))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        awaitTreasureCount("🦴", 0)
        assertEquals(5, database.petBondDao().getByPetId("corgi")?.bondPoints)
        assertEquals(1, database.treasureDao().getTreasure("🦴")?.totalFound)
    }

    @Test
    fun spentTreasureStillOpensItsStoryWithoutOfferingAnotherGift(): Unit = runBlocking {
        database.treasureDao().insertTreasure(TreasureItem("🦴", 0, 100L, 100L, 1))
        albumScenario = ActivityScenario.launch(TreasureAlbumActivity::class.java)
        awaitRecyclerItemCount(19)
        albumScenario!!.onActivity { activity ->
            val recycler = activity.findViewById<RecyclerView>(R.id.recyclerViewTreasures)
            recycler.findViewHolderForAdapterPosition(2)?.itemView?.performClick()
        }
        val story = context.getString(R.string.treasure_story_lucky_bone)
        onView(withText(story)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        assertEquals(0, database.treasureDao().getTreasure("🦴")?.count)
        assertEquals(1, database.treasureDao().getTreasure("🦴")?.totalFound)
    }

    @Test
    fun dashboardExposesCollectionCardAndAlbumAction(): Unit = runBlocking {
        database.treasureDao().insertTreasure(TreasureItem("🦴", 1, 100L, 100L, 1))
        database.treasureDao().insertTreasure(TreasureItem("🍀", 1, 100L, 100L, 1))
        dashboardScenario = ActivityScenario.launch(PetDashboardActivity::class.java)
        var albumOpened = false
        try {
            awaitDashboardCollectionReady(context.getString(R.string.treasure_collection_progress, 2, 19))
            scrollToDashboardCollectionWithGestures()
            onView(withId(R.id.btnDashboardCollection)).check(matches(isDisplayingAtLeast(90))).perform(click())
            albumOpened = true
            awaitOpenedAlbumReady(context.getString(R.string.treasure_collection_progress, 2, 19))
            onView(withText(context.getString(R.string.treasure_collection_progress, 2, 19)))
                .check(matches(isDisplayed()))
            onView(withId(R.id.recyclerViewTreasures)).check(matches(isDisplayed()))
        } finally {
            if (albumOpened) runCatching { pressBack() }
        }
    }

    @Test
    fun giftRefreshFailureKeepsCollectionVisibleAndOffersRetry(): Unit {
        albumScenario = ActivityScenario.launch(TreasureAlbumActivity::class.java)
        awaitRecyclerItemCount(19)
        albumScenario!!.onActivity { activity ->
            val collection = TreasureCollection(
                summary = TreasureCollectionSummary(
                    discoveredCount = 1, totalCount = 19, badge = TreasureBadge.NONE,
                    nextMilestone = 5, nextRewardCoins = 25, isPetActive = true,
                    hasGiftedToday = false, currentBond = 0,
                ),
                items = emptyList(),
            )
            TreasureAlbumActivity::class.java.getDeclaredMethod(
                "renderState", TreasureAlbumUiState::class.java,
            ).apply { isAccessible = true }.invoke(
                activity,
                TreasureAlbumUiState(isLoading = false, collection = collection, hasError = true),
            )
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.cardCollectionSummary).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recyclerViewTreasures).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnAlbumRetry).visibility)
            assertEquals(activity.getString(R.string.treasure_album_error),
                activity.findViewById<android.widget.TextView>(R.id.tvAlbumState).text.toString())
            val render = TreasureAlbumActivity::class.java.getDeclaredMethod(
                "renderState", TreasureAlbumUiState::class.java,
            ).apply { isAccessible = true }
            render.invoke(activity, TreasureAlbumUiState(isLoading = true, collection = collection))
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recyclerViewTreasures).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pbAlbumLoading).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.btnAlbumRetry).visibility)
            render.invoke(activity, TreasureAlbumUiState(isLoading = false, collection = collection))
            assertEquals(View.GONE, activity.findViewById<View>(R.id.pbAlbumLoading).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.btnAlbumRetry).visibility)
            assertEquals(activity.getString(R.string.treasure_album_ready),
                activity.findViewById<android.widget.TextView>(R.id.tvAlbumState).text.toString())
        }
    }

    private fun scrollToDashboardCollectionWithGestures(): Unit {
        // Exercise the user's scroll path. Espresso scrollTo aligns an off-screen
        // descendant once; asynchronous dashboard reflow can invalidate that position.
        for (attempt in 0 until 12) {
            var visible = false
            dashboardScenario!!.onActivity { activity ->
                visible = isDisplayingAtLeast(90).matches(activity.findViewById<android.widget.Button>(R.id.btnDashboardCollection))
            }
            if (visible) return
            onView(withId(R.id.dashboardScroll)).perform(swipeUp())
        }
        throw AssertionError("Dashboard collection button was not reachable by scrolling")
    }

    private fun awaitRecyclerItemCount(expected: Int): Unit {
        val deadline: Long = System.currentTimeMillis() + UI_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            var itemCount: Int = 0
            albumScenario!!.onActivity { activity ->
                itemCount = activity.findViewById<RecyclerView>(R.id.recyclerViewTreasures).adapter?.itemCount ?: 0
            }
            if (itemCount == expected) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Album did not show $expected treasure slots")
    }

    private fun awaitTreasureCount(emoji: String, expected: Int): Unit {
        val deadline: Long = System.currentTimeMillis() + UI_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val count: Int? = runBlocking { database.treasureDao().getTreasure(emoji)?.count }
            if (count == expected) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Treasure $emoji did not reach count $expected")
    }

    private fun awaitDashboardCollectionReady(expectedText: String): Unit {
        val deadline: Long = System.currentTimeMillis() + UI_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            var ready: Boolean = false
            dashboardScenario!!.onActivity { activity ->
                val scroll = activity.findViewById<android.widget.ScrollView>(R.id.dashboardScroll)
                val progress = activity.findViewById<android.widget.TextView>(R.id.txtCollectionProgress)
                val button = activity.findViewById<android.widget.Button>(R.id.btnDashboardCollection)
                ready = progress.text.toString() == expectedText &&
                    !activity.window.decorView.isLayoutRequested &&
                    scroll.isLaidOut && !scroll.isLayoutRequested &&
                    button.isShown && button.isLaidOut && button.height > 0
            }
            if (ready) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Dashboard collection card did not load")
    }

    private fun awaitOpenedAlbumReady(expectedText: String): Unit {
        val deadline: Long = System.currentTimeMillis() + UI_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            var ready: Boolean = false
            instrumentation.runOnMainSync {
                val album = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<TreasureAlbumActivity>()
                    .firstOrNull()
                if (album != null) {
                    val progress = album.findViewById<android.widget.TextView>(R.id.tvCollectionProgress)
                    val recycler = album.findViewById<RecyclerView>(R.id.recyclerViewTreasures)
                    ready = progress.text.toString() == expectedText &&
                        recycler.isShown && recycler.isLaidOut && recycler.adapter?.itemCount == 19
                }
            }
            if (ready) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Opened treasure album did not finish loading")
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS: Long = 10_000L
        const val POLL_INTERVAL_MILLIS: Long = 50L
    }
}
