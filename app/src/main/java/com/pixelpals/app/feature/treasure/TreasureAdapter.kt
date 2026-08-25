package com.pixelpals.app.feature.treasure

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.R

class TreasureAdapter(
    private val onTreasureClicked: (TreasureCollectionItem) -> Unit,
) : ListAdapter<TreasureCollectionItem, TreasureAdapter.TreasureViewHolder>(TREASURE_DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreasureViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(R.layout.item_treasure, parent, false)
        return TreasureViewHolder(view)
    }

    override fun onBindViewHolder(holder: TreasureViewHolder, position: Int): Unit = holder.bind(getItem(position))

    inner class TreasureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emojiText: TextView = itemView.findViewById(R.id.tvEmoji)
        private val nameText: TextView = itemView.findViewById(R.id.tvTreasureName)
        private val storyText: TextView = itemView.findViewById(R.id.tvTreasureStory)
        private val countText: TextView = itemView.findViewById(R.id.tvCount)
        private val favoriteText: TextView = itemView.findViewById(R.id.tvFavorite)

        fun bind(item: TreasureCollectionItem): Unit {
            val context = itemView.context
            emojiText.text = if (item.isDiscovered) item.emoji else context.getString(R.string.treasure_mystery_symbol)
            emojiText.alpha = if (item.isDiscovered) 1f else 0.38f
            nameText.text = if (item.isDiscovered) item.name else context.getString(R.string.treasure_mystery_name)
            storyText.text = if (item.isDiscovered) item.story else item.hint
            countText.text = getCountText(item)
            favoriteText.isVisible = item.isDiscovered
            favoriteText.text = context.getString(
                if (item.isFavorite) R.string.treasure_favorite_label else R.string.treasure_regular_label,
            )
            itemView.alpha = if (item.isDiscovered) 1f else 0.82f
            itemView.isClickable = item.canGift
            itemView.isFocusable = item.canGift
            itemView.contentDescription = getContentDescription(item)
            itemView.setOnClickListener(if (item.canGift) View.OnClickListener { onTreasureClicked(item) } else null)
        }

        private fun getCountText(item: TreasureCollectionItem): String {
            val context = itemView.context
            if (!item.isDiscovered) return context.getString(R.string.treasure_not_discovered)
            if (item.inventoryCount <= 0) return context.getString(R.string.treasure_no_duplicates)
            return context.resources.getQuantityString(
                R.plurals.treasure_inventory_count,
                item.inventoryCount,
                item.inventoryCount,
            )
        }

        private fun getContentDescription(item: TreasureCollectionItem): String {
            val context = itemView.context
            if (!item.isDiscovered) {
                return context.getString(R.string.treasure_mystery_content_description, item.hint)
            }
            return context.getString(
                R.string.treasure_discovered_content_description,
                item.name,
                getCountText(item),
                context.getString(if (item.isFavorite) R.string.treasure_favorite_label else R.string.treasure_regular_label),
                item.story,
            )
        }
    }

    companion object {
        private val TREASURE_DIFF: DiffUtil.ItemCallback<TreasureCollectionItem> =
            object : DiffUtil.ItemCallback<TreasureCollectionItem>() {
                override fun areItemsTheSame(
                    oldItem: TreasureCollectionItem,
                    newItem: TreasureCollectionItem,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: TreasureCollectionItem,
                    newItem: TreasureCollectionItem,
                ): Boolean = oldItem == newItem
            }
    }
}
