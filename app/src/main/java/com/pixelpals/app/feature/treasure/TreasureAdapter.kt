package com.pixelpals.app.feature.treasure

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.pixelpals.app.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.database.TreasureItem
import java.text.DateFormat
import java.util.Date

class TreasureAdapter(
    private val onTreasureClicked: (TreasureItem) -> Unit
) : ListAdapter<TreasureItem, TreasureAdapter.TreasureViewHolder>(TREASURE_DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreasureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_treasure, parent, false)
        return TreasureViewHolder(view)
    }

    override fun onBindViewHolder(holder: TreasureViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TreasureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEmoji: TextView = itemView.findViewById(R.id.tvEmoji)
        private val tvCount: TextView = itemView.findViewById(R.id.tvCount)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(item: TreasureItem) {
            tvEmoji.text = item.emoji
            val context = itemView.context
            tvCount.text = context.getString(R.string.treasure_count_format, item.count)
            val dateStr = formatDate(context, item.lastFoundAt)
            tvDate.text = context.getString(R.string.treasure_last_found_format, dateStr)
            itemView.contentDescription = context.getString(
                R.string.treasure_item_content_description,
                item.emoji,
                tvCount.text,
                tvDate.text
            )
            
            // Allow consuming / using the item
            itemView.setOnClickListener {
                onTreasureClicked(item)
            }
        }

        private fun formatDate(context: android.content.Context, timestamp: Long): String {
            val locale = context.resources.configuration.locales[0]
            val mediumDate = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
            val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
            return "${mediumDate.format(Date(timestamp))} ${timeFormat.format(Date(timestamp))}"
        }
    }

    companion object {
        private val TREASURE_DIFF = object : DiffUtil.ItemCallback<TreasureItem>() {
            override fun areItemsTheSame(oldItem: TreasureItem, newItem: TreasureItem): Boolean {
                return oldItem.emoji == newItem.emoji
            }

            override fun areContentsTheSame(oldItem: TreasureItem, newItem: TreasureItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
