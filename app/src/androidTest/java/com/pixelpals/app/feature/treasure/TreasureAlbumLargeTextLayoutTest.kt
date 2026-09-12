package com.pixelpals.app.feature.treasure

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.pixelpals.app.CompanionSettingsActivity
import com.pixelpals.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.io.File

@RunWith(AndroidJUnit4::class)
class TreasureAlbumLargeTextLayoutTest {
    @Test
    fun largeTextHeaderCollapsesAndExposesInteractiveTreasureItems(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (locale: Locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
            val scenario: ActivityScenario<CompanionSettingsActivity> = ActivityScenario.launch(CompanionSettingsActivity::class.java)
            var clicks: Int = 0
            try {
                scenario.onActivity { activity ->
                    val context: Context = themedContext(activity, locale)
                    val root: View = LayoutInflater.from(context).inflate(R.layout.activity_treasure_album, null, false)
                    val summary: View = root.findViewById(R.id.cardCollectionSummary)
                    summary.visibility = View.VISIBLE
                    root.findViewById<TextView>(R.id.tvCollectionProgress).text = context.getString(R.string.treasure_collection_progress, 2, 19)
                    root.findViewById<TextView>(R.id.tvCollectionBadge).text = context.getString(R.string.treasure_collection_badge_format, "✨")
                    root.findViewById<TextView>(R.id.tvCollectionNextReward).text = context.getString(R.string.treasure_collection_next_reward, 5, 30, 19)
                    root.findViewById<TextView>(R.id.tvDailyGiftStatus).text = context.getString(R.string.treasure_collection_gift_available, "Corgi")
                    root.findViewById<TextView>(R.id.tvAlbumState).text = context.getString(R.string.treasure_album_ready)
                    val recycler: RecyclerView = root.findViewById(R.id.recyclerViewTreasures)
                    recycler.layoutManager = GridLayoutManager(context, 1)
                    recycler.adapter = TreasureAdapter { clicks++ }.apply { submitList(createItems()) }
                    val width: Int = dp(context, 360)
                    val height: Int = dp(context, 420)
                    activity.setContentView(root, ViewGroup.LayoutParams(width, height))
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val root: View = activity.findViewById(R.id.albumRoot)
                    assertComplete(root.findViewById(R.id.tvAlbumTitle))
                    assertComplete(root.findViewById(R.id.tvAlbumSubtitle))
                    assertComplete(root.findViewById(R.id.tvCollectionProgress))
                    assertComplete(root.findViewById(R.id.tvCollectionBadge))
                    assertComplete(root.findViewById(R.id.tvCollectionNextReward))
                    assertComplete(root.findViewById(R.id.tvDailyGiftStatus))
                    assertComplete(root.findViewById(R.id.tvAlbumState))
                }
                instrumentation.waitForIdleSync()
                val device: UiDevice = UiDevice.getInstance(instrumentation)
                val observations = StringBuilder("locale=${locale.language}\n")
                var clicked: Boolean = false
                repeat(8) {
                    if (clicked) return@repeat
                    val viewport = Rect()
                    scenario.onActivity { activity ->
                        activity.findViewById<View>(R.id.albumRoot).getGlobalVisibleRect(viewport)
                    }
                    device.swipe(viewport.centerX(), viewport.bottom - 40, viewport.centerX(), viewport.top + 40, 30)
                    device.waitForIdle(2_000L)
                    instrumentation.waitForIdleSync()
                    var tapPoint: Pair<Int, Int>? = null
                    scenario.onActivity { activity ->
                        val recycler: RecyclerView = activity.findViewById(R.id.recyclerViewTreasures)
                        observations.append("viewport=$viewport recyclerY=${recycler.y} height=${recycler.height} children=${recycler.childCount}\n")
                        for (position in 1 until (recycler.adapter?.itemCount ?: 1)) {
                            val item = recycler.findViewHolderForAdapterPosition(position)?.itemView ?: continue
                            val bounds = Rect()
                            item.getGlobalVisibleRect(bounds)
                            observations.append("position=$position bounds=$bounds\n")
                            if (item.getGlobalVisibleRect(bounds) && viewport.contains(bounds) &&
                                bounds.height() >= dp(item.context, 48)) {
                                tapPoint = bounds.centerX() to bounds.centerY()
                                break
                            }
                        }
                    }
                    tapPoint?.let { (x, y) ->
                        device.click(x, y)
                    }
                    instrumentation.waitForIdleSync()
                    clicked = clicks > 0
                }
                val evidence = File(instrumentation.targetContext.cacheDir, "commerce-large-text").apply { mkdirs() }
                File(evidence, "${locale.language}.txt").writeText(observations.toString())
                device.takeScreenshot(File(evidence, "${locale.language}.png"))
                device.dumpWindowHierarchy(File(evidence, "${locale.language}.xml"))
                assertTrue("A later treasure item must become visible and clickable", clicked)
                assertEquals(1, clicks)
            } finally {
                scenario.close()
            }
        }
    }

    private fun themedContext(base: Context, locale: Locale): Context {
        val configuration = Configuration(base.resources.configuration).apply { fontScale = 2f; setLocale(locale) }
        return ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_PixelPals)
    }

    private fun createItems(): List<TreasureCollectionItem> = (0 until 19).map { index ->
        TreasureCollectionItem(
            id = "treasure-$index", emoji = "✨", name = "Treasure ${index + 1}",
            story = "A discovered keepsake with a long story that remains readable while browsing the album.",
            hint = "Keep exploring to discover this keepsake.", inventoryCount = 1, totalFound = 1,
            lastFoundAt = 1L, isFavorite = index == 0, canGift = false,
        )
    }

    private fun assertComplete(view: TextView): Unit {
        assertEquals(view.text.length, view.layout.getLineEnd(view.layout.lineCount - 1))
        assertEquals(0, view.layout.getEllipsisCount(view.layout.lineCount - 1))
        assertTrue(view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
