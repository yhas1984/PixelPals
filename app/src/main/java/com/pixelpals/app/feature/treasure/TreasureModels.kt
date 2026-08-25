package com.pixelpals.app.feature.treasure

data class TreasureCollection(
    val summary: TreasureCollectionSummary,
    val items: List<TreasureCollectionItem>,
)

data class TreasureCollectionSummary(
    val discoveredCount: Int,
    val totalCount: Int,
    val badge: TreasureBadge,
    val nextMilestone: Int?,
    val nextRewardCoins: Int?,
    val isPetActive: Boolean,
    val hasGiftedToday: Boolean,
    val currentBond: Int,
) {
    val canGiftToday: Boolean
        get() = isPetActive && !hasGiftedToday
}

data class TreasureCollectionItem(
    val id: String,
    val emoji: String,
    val name: String,
    val story: String,
    val hint: String,
    val inventoryCount: Int,
    val totalFound: Int,
    val lastFoundAt: Long,
    val isFavorite: Boolean,
    val canGift: Boolean,
) {
    val isDiscovered: Boolean
        get() = totalFound > 0
}

enum class TreasureBadge(
    val milestone: Int,
    val rewardCoins: Int,
) {
    NONE(0, 0),
    BRONZE(5, 25),
    SILVER(10, 50),
    GOLD(15, 75),
    LEGENDARY(19, 150),
    ;

    companion object {
        fun getForProgress(discoveredCount: Int): TreasureBadge = entries
            .last { badge -> badge.milestone <= discoveredCount }

        fun getNext(discoveredCount: Int): TreasureBadge? = entries
            .firstOrNull { badge -> badge.milestone > discoveredCount }

        fun getRewardsAfter(lastMilestone: Int, discoveredCount: Int): List<TreasureBadge> = entries
            .filter { badge -> badge.milestone > lastMilestone && badge.milestone <= discoveredCount }
            .filterNot { badge -> badge == NONE }
    }
}

data class TreasureDiscoveryResult(
    val treasureId: String,
    val emoji: String,
    val isNewDiscovery: Boolean,
    val coinsGained: Int,
    val bondGained: Int,
    val milestone: TreasureBadge?,
)

sealed interface TreasureGiftResult {
    data class Success(
        val treasureId: String,
        val emoji: String,
        val isFavorite: Boolean,
        val bondGained: Int,
        val remainingCount: Int,
    ) : TreasureGiftResult

    data object PetNotActive : TreasureGiftResult
    data object AlreadyGiftedToday : TreasureGiftResult
    data object TreasureUnavailable : TreasureGiftResult
    data object MaximumBondConfirmationRequired : TreasureGiftResult
}
