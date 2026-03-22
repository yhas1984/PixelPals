package com.pixelpals.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.database.TreasureItem
import java.text.SimpleDateFormat
import java.util.*

class TreasureAdapter(private var items: List<TreasureItem> = emptyList()) :
    RecyclerView.Adapter<TreasureAdapter.TreasureViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

    fun submitList(newItems: List<TreasureItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreasureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_treasure, parent, false)
        return TreasureViewHolder(view)
    }

    override fun onBindViewHolder(holder: TreasureViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class TreasureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEmoji: TextView = itemView.findViewById(R.id.tvEmoji)
        private val tvCount: TextView = itemView.findViewById(R.id.tvCount)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(item: TreasureItem) {
            tvEmoji.text = item.emoji
            tvCount.text = "Coleccionado: x${item.count}"
            val dateStr = dateFormat.format(Date(item.lastFoundAt))
            tvDate.text = "Última vez: $dateStr"
        }
    }
}
