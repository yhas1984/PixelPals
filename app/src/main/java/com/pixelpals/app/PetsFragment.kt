package com.pixelpals.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.databinding.ActivityPetSelectionBinding
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootNavigator
import com.pixelpals.app.navigation.StoreSection
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetDashboardActivity
import com.pixelpals.app.status.PetMood
import kotlinx.coroutines.launch

class PetsFragment : Fragment() {
    private var bindingReference: ActivityPetSelectionBinding? = null
    private val binding: ActivityPetSelectionBinding
        get() = requireNotNull(bindingReference)
    private val viewModel: PetsViewModel by lazy {
        ViewModelProvider(this)[PetsViewModel::class.java]
    }
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }
    private lateinit var adapter: PetCatalogAdapter
    private var isLaunchingPet: Boolean = false
    private var lastTrackedCatalogKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val inflatedBinding: ActivityPetSelectionBinding = ActivityPetSelectionBinding.inflate(
            inflater,
            container,
            false,
        )
        bindingReference = inflatedBinding
        return inflatedBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = PetCatalogAdapter(PetCatalogMode.SELECTION, ::handlePetAction)
        binding.catalogList.layoutManager = LinearLayoutManager(requireContext())
        binding.catalogList.adapter = adapter
        binding.btnOpenDashboard.setOnClickListener {
            startActivity(Intent(requireContext(), PetDashboardActivity::class.java))
        }
        collectUiState()
    }

    override fun onResume() {
        super.onResume()
        isLaunchingPet = false
        viewModel.refreshIfNeeded(isForced = true)
    }

    override fun onDestroyView() {
        binding.catalogList.adapter = null
        bindingReference = null
        super.onDestroyView()
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: PetsUiState) {
        binding.progressSelection.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        val errorMessage: String? = state.errorMessage
        if (errorMessage != null) {
            renderError(errorMessage)
            adapter.submitList(emptyList())
            return
        }
        val snapshot = state.snapshot ?: return
        val selectedCount: Int = state.items.count { it.state == CatalogItemState.SELECTED }
        val ownedCount: Int = state.items.count { it.state == CatalogItemState.OWNED }
        val lockedCount: Int = state.items.count { it.state == CatalogItemState.LOCKED }
        binding.txtCurrentMood.text = getString(
            R.string.selection_current_pet_format,
            getString(state.selectedPet.displayNameResId),
            getMoodLabel(snapshot.mood),
            snapshot.bond,
        )
        binding.txtCurrentMood.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        binding.txtCatalogSummary.text = getString(
            R.string.selection_catalog_summary_format,
            selectedCount,
            ownedCount,
            lockedCount,
        )
        binding.txtCatalogSummary.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.status_info_fg),
        )
        binding.txtSelectionHint.text = getString(
            R.string.selection_hint_format,
            getCareActionLabel(snapshot.dominantSuggestion),
            snapshot.careStreakDays,
        )
        binding.txtSelectionHint.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.text_secondary),
        )
        adapter.submitList(state.items.map(::PetCatalogRow))
        trackCatalogOnce(state)
    }

    private fun renderError(message: String) {
        val localizedMessage: String = getString(R.string.selection_error)
        binding.txtCurrentMood.text = localizedMessage
        binding.txtCatalogSummary.text = localizedMessage
        binding.txtSelectionHint.text = message
        val errorColor: Int = ContextCompat.getColor(requireContext(), R.color.red_error)
        binding.txtCurrentMood.setTextColor(errorColor)
        binding.txtCatalogSummary.setTextColor(errorColor)
        binding.txtSelectionHint.setTextColor(errorColor)
    }

    private fun trackCatalogOnce(state: PetsUiState) {
        val key: String = "${state.selectedPet.name}:${state.items.size}"
        if (lastTrackedCatalogKey == key) return
        lastTrackedCatalogKey = key
        analytics.track(
            "selection_opened",
            mapOf(
                "selected_pet" to state.selectedPet.name.lowercase(),
                "catalog_size" to state.items.size.toString(),
            ),
        )
    }

    private fun handlePetAction(item: PetCatalogItem) {
        when (item.state) {
            CatalogItemState.LOCKED -> (requireActivity() as RootNavigator).navigate(
                PixelPalsDestination.STORE,
                StoreSection.PREMIUM,
            )
            CatalogItemState.OWNED,
            CatalogItemState.SELECTED -> item.petType?.let(::launchPet)
        }
    }

    private fun launchPet(petType: PetType) {
        if (isLaunchingPet) return
        isLaunchingPet = true
        analytics.track(
            "pet_selected",
            mapOf(
                "pet_id" to petType.name.lowercase(),
                "display_name" to getString(petType.displayNameResId),
            ),
        )
        PetService.requestPetChange(requireContext(), petType)
        Toast.makeText(
            requireContext(),
            getString(R.string.selection_launching_pet_format, getString(petType.displayNameResId)),
            Toast.LENGTH_SHORT,
        ).show()
        viewModel.refreshIfNeeded(isForced = true)
        requireActivity().moveTaskToBack(true)
    }

    private fun getMoodLabel(mood: PetMood): String = getString(
        when (mood) {
            PetMood.HAPPY -> R.string.mood_happy
            PetMood.SLEEPY -> R.string.mood_sleepy
            PetMood.HUNGRY -> R.string.mood_hungry
            PetMood.DIRTY -> R.string.mood_dirty
            PetMood.BORED -> R.string.mood_bored
            PetMood.EXCITED -> R.string.mood_excited
        },
    )

    private fun getCareActionLabel(action: CareAction): String = getString(
        when (action) {
            CareAction.FEED -> R.string.action_feed
            CareAction.CLEAN -> R.string.action_clean
            CareAction.PLAY -> R.string.action_play
            CareAction.REST -> R.string.action_rest
            CareAction.CHECK_IN -> R.string.action_check_in
        },
    )
}
