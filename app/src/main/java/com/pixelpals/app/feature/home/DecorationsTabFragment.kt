package com.pixelpals.app.feature.home

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.pixelpals.app.R
import com.pixelpals.app.data.repository.CoinSpendResult
import kotlinx.coroutines.launch

class DecorationsTabFragment : Fragment() {
    private lateinit var model: CompanionViewModel
    private var body: LinearLayout? = null
    override fun onCreate(savedInstanceState: Bundle?): Unit { super.onCreate(savedInstanceState); model = ViewModelProvider(this)[CompanionViewModel::class.java] }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ScrollView(requireContext()).apply { addView(HomeUi.column(context).also { body = it }) }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { model.world.collect { render(it) } }
                launch { model.error.collect { if (it) { Toast.makeText(requireContext(), R.string.home_error, Toast.LENGTH_LONG).show(); model.error.value = false } } }
            }
        }
    }
    private fun render(world: CompanionWorld): Unit {
        val column: LinearLayout = body ?: return
        column.removeAllViews()
        column.addView(HomeUi.text(requireContext(), getString(R.string.home_decoration_shop), 26f))
        DecorationCatalog.all.forEach { item ->
            val owned: Boolean = world.inventory.any { it.decorationId == item.id }
            val card: LinearLayout = HomeUi.card(requireContext())
            card.addView(DecorationPreview(requireContext(), item), LinearLayout.LayoutParams(-1, HomeUi.dp(requireContext(), 100)))
            card.addView(HomeUi.text(requireContext(), getString(item.title), 19f))
            card.addView(HomeUi.button(requireContext(), getString(if (owned) R.string.home_owned else R.string.home_preview)) { preview(item, owned) })
            column.addView(card)
        }
    }
    private fun preview(item: Decoration, owned: Boolean): Unit {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext()).setTitle(item.title)
            .setView(DecorationPreview(requireContext(), item))
            .setNegativeButton(android.R.string.cancel, null)
        if (owned) builder.setPositiveButton(R.string.home_place) { _, _ ->
            DecorationDialogs.show(requireContext(), item, model.world.value, model.pet.value, model)
        } else if (item.expedition != null) builder.setMessage(R.string.home_reward)
        else builder.setPositiveButton(getString(R.string.home_buy, item.price)) { _, _ ->
            model.perform {
                val result: CoinSpendResult = model.repository.purchase(item.id)
                if (result == CoinSpendResult.InsufficientFunds) Toast.makeText(requireContext(), R.string.home_no_coins, Toast.LENGTH_LONG).show()
                if (result is CoinSpendResult.Failure) error(result.reason)
                (parentFragment as? com.pixelpals.app.feature.store.StoreFragment)?.getStoreViewModel()?.refresh()
            }
        }
        builder.show()
    }
    override fun onResume(): Unit { super.onResume(); model.refresh() }
    override fun onDestroyView(): Unit { body = null; super.onDestroyView() }
}
