package com.pixelpals.app.feature.home

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootNavigator
import com.pixelpals.app.R
import com.pixelpals.app.data.repository.CoinSpendResult
import kotlinx.coroutines.launch

class DecorationsTabFragment : Fragment() {
    private lateinit var model: CompanionViewModel
    private var body: LinearLayout? = null
    private var previewDialog: AlertDialog? = null
    private var purchaseStatus: TextView? = null
    override fun onCreate(savedInstanceState: Bundle?): Unit { super.onCreate(savedInstanceState); model = ViewModelProvider(this)[CompanionViewModel::class.java] }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(HomeUi.text(context, getString(R.string.home_purchase_processing), 16f).also {
                purchaseStatus = it
                it.visibility = View.GONE
                it.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            })
            addView(androidx.core.widget.NestedScrollView(context).apply { addView(HomeUi.column(context).also { body = it }) },
                LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { model.world.collect { render(it) } }
                launch { model.busy.collect { updatePurchaseState(it) } }
                launch { model.error.collect { if (it) { Toast.makeText(requireContext(), R.string.home_error, Toast.LENGTH_LONG).show(); model.error.value = false } } }
            }
        }
    }
    private fun updatePurchaseState(busy: Boolean): Unit {
        purchaseStatus?.visibility = if (busy) View.VISIBLE else View.GONE
        previewDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
    }
    private fun render(world: CompanionWorld): Unit {
        val column: LinearLayout = body ?: return
        column.removeAllViews()
        column.addView(HomeUi.text(requireContext(), getString(R.string.home_decoration_shop), 26f))
        DecorationCatalog.all.forEach { item ->
            val owned: Boolean = world.inventory.any { it.decorationId == item.id }
            val card: LinearLayout = HomeUi.card(requireContext())
            card.addView(DecorationPreview(requireContext(), item, model.pet.value), LinearLayout.LayoutParams(-1, HomeUi.dp(requireContext(), 100)))
            card.addView(HomeUi.text(requireContext(), DecorationPresentation.title(requireContext(), item, model.pet.value), 19f))
            card.addView(HomeUi.button(requireContext(), getString(if (owned) R.string.home_owned else R.string.home_preview)) { preview(item, owned) })
            column.addView(card)
        }
    }
    private fun preview(item: Decoration, owned: Boolean): Unit {
        previewDialog?.dismiss()
        val messageContext: android.content.Context = requireContext().applicationContext
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext()).setTitle(DecorationPresentation.title(requireContext(), item, model.pet.value))
            .setView(DecorationPreview(requireContext(), item, model.pet.value))
            .setNegativeButton(android.R.string.cancel, null)
        if (owned) builder.setPositiveButton(R.string.home_place) { _, _ ->
            val navigator: RootNavigator = activity as? RootNavigator ?: return@setPositiveButton
            requireActivity().supportFragmentManager.setFragmentResult(LivingHomeFragment.PLACE_OBJECT_RESULT,
                Bundle().apply {
                    putString("decoration", item.id)
                    putString("pet", model.pet.value.name)
                })
            navigator.navigate(PixelPalsDestination.HOME)
        } else if (item.expedition != null) builder.setMessage(R.string.home_reward)
        else builder.setPositiveButton(getString(R.string.home_buy, item.price)) { _, _ ->
            model.perform {
                val result: CoinSpendResult = model.repository.purchase(item.id)
                if (result == CoinSpendResult.InsufficientFunds) Toast.makeText(messageContext, R.string.home_no_coins, Toast.LENGTH_LONG).show()
                if (result is CoinSpendResult.Failure) error(result.reason)
                (parentFragment as? com.pixelpals.app.feature.store.StoreFragment)?.getStoreViewModel()?.refresh()
            }
        }
        previewDialog = builder.create().also { dialog ->
            dialog.setOnDismissListener { if (previewDialog === dialog) previewDialog = null }
            dialog.show()
            updatePurchaseState(model.busy.value)
        }
    }
    override fun onResume(): Unit { super.onResume(); model.refresh() }
    override fun onDestroyView(): Unit {
        previewDialog?.dismiss()
        previewDialog = null
        purchaseStatus = null
        body = null
        super.onDestroyView()
    }
}
