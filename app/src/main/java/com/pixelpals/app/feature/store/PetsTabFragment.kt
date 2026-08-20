package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelpals.app.PetCatalogAdapter
import com.pixelpals.app.PetCatalogMode
import com.pixelpals.app.PetCatalogRow
import com.pixelpals.app.R
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.databinding.FragmentStoreListBinding
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootNavigator
import kotlinx.coroutines.launch

class PetsTabFragment : Fragment() {
    private var bindingReference: FragmentStoreListBinding? = null
    private val binding: FragmentStoreListBinding
        get() = requireNotNull(bindingReference)
    private lateinit var storeFragment: StoreFragment
    private lateinit var adapter: PetCatalogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val inflatedBinding: FragmentStoreListBinding = FragmentStoreListBinding.inflate(
            inflater,
            container,
            false,
        )
        bindingReference = inflatedBinding
        return inflatedBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storeFragment = requireParentFragment() as StoreFragment
        adapter = PetCatalogAdapter(PetCatalogMode.PREMIUM_STORE, ::handleUnlockRequest)
        binding.storeList.layoutManager = LinearLayoutManager(requireContext())
        binding.storeList.adapter = adapter
        binding.storeEmptyMessage.setText(R.string.store_all_premium_owned)
        binding.storeEmptyAction.setText(R.string.store_go_to_pets)
        binding.storeEmptyAction.setOnClickListener {
            (requireActivity() as RootNavigator).navigate(PixelPalsDestination.PETS)
        }
        collectUiState()
    }

    override fun onDestroyView() {
        binding.storeList.adapter = null
        bindingReference = null
        super.onDestroyView()
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                storeFragment.getStoreViewModel().uiState.collect(::render)
            }
        }
    }

    private fun render(state: StoreUiState) {
        val isEmpty: Boolean = !state.isInitialLoading && state.lockedPremiumPets.isEmpty()
        binding.storeEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.storeList.visibility = if (isEmpty) View.GONE else View.VISIBLE
        val isActionEnabled: Boolean = state.activeOperation == null
        adapter.submitList(
            state.lockedPremiumPets.map { item ->
                PetCatalogRow(item = item, isActionEnabled = isActionEnabled)
            },
        )
    }

    private fun handleUnlockRequest(item: PetCatalogItem) {
        val price: Int = item.coinPrice ?: return
        val viewModel: StoreViewModel = storeFragment.getStoreViewModel()
        val balance: Int = viewModel.uiState.value.balance
        if (balance < price) {
            showInsufficientCoins()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.store_confirm_purchase_title)
            .setMessage(
                getString(
                    R.string.store_confirm_pet_purchase,
                    item.displayName,
                    price,
                    balance - price,
                ),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.store_buy_button) { _, _ ->
                viewModel.unlockPremiumPet(item)
            }
            .show()
    }

    private fun showInsufficientCoins() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.store_insufficient_coins)
            .setMessage(R.string.store_insufficient_coins_detail)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.store_open_coins) { _, _ -> storeFragment.openCoinsTab() }
            .show()
    }
}
