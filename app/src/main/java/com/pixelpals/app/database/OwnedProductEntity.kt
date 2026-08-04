package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "owned_product")
data class OwnedProductEntity(
    @PrimaryKey val productId: String,
    val productType: String,
    val source: String,
    val purchasedAt: Long,
    val restoredAt: Long? = null,
    val acknowledged: Boolean = true
)
