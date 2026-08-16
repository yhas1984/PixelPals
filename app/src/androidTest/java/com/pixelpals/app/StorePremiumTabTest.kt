package com.pixelpals.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.feature.store.StoreActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorePremiumTabTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<StoreActivity>? = null

    @Before
    fun setUp() {
        AppDatabase.getDatabase(context).clearAllTables()
        context.getSharedPreferences("pixelpals_selection", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("pixelpals_cosmetics", Context.MODE_PRIVATE).edit().clear().commit()
        SelectedPetStore(context).save(PetType.CORGI)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun premiumTabDoesNotOfferBasePetSelection() {
        scenario = ActivityScenario.launch(StoreActivity::class.java)
        scenario!!.onActivity { activity ->
            val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.storePager)
            pager.setCurrentItem(0, false)
        }
        awaitStateReady()
        scenario!!.onActivity { activity ->
            val root = activity.findViewById<View>(R.id.scrollContent)
            val texts = collectTexts(root)
            assertFalse(texts.contains("Corgi"))
            assertTrue(texts.any { it.contains("Unlock") || it.contains("Desbloquear") })
            assertTrue(collectButtons(root).none { it.text.toString() == "Select" || it.text.toString() == "Seleccionar" })
        }
    }

    private fun awaitStateReady() {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            scenario!!.onActivity { ready = !it.getStoreViewModel().uiState.value.isLoading }
            if (ready) return
            Thread.sleep(100)
        }
        throw AssertionError("La tienda no terminó de cargar")
    }

    private fun collectTexts(view: View): List<String> {
        val result = mutableListOf<String>()
        if (view is TextView && view.text.isNotBlank()) result += view.text.toString()
        if (view is ViewGroup) for (index in 0 until view.childCount) result += collectTexts(view.getChildAt(index))
        return result
    }

    private fun collectButtons(view: View): List<Button> {
        val result = mutableListOf<Button>()
        if (view is Button) result += view
        if (view is ViewGroup) for (index in 0 until view.childCount) result += collectButtons(view.getChildAt(index))
        return result
    }
}
