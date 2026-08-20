package com.pixelpals.app.feature.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.R
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticEffect

sealed interface CosmeticCatalogRow {
    val stableId: String

    data class Header(
        override val stableId: String,
        val titleResource: Int,
    ) : CosmeticCatalogRow

    data class Item(
        val cosmetic: Cosmetic,
        val action: CosmeticAction,
        val isActionEnabled: Boolean,
    ) : CosmeticCatalogRow {
        override val stableId: String = cosmetic.id
    }
}

class CosmeticCatalogAdapter(
    private val onAction: (Cosmetic) -> Unit,
) : ListAdapter<CosmeticCatalogRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {
    companion object {
        private const val VIEW_TYPE_HEADER: Int = 0
        private const val VIEW_TYPE_ITEM: Int = 1
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CosmeticCatalogRow>() {
            override fun areItemsTheSame(
                oldItem: CosmeticCatalogRow,
                newItem: CosmeticCatalogRow,
            ): Boolean = oldItem.stableId == newItem.stableId

            override fun areContentsTheSame(
                oldItem: CosmeticCatalogRow,
                newItem: CosmeticCatalogRow,
            ): Boolean = oldItem == newItem
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId.hashCode().toLong()

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CosmeticCatalogRow.Header -> VIEW_TYPE_HEADER
        is CosmeticCatalogRow.Item -> VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater: LayoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_store_category_header, parent, false))
        } else {
            CosmeticViewHolder(inflater.inflate(R.layout.item_cosmetic, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row: CosmeticCatalogRow = getItem(position)) {
            is CosmeticCatalogRow.Header -> (holder as HeaderViewHolder).bind(row)
            is CosmeticCatalogRow.Item -> (holder as CosmeticViewHolder).bind(row, onAction)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.storeCategoryTitle)

        fun bind(row: CosmeticCatalogRow.Header) {
            title.setText(row.titleResource)
        }
    }

    class CosmeticViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emoji: TextView = itemView.findViewById(R.id.txtCosmeticEmoji)
        private val title: TextView = itemView.findViewById(R.id.txtCosmeticTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.txtCosmeticSubtitle)
        private val price: TextView = itemView.findViewById(R.id.txtCosmeticPrice)
        private val action: Button = itemView.findViewById(R.id.btnCosmeticAction)

        fun bind(row: CosmeticCatalogRow.Item, onAction: (Cosmetic) -> Unit) {
            val context = itemView.context
            val cosmetic: Cosmetic = row.cosmetic
            emoji.text = getPreviewEmoji(cosmetic.effect)
            title.text = cosmetic.displayName
            subtitle.text = cosmetic.description
            val isPurchasable: Boolean = row.action == CosmeticAction.BUY && cosmetic.coinPrice != null
            price.visibility = if (isPurchasable) View.VISIBLE else View.GONE
            price.text = cosmetic.coinPrice?.let {
                context.getString(R.string.cosmetic_price_format, it)
            }.orEmpty()
            action.text = when (row.action) {
                CosmeticAction.BUY -> context.getString(
                    R.string.store_buy_cosmetic_with_coins,
                    cosmetic.coinPrice ?: 0,
                )
                CosmeticAction.EQUIP -> context.getString(R.string.store_equip_button)
                CosmeticAction.EQUIPPED -> context.getString(R.string.store_equipped_button)
            }
            action.isEnabled = row.isActionEnabled && row.action != CosmeticAction.EQUIPPED
            action.alpha = if (action.isEnabled) 1f else 0.55f
            action.setOnClickListener { onAction(cosmetic) }
        }

        private fun getPreviewEmoji(effect: CosmeticEffect): String = when (effect) {
            is CosmeticEffect.TintEffect -> "🎨"
            is CosmeticEffect.AuraEffect -> effect.emoji
            is CosmeticEffect.FloatEffect -> effect.emoji
        }
    }
}
