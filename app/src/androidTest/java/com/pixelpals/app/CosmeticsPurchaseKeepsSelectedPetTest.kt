package com.pixelpals.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticCatalog
import com.pixelpals.app.data.catalog.CosmeticEffect
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.feature.store.CosmeticsTabFragment
import com.pixelpals.app.feature.store.CosmeticCatalogAdapter
import com.pixelpals.app.feature.store.CosmeticCatalogRow
import com.pixelpals.app.feature.store.StoreFragment
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.StoreSection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CosmeticsPurchaseKeepsSelectedPetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private lateinit var repository: PixelPalsRepository
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppDatabase.getDatabase(context).clearAllTables()
        context.getSharedPreferences("pixelpals_cosmetics", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("pixelpals_selection", Context.MODE_PRIVATE).edit().clear().commit()
        repository = PixelPalsRepository(context)
        SelectedPetStore(context).save(PetType.TELA)
        runBlocking { repository.grantCoins(petType = null, amount = 2_000) }
    }

    @After
    fun tearDown() {
        scenario?.close()
        context.stopService(Intent(context, PetService::class.java))
        PetService.isRunning = false
    }

    @Test
    fun buyingAFloatCosmeticEquipsItOnTheSelectedPetNotCorgi() {
        val cosmetic: Cosmetic = CosmeticCatalog.all(context)
            .first { it.effect is CosmeticEffect.FloatEffect }
        scenario = ActivityScenario.launch(
            MainActivity.createIntent(context, PixelPalsDestination.STORE, StoreSection.COSMETICS),
        )
        val button: Button = awaitCosmeticButton(cosmetic)
        instrumentation.runOnMainSync { button.performClick() }
        val confirm = device.wait(
            Until.findObject(By.res("android", "button1")),
            5_000,
        )
        checkNotNull(confirm) { "Purchase confirmation was not displayed" }
        clickDialogButtonWithRetry()
        awaitEquipped(petId = "tela", cosmeticId = cosmetic.id)
        assertEquals(cosmetic.id, repository.getEquippedCosmetic("tela"))
        assertNull(repository.getEquippedCosmetic("corgi"))
    }

    private fun clickDialogButtonWithRetry() {
        val deadline: Long = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            try {
                device.findObject(By.res("android", "button1"))?.click()
                return
            } catch (_: StaleObjectException) {
                instrumentation.waitForIdleSync()
            }
            Thread.sleep(100)
        }
        throw AssertionError("Purchase confirmation could not be clicked")
    }

    private fun awaitCosmeticButton(cosmetic: Cosmetic): Button {
        val deadline: Long = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var found: Button? = null
            scenario!!.onActivity { activity ->
                val store: StoreFragment = activity.supportFragmentManager
                    .findFragmentByTag(PixelPalsDestination.STORE.fragmentTag) as StoreFragment
                val cosmetics: CosmeticsTabFragment = store.childFragmentManager.fragments
                    .filterIsInstance<CosmeticsTabFragment>()
                    .first()
                val list: RecyclerView = cosmetics.requireView().findViewById(R.id.storeList)
                val catalogAdapter: CosmeticCatalogAdapter = list.adapter as CosmeticCatalogAdapter
                val targetPosition: Int = catalogAdapter.currentList.indexOfFirst { row ->
                    row is CosmeticCatalogRow.Item && row.cosmetic.id == cosmetic.id
                }
                if (targetPosition >= 0) list.scrollToPosition(targetPosition)
                found = findCosmeticButton(list, cosmetic)
            }
            found?.let { return it }
            Thread.sleep(150)
        }
        throw AssertionError("Cosmetic action was not rendered for ${cosmetic.id}")
    }

    private fun findCosmeticButton(root: View, cosmetic: Cosmetic): Button? {
        if (root is Button && root.id == R.id.btnCosmeticAction) {
            val card: ViewGroup? = root.parent as? ViewGroup
            val title: TextView? = card?.findViewById(R.id.txtCosmeticTitle)
            if (title?.text.toString() == cosmetic.displayName) return root
        }
        if (root is ViewGroup) {
            for (index: Int in 0 until root.childCount) {
                findCosmeticButton(root.getChildAt(index), cosmetic)?.let { return it }
            }
        }
        return null
    }

    private fun awaitEquipped(petId: String, cosmeticId: String) {
        val deadline: Long = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (repository.getEquippedCosmetic(petId) == cosmeticId) return
            Thread.sleep(150)
        }
        throw AssertionError("Cosmetic $cosmeticId was not equipped on $petId")
    }
}
