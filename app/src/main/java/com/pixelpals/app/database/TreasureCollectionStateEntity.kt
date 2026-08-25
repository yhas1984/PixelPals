package com.pixelpals.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treasure_collection_state")
data class TreasureCollectionStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastRewardedMilestone: Int = 0,
    val completedAt: Long = 0L,
    val finalCollectorPetId: String = "",
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}
