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
import com.pixelpals.app.feature.store.StoreFragment
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.StoreSection
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorePremiumTabTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

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
        scenario = ActivityScenario.launch(
            MainActivity.createIntent(context, PixelPalsDestination.STORE, StoreSection.PREMIUM),
        )
        awaitStateReady()
        scenario!!.onActivity { activity ->
            val root: View = activity.findViewById(R.id.storeList)
            val texts: List<String> = collectTexts(root)
            assertFalse(texts.contains("Corgi"))
            assertTrue(texts.any { it.contains("Unlock") || it.contains("Desbloquear") })
            assertTrue(
                collectButtons(root).none {
                    it.text.toString() == "Select" || it.text.toString() == "Seleccionar"
                },
            )
        }
    }

    private fun awaitStateReady() {
        val deadline: Long = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var isReady: Boolean = false
            scenario!!.onActivity { activity ->
                val store: StoreFragment = activity.supportFragmentManager
                    .findFragmentByTag(PixelPalsDestination.STORE.fragmentTag) as StoreFragment
                isReady = !store.getStoreViewModel().uiState.value.isInitialLoading
            }
            if (isReady) return
            Thread.sleep(100)
        }
        throw AssertionError("The store did not finish loading")
    }

    private fun collectTexts(view: View): List<String> {
        val result = mutableListOf<String>()
        if (view is TextView && view.text.isNotBlank()) result += view.text.toString()
        if (view is ViewGroup) {
            for (index: Int in 0 until view.childCount) result += collectTexts(view.getChildAt(index))
        }
        return result
    }

    private fun collectButtons(view: View): List<Button> {
        val result = mutableListOf<Button>()
        if (view is Button) result += view
        if (view is ViewGroup) {
            for (index: Int in 0 until view.childCount) result += collectButtons(view.getChildAt(index))
        }
        return result
    }
}
