package com.pixelpals.app.database

import androidx.room.Entity

/** Durable local ledger used to make Play purchase fulfillment idempotent. */
@Entity(
    tableName = "processed_purchase",
    primaryKeys = ["purchaseToken", "productId"],
)
data class ProcessedPurchaseEntity(
    val purchaseToken: String,
    val productId: String,
    val quantity: Int,
    val purchaseTime: Long,
    val source: String,
    val grantedAt: Long? = null,
    val consumedAt: Long? = null,
    val acknowledgedAt: Long? = null,
    val lastSeenAt: Long = System.currentTimeMillis(),
)
