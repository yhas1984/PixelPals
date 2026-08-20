package com.pixelpals.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.feature.store.StoreFragment
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.StoreSection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumPetUnlockAvailabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context: Context = instrumentation.targetContext
    private lateinit var repository: PixelPalsRepository
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppDatabase.getDatabase(context).clearAllTables()
        context.getSharedPreferences("pixelpals_selection", Context.MODE_PRIVATE).edit().clear().commit()
        SelectedPetStore(context).save(PetType.CORGI)
        repository = PixelPalsRepository(context)
        runBlocking { repository.grantCoins(petType = null, amount = 1_000) }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun unlockedPremiumPetCanBeSelectedImmediatelyAfterReturningFromStore() {
        val petName: String = context.getString(PetType.TARO.displayNameResId)
        scenario = ActivityScenario.launch(
            MainActivity.createIntent(context, PixelPalsDestination.STORE, StoreSection.PREMIUM),
        )
        awaitStoreReady()
        val storeButton: Button = awaitPetButton(R.id.storeList, petName)
        instrumentation.runOnMainSync { storeButton.performClick() }
        val confirmation: Boolean = device.wait(
            Until.findObject(By.res("android", "button1")),
            5_000,
        ) != null
        check(confirmation) { "Premium purchase confirmation was not displayed" }
        device.findObject(By.res("android", "button1")).click()
        awaitStoreDoesNotContain(R.id.storeList, petName)

        scenario!!.onActivity { activity -> activity.navigate(PixelPalsDestination.PETS) }
        awaitOwnedPet(petName)
        val expectedAction: String = context.getString(R.string.selection_choose_button)
        val button: Button = awaitSelectionButton(petName)
        assertEquals(expectedAction, button.text.toString())
    }

    private fun awaitStoreReady() {
        awaitCondition("Store did not finish loading") { activity ->
            val store: StoreFragment = activity.supportFragmentManager
                .findFragmentByTag(PixelPalsDestination.STORE.fragmentTag) as StoreFragment
            return@awaitCondition !store.getStoreViewModel().uiState.value.isInitialLoading
        }
    }

    private fun awaitStoreDoesNotContain(listId: Int, petName: String) {
        awaitCondition("Unlocked pet remained in the store") { activity ->
            findPetButton(activity.findViewById(listId), petName) == null
        }
    }

    private fun awaitOwnedPet(petName: String) {
        awaitCondition("Unlocked pet was not refreshed in Mascotas") { activity ->
            val list: androidx.recyclerview.widget.RecyclerView = activity.findViewById(R.id.catalogList)
            val adapter: PetCatalogAdapter = list.adapter as? PetCatalogAdapter ?: return@awaitCondition false
            adapter.currentList.any { row ->
                row.item.displayName == petName && row.item.state == com.pixelpals.app.data.catalog.CatalogItemState.OWNED
            }
        }
    }

    private fun awaitSelectionButton(petName: String): Button {
        var result: Button? = null
        awaitCondition("Unlocked pet action was not rendered in Mascotas") { activity ->
            val list: RecyclerView = activity.findViewById(R.id.catalogList)
            val adapter: PetCatalogAdapter = list.adapter as? PetCatalogAdapter
                ?: return@awaitCondition false
            val targetPosition: Int = adapter.currentList.indexOfFirst { row ->
                row.item.displayName == petName
            }
            if (targetPosition < 0) return@awaitCondition false
            list.scrollToPosition(targetPosition)
            result = findPetButton(list, petName)
            result != null
        }
        return requireNotNull(result)
    }

    private fun awaitPetButton(listId: Int, petName: String): Button {
        var result: Button? = null
        awaitCondition("Premium pet action was not rendered") { activity ->
            val list: RecyclerView = activity.findViewById(listId)
            val adapter: PetCatalogAdapter = list.adapter as? PetCatalogAdapter
                ?: return@awaitCondition false
            val targetPosition: Int = adapter.currentList.indexOfFirst { row ->
                row.item.displayName == petName
            }
            if (targetPosition < 0) return@awaitCondition false
            list.scrollToPosition(targetPosition)
            result = findPetButton(list, petName)
            result != null
        }
        return requireNotNull(result)
    }

    private fun findPetButton(root: View, petName: String): Button? {
        if (root is Button && root.id == R.id.btnPetAction) {
            val card: ViewGroup? = root.parent as? ViewGroup
            val title: TextView? = card?.findViewById(R.id.txtPetName)
            if (title?.text?.toString() == petName) return root
        }
        if (root is ViewGroup) {
            for (index: Int in 0 until root.childCount) {
                findPetButton(root.getChildAt(index), petName)?.let { return it }
            }
        }
        return null
    }

    private fun awaitCondition(message: String, condition: (MainActivity) -> Boolean) {
        val deadline: Long = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            var matched: Boolean = false
            scenario!!.onActivity { activity -> matched = condition(activity) }
            if (matched) return
            instrumentation.waitForIdleSync()
            Thread.sleep(100)
        }
        throw AssertionError(message)
    }
}
