package com.pixelpals.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticCatalog
import com.pixelpals.app.data.catalog.CosmeticEffect
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.feature.store.StoreActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresión del bug "comprar un flotante hace aparecer a Corgi": la pestaña de
 * cosméticos capturaba el petId al dibujar las tarjetas; si el usuario cambiaba
 * de mascota después, el clic equipaba/compraba sobre el pet viejo y re-cambiaba
 * el overlay a Corgi. El clic debe releer la selección persistida.
 */
@RunWith(AndroidJUnit4::class)
class CosmeticsPurchaseKeepsSelectedPetTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var repository: PixelPalsRepository
    private var scenario: ActivityScenario<StoreActivity>? = null

    @Before
    fun setUp() {
        AppDatabase.getDatabase(context).clearAllTables()
        context.getSharedPreferences("pixelpals_cosmetics", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("pixelpals_selection", Context.MODE_PRIVATE).edit().clear().commit()
        repository = PixelPalsRepository(context)
        // El usuario ya tenía TELA seleccionada (desde PetsTab / selección).
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
        val cosmetic = CosmeticCatalog.all(context)
            .first { it.effect is CosmeticEffect.FloatEffect }

        scenario = ActivityScenario.launch(StoreActivity::class.java)
        scenario!!.onActivity { activity ->
            val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.storePager)
            pager.setCurrentItem(1, false)
        }

        val button = awaitCosmeticButton(cosmetic)
        instrumentation.runOnMainSync { button.performClick() }

        awaitEquipped(petId = "tela", cosmeticId = cosmetic.id)

        // El cosmético quedó equipado en TELA (la selección real)…
        assertEquals(cosmetic.id, repository.getEquippedCosmetic("tela"))
        // …y no se aplicó al Corgi (el petId obsoleto del bug).
        assertNull(repository.getEquippedCosmetic("corgi"))
    }

    private fun awaitCosmeticButton(cosmetic: Cosmetic): Button {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var found: Button? = null
            scenario!!.onActivity { activity ->
                found = findCosmeticButton(activity.findViewById(R.id.scrollContent), cosmetic)
            }
            val result = found
            if (result != null) return result
            Thread.sleep(150)
        }
        throw AssertionError("No se renderizó el botón del cosmético ${cosmetic.id}")
    }

    private fun findCosmeticButton(root: View, cosmetic: Cosmetic): Button? {
        if (root is Button && root.id == R.id.btnCosmeticAction) {
            val card = root.parent as? ViewGroup
            val title = card?.findViewById<TextView>(R.id.txtCosmeticTitle)
            if (title?.text.toString() == cosmetic.displayName) return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findCosmeticButton(root.getChildAt(i), cosmetic)?.let { return it }
            }
        }
        return null
    }

    private fun awaitEquipped(petId: String, cosmeticId: String) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (repository.getEquippedCosmetic(petId) == cosmeticId) return
            Thread.sleep(150)
        }
        throw AssertionError("El cosmético $cosmeticId no quedó equipado en $petId")
    }
}
