package com.pixelpals.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.pixelpals.app.R
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetDashboardActivity
import com.pixelpals.app.feature.store.StoreActivity
import kotlinx.coroutines.launch

/**
 * PetSelectionActivity — Pantalla de selección de mascota.
 *
 * Presenta las mascotas en una grilla con sus sprites.
 * Al tocar una, se selecciona y se lanza el servicio con esa mascota.
 */
class PetSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PET_TYPE = "pet_type"
    }

    private var isLaunching = false
    private lateinit var selectedPetStore: SelectedPetStore
    private val repository: PixelPalsRepository by lazy { AppServices.repository(this) }
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(this) }
    private lateinit var catalogContainer: LinearLayout
    private lateinit var txtCurrentMood: TextView
    private lateinit var txtCatalogSummary: TextView
    private lateinit var txtSelectionHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.choose_your_pet)
        edgeToEdge()
        setContentView(R.layout.activity_pet_selection)
        selectedPetStore = SelectedPetStore(this)
        catalogContainer = findViewById(R.id.catalogContainer)
        txtCurrentMood = findViewById(R.id.txtCurrentMood)
        txtCatalogSummary = findViewById(R.id.txtCatalogSummary)
        txtSelectionHint = findViewById(R.id.txtSelectionHint)
        applySystemBarsInsets(findViewById(R.id.selectionScroll))

        findViewById<Button>(R.id.btnOpenStore).setOnClickListener {
            startActivity(Intent(this, StoreActivity::class.java))
        }
        findViewById<Button>(R.id.btnOpenDashboard).setOnClickListener {
            startActivity(Intent(this, PetDashboardActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { renderCatalog() }
    }

    private suspend fun renderCatalog() {
        runCatching {
            val selected = selectedPetStore.load()
            val snapshot = repository.getStatusSnapshot(selected)
            val items = repository.getCatalog(selected).sortedWith(
                compareBy<PetCatalogItem> {
                    when (it.state) {
                        CatalogItemState.SELECTED -> 0
                        CatalogItemState.OWNED -> 1
                        CatalogItemState.LOCKED -> 2
                    }
                }.thenBy { it.displayName }
            )
            val selectedCount = items.count { it.state == CatalogItemState.SELECTED }
            val ownedCount = items.count { it.state == CatalogItemState.OWNED }
            val lockedCount = items.count { it.state == CatalogItemState.LOCKED }

            txtCurrentMood.text = getString(
                R.string.selection_current_pet_format,
                getString(selected.displayNameResId),
                moodLabel(snapshot.mood),
                snapshot.bond
            )
            txtCurrentMood.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            txtCatalogSummary.text = getString(
                R.string.selection_catalog_summary_format,
                selectedCount,
                ownedCount,
                lockedCount
            )
            txtCatalogSummary.setTextColor(ContextCompat.getColor(this, R.color.status_info_fg))
            txtSelectionHint.text = getString(
                R.string.selection_hint_format,
                careActionLabel(snapshot.dominantSuggestion),
                snapshot.careStreakDays
            )
            txtSelectionHint.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            catalogContainer.removeAllViews()
            items.forEach { item ->
                catalogContainer.addView(buildCatalogCard(item))
            }

            analytics.track(
                "selection_opened",
                mapOf("selected_pet" to selected.name.lowercase(), "catalog_size" to items.size.toString())
            )
        }.onFailure {
            txtCurrentMood.text = getString(R.string.selection_error)
            txtCatalogSummary.text = getString(R.string.selection_error)
            txtSelectionHint.text = getString(R.string.selection_error)
            txtCurrentMood.setTextColor(ContextCompat.getColor(this, R.color.red_error))
            txtCatalogSummary.setTextColor(ContextCompat.getColor(this, R.color.red_error))
            txtSelectionHint.setTextColor(ContextCompat.getColor(this, R.color.red_error))
            catalogContainer.removeAllViews()
            catalogContainer.addView(
                TextView(this).apply {
                    text = getString(R.string.selection_error)
                    setTextColor(ContextCompat.getColor(this@PetSelectionActivity, R.color.red_error))
                    setPadding(0, 24, 0, 0)
                }
            )
        }
    }

    private fun buildCatalogCard(item: PetCatalogItem): LinearLayout {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_pet_catalog, catalogContainer, false) as LinearLayout
        val image = card.findViewById<android.widget.ImageView>(R.id.imgPetPreview)
        val name = card.findViewById<TextView>(R.id.txtPetName)
        val desc = card.findViewById<TextView>(R.id.txtPetDesc)
        val badge = card.findViewById<TextView>(R.id.txtPetBadge)
        val state = card.findViewById<TextView>(R.id.txtPetState)
        val action = card.findViewById<Button>(R.id.btnPetAction)
        val price = card.findViewById<TextView>(R.id.txtPetPrice)

        image.setImageResource(item.previewResId)
        name.text = item.displayName
        desc.text = item.description.replace('\n', ' ')
        badge.text = if (item.isPremium) {
            getString(R.string.selection_premium_badge)
        } else {
            getString(R.string.selection_base_badge)
        }
        state.text = when (item.state) {
            CatalogItemState.LOCKED -> getString(R.string.selection_locked_state)
            CatalogItemState.OWNED -> getString(R.string.selection_owned_state)
            CatalogItemState.SELECTED -> getString(R.string.selection_selected_state)
        }
        if (item.state == CatalogItemState.LOCKED && item.coinPrice != null) {
            price.text = getString(R.string.cosmetic_price_format, item.coinPrice)
            price.visibility = View.VISIBLE
        } else {
            price.visibility = View.GONE
        }
        action.text = when (item.state) {
            CatalogItemState.LOCKED -> getString(R.string.selection_unlock_button)
            CatalogItemState.OWNED -> getString(R.string.selection_choose_button)
            CatalogItemState.SELECTED -> getString(R.string.selection_selected_button)
        }
        if (item.state == CatalogItemState.SELECTED) {
            card.setBackgroundResource(R.drawable.bg_card_pet_selected)
        }
        val accessibleState = when (item.state) {
            CatalogItemState.LOCKED -> getString(R.string.selection_locked_state)
            CatalogItemState.OWNED -> getString(R.string.selection_owned_state)
            CatalogItemState.SELECTED -> getString(R.string.selection_selected_state)
        }
        card.contentDescription = getString(
            R.string.selection_item_content_description,
            item.displayName,
            accessibleState,
            desc.text.toString()
        )
        image.contentDescription = card.contentDescription
        // El pet seleccionado también debe poder relanzarse (si el servicio fue
        // terminado por el sistema, "Usar" lo vuelve a arrancar).
        action.isEnabled = true
        action.alpha = 1f
        action.setOnClickListener {
            when (item.state) {
                CatalogItemState.LOCKED -> startActivity(Intent(this, StoreActivity::class.java))
                CatalogItemState.OWNED -> item.petType?.let { launchSelectedPet(it) }
                CatalogItemState.SELECTED -> item.petType?.let { launchSelectedPet(it) }
            }
        }
        card.setOnClickListener(null)
        card.isClickable = false
        card.isFocusable = false
        return card
    }

    private fun launchSelectedPet(type: PetType) {
        if (isLaunching) return
        isLaunching = true
        selectedPetStore.save(type)
        analytics.track(
            "pet_selected",
            mapOf("pet_id" to type.name.lowercase(), "display_name" to getString(type.displayNameResId))
        )
        launchPet(type)
    }

    private fun launchPet(type: PetType) {
        PetService.requestPetChange(this, type)

        Toast.makeText(
            this,
            getString(R.string.selection_launching_pet_format, getString(type.displayNameResId)),
            Toast.LENGTH_SHORT
        ).show()

        finish()
        moveTaskToBack(true)
    }

    private fun edgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarsInsets(view: ScrollView) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun moodLabel(mood: PetMood): String {
        return getString(
            when (mood) {
                PetMood.HAPPY -> R.string.mood_happy
                PetMood.SLEEPY -> R.string.mood_sleepy
                PetMood.HUNGRY -> R.string.mood_hungry
                PetMood.DIRTY -> R.string.mood_dirty
                PetMood.BORED -> R.string.mood_bored
                PetMood.EXCITED -> R.string.mood_excited
            }
        )
    }

    private fun careActionLabel(action: CareAction): String {
        return getString(
            when (action) {
                CareAction.FEED -> R.string.action_feed
                CareAction.CLEAN -> R.string.action_clean
                CareAction.PLAY -> R.string.action_play
                CareAction.REST -> R.string.action_rest
                CareAction.CHECK_IN -> R.string.action_check_in
            }
        )
    }
}
