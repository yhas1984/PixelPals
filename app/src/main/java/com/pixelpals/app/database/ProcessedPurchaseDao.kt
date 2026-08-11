package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProcessedPurchaseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProcessedPurchaseEntity): Long

    @Query(
        "UPDATE processed_purchase SET consumedAt = :timestamp " +
            "WHERE purchaseToken = :purchaseToken"
    )
    suspend fun markConsumed(purchaseToken: String, timestamp: Long = System.currentTimeMillis())

    @Query(
        "UPDATE processed_purchase SET lastSeenAt = :timestamp " +
            "WHERE purchaseToken = :purchaseToken AND productId = :productId"
    )
    suspend fun markSeen(
        purchaseToken: String,
        productId: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE processed_purchase SET acknowledgedAt = :timestamp " +
            "WHERE purchaseToken = :purchaseToken AND productId = :productId"
    )
    suspend fun markAcknowledged(
        purchaseToken: String,
        productId: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        "SELECT * FROM processed_purchase " +
            "WHERE purchaseToken = :purchaseToken AND productId = :productId LIMIT 1"
    )
    suspend fun get(purchaseToken: String, productId: String): ProcessedPurchaseEntity?
}
