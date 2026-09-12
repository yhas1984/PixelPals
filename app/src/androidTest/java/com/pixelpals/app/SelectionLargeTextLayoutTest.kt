package com.pixelpals.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SelectionLargeTextLayoutTest {
    @Test
    fun largeTextAndShortViewportRevealChooseActionAfterScrolling(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario: ActivityScenario<CompanionSettingsActivity> = ActivityScenario.launch(CompanionSettingsActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val base: Context = activity
                val configuration = Configuration(base.resources.configuration).apply {
                    fontScale = 2f
                    setLocale(Locale.ENGLISH)
                }
                val localized: Context = base.createConfigurationContext(configuration)
                val context: Context = ContextThemeWrapper(localized, R.style.Theme_PixelPals)
                val root: View = LayoutInflater.from(context).inflate(R.layout.activity_pet_selection, null, false)
                root.findViewById<View>(R.id.progressSelection).visibility = View.GONE
                root.findViewById<TextView>(R.id.txtCurrentMood).text = "Corgi feels happy • Bond: 5"
                root.findViewById<TextView>(R.id.txtCatalogSummary).text = "With you: 1 • Available: 6 • To discover: 8"
                root.findViewById<TextView>(R.id.txtSelectionHint).text = "A good next step: Check in • Streak: 0"
                val catalog: RecyclerView = root.findViewById(R.id.catalogList)
                catalog.layoutManager = LinearLayoutManager(context)
                catalog.adapter = PetCatalogAdapter(PetCatalogMode.SELECTION) {}
                val adapter: PetCatalogAdapter = catalog.adapter as PetCatalogAdapter
                adapter.submitList((0 until 12).map { index ->
                    PetCatalogRow(PetCatalogItem(
                        id = "pet-$index",
                        displayName = "Pet $index",
                        description = "A companion description that wraps at large text size.",
                        previewResId = PetType.CORGI.spriteResId,
                        petType = PetType.CORGI,
                        productId = null,
                        isPremium = false,
                        state = CatalogItemState.OWNED,
                    ))
                })
                val width: Int = (360 * context.resources.displayMetrics.density).toInt()
                val height: Int = (420 * context.resources.displayMetrics.density).toInt()
                activity.setContentView(root, android.view.ViewGroup.LayoutParams(width, height))
            }
            instrumentation.waitForIdleSync()
            val device: UiDevice = UiDevice.getInstance(instrumentation)
            var found: Boolean = false
            repeat(8) {
                if (found) return@repeat
                val viewportBounds = Rect()
                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.selectionRoot).getGlobalVisibleRect(viewportBounds)
                }
                device.swipe(viewportBounds.centerX(), viewportBounds.bottom - 40, viewportBounds.centerX(), viewportBounds.top + 40, 30)
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val catalog: RecyclerView = activity.findViewById(R.id.catalogList)
                    for (position in 1 until (catalog.adapter?.itemCount ?: 1)) {
                        val holder = catalog.findViewHolderForAdapterPosition(position) ?: continue
                        val choose: Button = requireNotNull(holder.itemView.findViewById(R.id.btnPetAction))
                        val chooseBounds = Rect()
                        if (choose.getGlobalVisibleRect(chooseBounds) && viewportBounds.contains(chooseBounds) &&
                            chooseBounds.height() == choose.height && chooseBounds.width() == choose.width) {
                            found = true
                            return@onActivity
                        }
                    }
                }
            }
            assertTrue("A later pet card and its fully visible Choose action must be reachable after swipes", found)
        } finally {
            scenario.close()
        }
    }
}
