package com.pixelpals.app.feature.treasure

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun dashboardExposesCollectionCardAndAlbumAction(): Unit = runBlocking {
        database.treasureDao().insertTreasure(TreasureItem("🦴", 1, 100L, 100L, 1))
        database.treasureDao().insertTreasure(TreasureItem("🍀", 1, 100L, 100L, 1))
        dashboardScenario = ActivityScenario.launch(PetDashboardActivity::class.java)
        awaitText(R.id.txtCollectionProgress)
        onView(withId(R.id.btnDashboardCollection)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText(context.getString(R.string.treasure_collection_progress, 2, 19)))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
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

    private fun awaitText(viewId: Int): Unit {
        val deadline: Long = System.currentTimeMillis() + UI_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            var hasText: Boolean = false
            dashboardScenario!!.onActivity { activity ->
                hasText = activity.findViewById<android.widget.TextView>(viewId).text.isNotBlank()
            }
            if (hasText) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Dashboard collection card did not load")
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS: Long = 10_000L
        const val POLL_INTERVAL_MILLIS: Long = 50L
    }
}
