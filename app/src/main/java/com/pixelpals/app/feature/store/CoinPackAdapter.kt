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
import com.pixelpals.app.data.catalog.CoinProduct

data class CoinPackRow(
    val product: CoinProduct,
    val formattedPrice: String?,
    val isEnabled: Boolean,
    val isActive: Boolean,
)

class CoinPackAdapter(
    private val onPurchase: (CoinProduct) -> Unit,
) : ListAdapter<CoinPackRow, CoinPackAdapter.CoinPackViewHolder>(DIFF_CALLBACK) {
    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CoinPackRow>() {
            override fun areItemsTheSame(oldItem: CoinPackRow, newItem: CoinPackRow): Boolean =
                oldItem.product.productId == newItem.product.productId

            override fun areContentsTheSame(oldItem: CoinPackRow, newItem: CoinPackRow): Boolean =
                oldItem == newItem
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).product.productId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoinPackViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_coin_pack, parent, false)
        return CoinPackViewHolder(view)
    }

    override fun onBindViewHolder(holder: CoinPackViewHolder, position: Int) {
        holder.bind(getItem(position), onPurchase)
    }

    class CoinPackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.txtCoinPackTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.txtCoinPackSubtitle)
        private val badge: TextView = itemView.findViewById(R.id.txtCoinPackBadge)
        private val price: TextView = itemView.findViewById(R.id.txtCoinPackPrice)
        private val purchase: Button = itemView.findViewById(R.id.btnCoinPackBuy)

        fun bind(row: CoinPackRow, onPurchase: (CoinProduct) -> Unit) {
            val context = itemView.context
            title.setText(row.product.displayNameResId)
            subtitle.setText(row.product.subtitleResId)
            badge.visibility = if (row.product.bestValueFlag) View.VISIBLE else View.GONE
            badge.setText(R.string.coins_pack_best_value)
            price.text = row.formattedPrice.orEmpty()
            purchase.text = when {
                row.isActive -> context.getString(R.string.store_loading)
                row.formattedPrice == null -> context.getString(R.string.store_product_unavailable)
                else -> context.getString(R.string.store_buy_with_real_money)
            }
            purchase.isEnabled = row.isEnabled
            purchase.alpha = if (row.isEnabled) 1f else 0.55f
            purchase.setOnClickListener { onPurchase(row.product) }
        }
    }
}
