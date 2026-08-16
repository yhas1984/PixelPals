package com.pixelpals.app.feature.store

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pixelpals.app.PetSelectionActivity
import com.pixelpals.app.R
import com.pixelpals.app.data.catalog.PetCatalogItem
import kotlinx.coroutines.launch

class PetsTabFragment : Fragment() {
    private lateinit var root: LinearLayout
    private lateinit var storeActivity: StoreActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val scroll = inflater.inflate(R.layout.fragment_store_scroll, container, false) as android.widget.ScrollView
        root = scroll.findViewById(R.id.scrollContent)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storeActivity = requireActivity() as StoreActivity
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                storeActivity.getStoreViewModel().uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: StoreUiState) {
        root.removeAllViews()
        if (state.isLoading) {
            root.addView(TextView(requireContext()).apply {
                text = getString(R.string.store_loading)
                setTextColor(requireContext().getColor(R.color.text_secondary))
                setPadding(4, 32, 4, 16)
            })
            return
        }
        val items = state.lockedPremiumPets
        if (items.isEmpty()) {
            root.addView(TextView(requireContext()).apply {
                text = getString(R.string.store_all_premium_owned)
                setTextColor(requireContext().getColor(R.color.text_primary))
                textSize = 16f
                setPadding(4, 32, 4, 16)
            })
            root.addView(Button(requireContext()).apply {
                text = getString(R.string.store_go_to_pets)
                isAllCaps = false
                setOnClickListener { startActivity(Intent(requireContext(), PetSelectionActivity::class.java)) }
            })
            return
        }
        root.addView(TextView(requireContext()).apply {
            text = getString(R.string.store_category_premium)
            setTextColor(requireContext().getColor(R.color.text_primary))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(4, 24, 4, 10)
        })
        val inflater = layoutInflater
        items.forEach { root.addView(buildPetCard(inflater, it, state.activeActionId)) }
    }

    private fun buildPetCard(inflater: LayoutInflater, item: PetCatalogItem, activeActionId: String?): View {
        val card = inflater.inflate(R.layout.item_pet_catalog, root, false)
        card.isClickable = false
        card.isFocusable = false
        card.findViewById<ImageView>(R.id.imgPetPreview).setImageResource(item.previewResId)
        card.findViewById<TextView>(R.id.txtPetName).text = item.displayName
        card.findViewById<TextView>(R.id.txtPetDesc).text = item.description.replace('\n', ' ')
        card.findViewById<TextView>(R.id.txtPetBadge).text = getString(R.string.selection_premium_badge)
        card.findViewById<TextView>(R.id.txtPetState).text = getString(R.string.selection_locked_state)
        card.findViewById<TextView>(R.id.txtPetPrice).apply {
            text = getString(R.string.cosmetic_price_format, item.coinPrice ?: 0)
            visibility = View.VISIBLE
        }
        card.findViewById<Button>(R.id.btnPetAction).apply {
            text = getString(R.string.store_buy_pet_with_coins, item.coinPrice ?: 0)
            isEnabled = activeActionId == null
            setOnClickListener { showUnlockConfirmation(item) }
        }
        return card
    }

    private fun showUnlockConfirmation(item: PetCatalogItem) {
        val price = item.coinPrice ?: return
        val balance = storeActivity.getStoreViewModel().uiState.value.balance
        if (balance < price) {
            storeActivity.getStoreViewModel().setMessage(
                getString(R.string.store_insufficient_coins),
                isError = true,
                canRetry = false,
                canOpenCoins = true,
            )
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.store_confirm_purchase_title)
            .setMessage(getString(R.string.store_confirm_pet_purchase, item.displayName, price, balance - price))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.store_buy_button) { _, _ -> storeActivity.getStoreViewModel().unlockPremiumPet(item) }
            .show()
    }
}
