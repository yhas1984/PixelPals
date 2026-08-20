package com.pixelpals.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem

enum class PetCatalogMode {
    SELECTION,
    PREMIUM_STORE,
}

data class PetCatalogRow(
    val item: PetCatalogItem,
    val isActionEnabled: Boolean = true,
)

class PetCatalogAdapter(
    private val mode: PetCatalogMode,
    private val onAction: (PetCatalogItem) -> Unit,
) : ListAdapter<PetCatalogRow, PetCatalogAdapter.PetViewHolder>(DIFF_CALLBACK) {
    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PetCatalogRow>() {
            override fun areItemsTheSame(oldItem: PetCatalogRow, newItem: PetCatalogRow): Boolean =
                oldItem.item.id == newItem.item.id

            override fun areContentsTheSame(oldItem: PetCatalogRow, newItem: PetCatalogRow): Boolean =
                oldItem == newItem
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).item.id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PetViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pet_catalog, parent, false)
        return PetViewHolder(view)
    }

    override fun onBindViewHolder(holder: PetViewHolder, position: Int) {
        holder.bind(getItem(position), mode, onAction)
    }

    class PetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.imgPetPreview)
        private val name: TextView = itemView.findViewById(R.id.txtPetName)
        private val description: TextView = itemView.findViewById(R.id.txtPetDesc)
        private val badge: TextView = itemView.findViewById(R.id.txtPetBadge)
        private val state: TextView = itemView.findViewById(R.id.txtPetState)
        private val price: TextView = itemView.findViewById(R.id.txtPetPrice)
        private val action: Button = itemView.findViewById(R.id.btnPetAction)

        fun bind(
            row: PetCatalogRow,
            mode: PetCatalogMode,
            onAction: (PetCatalogItem) -> Unit,
        ) {
            val context = itemView.context
            val item: PetCatalogItem = row.item
            image.setImageResource(item.previewResId)
            name.text = item.displayName
            description.text = item.description.replace('\n', ' ')
            badge.text = context.getString(
                if (item.isPremium) R.string.selection_premium_badge else R.string.selection_base_badge,
            )
            state.text = getStateText(item)
            val hasPrice: Boolean = item.state == CatalogItemState.LOCKED && item.coinPrice != null
            price.visibility = if (hasPrice) View.VISIBLE else View.GONE
            price.text = item.coinPrice?.let { context.getString(R.string.cosmetic_price_format, it) }.orEmpty()
            action.text = getActionText(item, mode)
            action.isEnabled = row.isActionEnabled
            action.alpha = if (row.isActionEnabled) 1f else 0.5f
            action.setOnClickListener { onAction(item) }
            itemView.setBackgroundResource(
                if (item.state == CatalogItemState.SELECTED && mode == PetCatalogMode.SELECTION) {
                    R.drawable.bg_card_pet_selected
                } else {
                    R.drawable.bg_card_pet
                },
            )
            val accessibleState: String = getStateText(item)
            itemView.contentDescription = context.getString(
                R.string.selection_item_content_description,
                item.displayName,
                accessibleState,
                description.text.toString(),
            )
            image.contentDescription = itemView.contentDescription
            itemView.isClickable = false
            itemView.isFocusable = false
        }

        private fun getStateText(item: PetCatalogItem): String {
            val resource: Int = when (item.state) {
                CatalogItemState.LOCKED -> R.string.selection_locked_state
                CatalogItemState.OWNED -> R.string.selection_owned_state
                CatalogItemState.SELECTED -> R.string.selection_selected_state
            }
            return itemView.context.getString(resource)
        }

        private fun getActionText(item: PetCatalogItem, mode: PetCatalogMode): String {
            if (mode == PetCatalogMode.PREMIUM_STORE) {
                return itemView.context.getString(R.string.store_buy_pet_with_coins, item.coinPrice ?: 0)
            }
            val resource: Int = when (item.state) {
                CatalogItemState.LOCKED -> R.string.selection_unlock_button
                CatalogItemState.OWNED -> R.string.selection_choose_button
                CatalogItemState.SELECTED -> R.string.selection_selected_button
            }
            return itemView.context.getString(resource)
        }
    }
}
