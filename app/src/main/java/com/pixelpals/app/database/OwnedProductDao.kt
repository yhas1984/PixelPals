package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OwnedProductDao {
    @Query("SELECT * FROM owned_product")
    suspend fun getAll(): List<OwnedProductEntity>

    @Query("SELECT * FROM owned_product WHERE productId = :productId LIMIT 1")
    suspend fun getByProductId(productId: String): OwnedProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OwnedProductEntity)

    @Query("DELETE FROM owned_product WHERE source IN ('billing', 'reconcile')")
    suspend fun deletePlayEntitlements()

    @Query(
        "DELETE FROM owned_product WHERE source IN ('billing', 'reconcile') " +
            "AND productId NOT IN (:activeProductIds)"
    )
    suspend fun deletePlayEntitlementsNotIn(activeProductIds: List<String>)
}
