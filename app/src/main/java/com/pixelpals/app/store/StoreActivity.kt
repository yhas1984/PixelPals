package com.pixelpals.app.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.pixelpals.app.AppServices
import com.pixelpals.app.PetType
import com.pixelpals.app.PetService
import com.pixelpals.app.R
import com.pixelpals.app.SelectedPetStore
import com.pixelpals.app.catalog.AccessoryCatalogItem
import com.pixelpals.app.catalog.AccessoryPurchaseResult
import com.pixelpals.app.catalog.CatalogItemState
import com.pixelpals.app.catalog.PetCatalogItem
import com.pixelpals.app.status.PetStatusSnapshot
import kotlinx.coroutines.launch

class StoreActivity : AppCompatActivity() {
    private lateinit var selectedPetStore: SelectedPetStore
    private val repository by lazy { AppServices.repository(this) }
    private val analytics by lazy { AppServices.analytics(this) }
    private val billing by lazy { AppServices.billingRepository(this) }

    private lateinit var selectedPet: PetType
    private lateinit var premiumPetsContainer: LinearLayout
    private lateinit var accessoriesContainer: LinearLayout
    private lateinit var txtStoreSubtitle: TextView
    private lateinit var txtStoreHighlight: TextView
    private lateinit var txtStoreWallet: TextView
    private lateinit var cardStoreState: LinearLayout
    private lateinit var txtStoreStatus: TextView
    private lateinit var progressStoreLoading: View
    private lateinit var btnStoreRetry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.store_title)
        edgeToEdge()
        setContentView(R.layout.activity_store)
        selectedPetStore = SelectedPetStore(this)
        selectedPet = selectedPetStore.load()
        premiumPetsContainer = findViewById(R.id.premiumPetsContainer)
        accessoriesContainer = findViewById(R.id.accessoriesContainer)
        txtStoreSubtitle = findViewById(R.id.txtStoreSubtitle)
        txtStoreHighlight = findViewById(R.id.txtStoreHighlight)
        txtStoreWallet = findViewById(R.id.txtStoreWallet)
        cardStoreState = findViewById(R.id.cardStoreState)
        txtStoreStatus = findViewById(R.id.txtStoreStatus)
        progressStoreLoading = findViewById(R.id.progressStoreLoading)
        btnStoreRetry = findViewById(R.id.btnStoreRetry)
        applySystemBarsInsets()

        findViewById<Button>(R.id.btnRestorePurchases).setOnClickListener {
            lifecycleScope.launch {
                showLoadingState(getString(R.string.store_restore_in_progress))
                runCatching {
                    val restored = billing.restorePurchases()
                    Toast.makeText(
                        this@StoreActivity,
                        resources.getQuantityString(R.plurals.store_restored_count, restored, restored),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshStore()
                }.onFailure {
                    showErrorState(getString(R.string.store_restore_failed))
                }
            }
        }

        btnStoreRetry.setOnClickListener { lifecycleScope.launch { refreshStore() } }

    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { refreshStore() }
    }

    private suspend fun refreshStore() {
        showLoadingState(getString(R.string.store_loading))
        runCatching {
            renderStore()
        }.onFailure {
            showErrorState(getString(R.string.store_error))
        }
    }

    private suspend fun renderStore() {
        val catalog = repository.getCatalog(selectedPet).filter { it.isPremium }
        val accessories = repository.getAccessoryCatalog(selectedPet)
        val productIds = (catalog.mapNotNull { it.productId } + accessories.map { it.productId }).distinct()
        val prices = billing.prefetch(productIds)
        val equippedAccessory = repository.getEquippedAccessory(selectedPet)
        val snapshot = repository.getStatusSnapshot(selectedPet)

        txtStoreSubtitle.text = getString(R.string.store_subtitle_format, selectedPet.displayName)
        txtStoreHighlight.text = getString(
            R.string.store_featured_message_format,
            selectedPet.displayName,
            equippedAccessory?.displayName ?: getString(R.string.store_owned_hint)
        )
        txtStoreWallet.text = getString(
            R.string.store_wallet_format,
            selectedPet.displayName,
            snapshot.softCurrency,
            snapshot.bond,
        )
        premiumPetsContainer.removeAllViews()
        accessoriesContainer.removeAllViews()

        catalog.forEach { item ->
            premiumPetsContainer.addView(buildPetOfferView(item, prices[item.productId]))
        }
        accessories.forEach { accessory ->
            val owned = repository.isProductOwned(accessory.productId)
            accessoriesContainer.addView(
                buildAccessoryOfferView(
                    accessory = accessory,
                    price = prices[accessory.productId],
                    equippedId = equippedAccessory?.id,
                    snapshot = snapshot,
                    owned = owned,
                )
            )
        }

        analytics.track(
            "store_opened",
            mapOf("pet_id" to selectedPet.name.lowercase(), "premium_pets" to catalog.size.toString())
        )
        val isEmpty = catalog.isEmpty() && accessories.isEmpty()
        if (isEmpty) {
            txtStoreStatus.text = getString(R.string.store_empty)
            txtStoreStatus.setTextColor(ContextCompat.getColor(this, R.color.status_empty_fg))
            cardStoreState.setBackgroundResource(R.drawable.bg_status_empty)
            progressStoreLoading.visibility = View.GONE
            btnStoreRetry.visibility = View.VISIBLE
        } else {
            txtStoreStatus.text = getString(R.string.store_state_ready)
            txtStoreStatus.setTextColor(ContextCompat.getColor(this, R.color.status_success_fg))
            cardStoreState.setBackgroundResource(R.drawable.bg_status_success)
            progressStoreLoading.visibility = View.GONE
            btnStoreRetry.visibility = View.GONE
        }
    }

    private fun buildPetOfferView(item: PetCatalogItem, price: String?): LinearLayout {
        val view = LayoutInflater.from(this).inflate(R.layout.item_store_offer, premiumPetsContainer, false) as LinearLayout
        view.findViewById<ImageView>(R.id.imgOfferPetPreview).setImageResource(item.previewResId)
        view.findViewById<TextView>(R.id.txtOfferEmoji).visibility = View.GONE
        view.findViewById<TextView>(R.id.txtOfferTitle).text = item.displayName
        view.findViewById<TextView>(R.id.txtOfferSubtitle).text =
            item.description.replace('\n', ' ')
        view.findViewById<TextView>(R.id.txtOfferPrice).text = price ?: getString(R.string.store_preview_price)
        val stateText = when (item.state) {
            CatalogItemState.LOCKED -> getString(R.string.store_locked_state)
            CatalogItemState.OWNED -> getString(R.string.store_owned_state)
            CatalogItemState.SELECTED -> getString(R.string.store_selected_state)
        }
        view.findViewById<TextView>(R.id.txtOfferState).text = stateText
        val button = view.findViewById<Button>(R.id.btnOfferAction)
        button.text = when (item.state) {
            CatalogItemState.LOCKED -> getString(R.string.store_buy_button)
            CatalogItemState.OWNED -> getString(R.string.store_select_button)
            CatalogItemState.SELECTED -> getString(R.string.store_selected_button)
        }
        button.isEnabled = item.state != CatalogItemState.SELECTED
        button.setOnClickListener {
            when (item.state) {
                CatalogItemState.LOCKED -> {
                    val productId = item.productId ?: return@setOnClickListener
                    billing.launchPurchase(this, productId) { success ->
                        lifecycleScope.launch {
                            if (success) refreshStore()
                        }
                    }
                }
                CatalogItemState.OWNED -> {
                    item.petType?.let {
                        selectedPetStore.save(it)
                        selectedPet = it
                        PetService.requestPetChange(this, it)
                    }
                    lifecycleScope.launch { refreshStore() }
                }
                CatalogItemState.SELECTED -> Unit
            }
        }
        view.contentDescription = getString(
            R.string.store_item_content_description,
            item.displayName,
            stateText,
            price ?: getString(R.string.store_preview_price)
        )
        return view
    }

    private fun buildAccessoryOfferView(
        accessory: AccessoryCatalogItem,
        price: String?,
        equippedId: String?,
        snapshot: PetStatusSnapshot,
        owned: Boolean,
    ): LinearLayout {
        val view = LayoutInflater.from(this).inflate(R.layout.item_store_offer, accessoriesContainer, false) as LinearLayout
        view.findViewById<ImageView>(R.id.imgOfferPetPreview).setImageResource(selectedPet.spriteResId)
        val accessoryEmoji = view.findViewById<TextView>(R.id.txtOfferEmoji)
        accessoryEmoji.text = accessory.emoji
        accessoryEmoji.translationX = accessory.offsetXRatio * 72f * resources.displayMetrics.density
        accessoryEmoji.translationY = accessory.offsetYRatio * 72f * resources.displayMetrics.density
        val emojiScale = (accessory.scale / 0.22f).coerceIn(0.8f, 1.35f)
        accessoryEmoji.scaleX = emojiScale
        accessoryEmoji.scaleY = emojiScale
        view.findViewById<TextView>(R.id.txtOfferTitle).text = accessory.displayName
        view.findViewById<TextView>(R.id.txtOfferSubtitle).text = accessory.description
        view.findViewById<TextView>(R.id.txtOfferPrice).text = when {
            owned -> getString(R.string.store_owned_state)
            accessory.coinPrice != null -> getString(R.string.store_coin_price, accessory.coinPrice)
            else -> price ?: getString(R.string.store_preview_price)
        }
        val button = view.findViewById<Button>(R.id.btnOfferAction)
        val isEquipped = equippedId == accessory.id
        val isBondLocked = snapshot.bond < accessory.bondRequired
        val missingCoins = ((accessory.coinPrice ?: 0) - snapshot.softCurrency).coerceAtLeast(0)
        val stateText = when {
            isEquipped -> getString(R.string.store_equipped_state)
            owned -> getString(R.string.store_owned_state)
            accessory.coinPrice != null && isBondLocked -> getString(R.string.store_bond_locked_state)
            accessory.coinPrice != null -> getString(R.string.store_coin_offer_state)
            else -> getString(R.string.store_locked_state)
        }
        view.findViewById<TextView>(R.id.txtOfferState).text = stateText
        button.text = when {
            isEquipped -> getString(R.string.store_unequip_button)
            owned -> getString(R.string.store_equip_button)
            accessory.coinPrice != null && isBondLocked -> getString(
                R.string.store_bond_required_button,
                accessory.bondRequired,
            )
            accessory.coinPrice != null && missingCoins > 0 -> getString(
                R.string.store_missing_coins_button,
                missingCoins,
            )
            accessory.coinPrice != null -> getString(R.string.store_unlock_coins_button, accessory.coinPrice)
            else -> getString(R.string.store_buy_button)
        }
        button.setOnClickListener {
            lifecycleScope.launch {
                when {
                    owned -> toggleAccessory(accessory, isEquipped)
                    accessory.coinPrice != null -> purchaseAccessoryWithCoins(accessory)
                    else -> billing.launchPurchase(this@StoreActivity, accessory.productId) { success ->
                        lifecycleScope.launch {
                            if (success) {
                                repository.equipAccessory(selectedPet, accessory.id)
                                notifyAccessoryChanged(accessory, celebrate = true)
                                refreshStore()
                            }
                        }
                    }
                }
            }
        }
        view.contentDescription = getString(
            R.string.store_item_content_description,
            accessory.displayName,
            stateText,
            view.findViewById<TextView>(R.id.txtOfferPrice).text,
        )
        return view
    }

    private suspend fun toggleAccessory(accessory: AccessoryCatalogItem, isEquipped: Boolean) {
        val didUpdate = repository.equipAccessory(selectedPet, if (isEquipped) null else accessory.id)
        if (!didUpdate) return
        if (isEquipped) {
            PetService.requestPetRefresh(this, getString(R.string.store_unequipped_bubble), celebrate = false)
        } else {
            notifyAccessoryChanged(accessory, celebrate = true)
        }
        refreshStore()
    }

    private suspend fun purchaseAccessoryWithCoins(accessory: AccessoryCatalogItem) {
        when (repository.purchaseAccessoryWithCoins(selectedPet, accessory.id)) {
            AccessoryPurchaseResult.PURCHASED,
            AccessoryPurchaseResult.ALREADY_OWNED -> {
                repository.equipAccessory(selectedPet, accessory.id)
                Toast.makeText(this, R.string.store_purchase_success, Toast.LENGTH_SHORT).show()
                analytics.track(
                    "accessory_coin_purchase",
                    mapOf("pet_id" to selectedPet.name.lowercase(), "accessory_id" to accessory.id),
                )
                notifyAccessoryChanged(accessory, celebrate = true)
            }
            AccessoryPurchaseResult.NOT_ENOUGH_COINS -> {
                Toast.makeText(this, R.string.store_not_enough_coins, Toast.LENGTH_SHORT).show()
            }
            AccessoryPurchaseResult.BOND_REQUIRED -> {
                Toast.makeText(this, R.string.store_bond_required, Toast.LENGTH_SHORT).show()
            }
            AccessoryPurchaseResult.NOT_AVAILABLE -> {
                Toast.makeText(this, R.string.store_accessory_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        refreshStore()
    }

    private fun notifyAccessoryChanged(accessory: AccessoryCatalogItem, celebrate: Boolean) {
        PetService.requestPetRefresh(
            this,
            getString(R.string.store_equipped_bubble, accessory.emoji),
            celebrate,
        )
    }

    private fun showLoadingState(message: String) {
        txtStoreStatus.text = message
        txtStoreStatus.setTextColor(ContextCompat.getColor(this, R.color.status_info_fg))
        cardStoreState.setBackgroundResource(R.drawable.bg_status_info)
        progressStoreLoading.visibility = View.VISIBLE
        btnStoreRetry.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        txtStoreStatus.text = message
        txtStoreStatus.setTextColor(ContextCompat.getColor(this, R.color.red_error))
        cardStoreState.setBackgroundResource(R.drawable.bg_status_error)
        progressStoreLoading.visibility = View.GONE
        btnStoreRetry.visibility = View.VISIBLE
    }

    private fun edgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarsInsets() {
        val view = findViewById<ScrollView>(R.id.storeScroll)
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
}
