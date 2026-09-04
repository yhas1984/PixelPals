package com.pixelpals.app.data.repository

import android.content.Context
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.R
import com.pixelpals.app.core.care.PetCareState
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.care.PetNeedsEngine
import com.pixelpals.app.core.care.SystemTimeProvider
import com.pixelpals.app.core.care.TimeProvider
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.care.scene.isMedicineAvailable
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.DailyTaskStateEntity
import com.pixelpals.app.database.OwnedProductEntity
import com.pixelpals.app.database.PetBondEntity
import com.pixelpals.app.database.PetStatusEntity
import com.pixelpals.app.database.ProcessedPurchaseEntity
import com.pixelpals.app.database.TreasureItem
import com.pixelpals.app.database.TreasureCollectionStateEntity
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.feature.treasure.TreasureBadge
import com.pixelpals.app.feature.treasure.TreasureCatalog
import com.pixelpals.app.feature.treasure.TreasureCollection
import com.pixelpals.app.feature.treasure.TreasureCollectionItem
import com.pixelpals.app.feature.treasure.TreasureCollectionSummary
import com.pixelpals.app.feature.treasure.TreasureDiscoveryResult
import com.pixelpals.app.feature.treasure.TreasureGiftResult
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.DailyTask
import com.pixelpals.app.status.MemoryMoment
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetPersonality
import com.pixelpals.app.status.PetStatusSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.room.withTransaction
import kotlin.math.max
import kotlin.math.min

sealed interface CoinSpendResult {
    data object Purchased : CoinSpendResult
    data object AlreadyOwned : CoinSpendResult
    data object InsufficientFunds : CoinSpendResult
    data class Failure(val reason: String) : CoinSpendResult
}

class PixelPalsRepository(
    context: Context,
    database: AppDatabase? = null,
    timeProvider: TimeProvider = SystemTimeProvider,
) {
    private val appContext: Context = context.applicationContext
    private val db = database ?: AppDatabase.getDatabase(appContext)
    private val cosmeticPrefs = appContext.getSharedPreferences("pixelpals_cosmetics", Context.MODE_PRIVATE)
    private val coinPrefs = appContext.getSharedPreferences("pixelpals_coins", Context.MODE_PRIVATE)
    private val selectedPetStore = SelectedPetStore(appContext)
    private val timeProvider: TimeProvider = timeProvider
    private val petNeedsEngine = PetNeedsEngine(timeProvider)

    /** Monedero GLOBAL: las monedas son del jugador, no del pet (v1.6+). */
    private val walletId = "wallet"

    private val premiumPetProductIds = mapOf(
        PetType.ANGEL to "pet_angel_premium",
        PetType.DIABLILLO to "pet_diablillo_premium",
        PetType.YUKI to "pet_yuki_premium",
        PetType.PIRU to "pet_piru_premium",
        PetType.TARO to "pet_taro_premium",
        PetType.MENTA to "pet_menta_premium",
        PetType.TELA to "pet_tela_premium",
        PetType.LUMI to "pet_lumi_premium"
    )

    /** Precio en monedas del monedero GLOBAL para desbloquear cada pet premium (null = solo IAP). */
    private val premiumPetCoinPrices = mapOf(
        PetType.ANGEL to 400,
        PetType.DIABLILLO to 350,
        PetType.YUKI to 500,
        PetType.PIRU to 500,
        PetType.TARO to 450,
        PetType.MENTA to 450,
        PetType.TELA to 550,
        PetType.LUMI to 600
    )

    /** Cosmético equipado por petId (null = ninguno). */
    fun getEquippedCosmetic(petId: String): String? =
        cosmeticPrefs.getString(petId, null)?.takeIf { it.isNotBlank() }

    /** Equipa (cosmeticId) o quita (null) el cosmético de un pet. */
    fun setEquippedCosmetic(petId: String, cosmeticId: String?) {
        cosmeticPrefs.edit().apply {
            if (cosmeticId == null) remove(petId) else putString(petId, cosmeticId)
        }.apply()
    }

    /** true si el cosmético (por productId) ya fue comprado. */
    suspend fun isCosmeticOwned(productId: String): Boolean =
        db.ownedProductDao().getByProductId(productId)?.let(::isEligibleEntitlement) == true

    /** Compra un cosmético con monedas del monedero global de forma idempotente. */
    suspend fun purchaseCosmeticWithCoins(petId: String, cosmeticId: String): CoinSpendResult {
        ensureWalletMigrated()
        val cosmetic = com.pixelpals.app.data.catalog.CosmeticCatalog.findById(appContext, cosmeticId)
            ?: return CoinSpendResult.Failure("Unknown cosmetic")
        val price = cosmetic.coinPrice ?: return CoinSpendResult.Failure("Cosmetic has no coin price")
        return db.withTransaction {
            if (db.ownedProductDao().getByProductId(cosmetic.productId)
                    ?.let(::isEligibleEntitlement) == true
            ) {
                return@withTransaction CoinSpendResult.AlreadyOwned
            }
            val wallet = ensureBondEntity(walletId)
            if (wallet.softCurrency < price) return@withTransaction CoinSpendResult.InsufficientFunds
            db.petBondDao().upsert(wallet.copy(softCurrency = wallet.softCurrency - price))
            db.ownedProductDao().upsert(
                com.pixelpals.app.database.OwnedProductEntity(
                    productId = cosmetic.productId,
                    productType = "cosmetic",
                    source = "soft_currency",
                    purchasedAt = System.currentTimeMillis(),
                )
            )
            CoinSpendResult.Purchased
        }
    }

    suspend fun getStatusSnapshot(petType: PetType): PetStatusSnapshot {
        return getStatusSnapshot(petIdOf(petType))
    }

    suspend fun getStatusSnapshot(petId: String): PetStatusSnapshot {
        val statusEntity = ensureStatusEntity(petId)
        var bondEntity = ensureBondEntity(petId)
        val reconciled = reconcileStatus(statusEntity)
        if (reconciled != statusEntity) {
            db.petStatusDao().upsert(reconciled)
        }
        if (hasRecovered(statusEntity, reconciled)) {
            bondEntity = bondEntity.copy(illnessRecoveries = bondEntity.illnessRecoveries + 1)
            db.petBondDao().upsert(bondEntity)
        }
        return toSnapshot(reconciled, bondEntity)
    }

    suspend fun recordActiveMinute(petType: PetType): PetStatusSnapshot {
        val petId = petIdOf(petType)
        val bond = ensureBondEntity(petId)
        db.petBondDao().upsert(bond.copy(activeMinutes = bond.activeMinutes + 1))
        return getStatusSnapshot(petId)
    }

    suspend fun recordInteraction(petType: PetType): PetStatusSnapshot {
        val petId = petIdOf(petType)
        return db.withTransaction {
            val currentStatus = reconcileStatus(ensureStatusEntity(petId))
            val currentBond = ensureBondEntity(petId)
            if (
                currentBond.bondPoints > 0 &&
                System.currentTimeMillis() - currentStatus.lastInteractionAt < INTERACTION_REWARD_COOLDOWN_MS
            ) {
                return@withTransaction getStatusSnapshot(petId)
            }
            applyMutation(petId) {
                copy(
                    energy = (energy + 2).coerceAtMost(100),
                    lastInteractionAt = System.currentTimeMillis(),
                )
            }
            db.petBondDao().upsert(
                currentBond.copy(
                    bondPoints = (currentBond.bondPoints + 3).coerceAtMost(100),
                    memoriesUnlocked = max(
                        currentBond.memoriesUnlocked,
                        memoryCountForBond(currentBond.bondPoints + 3),
                    )
                )
            )
            // Re-leer tras el upsert: el snapshot de applyMutation se construyó con el
            // bond ANTES del +3, y el bond debe reflejarse en lo que ve el usuario.
            getStatusSnapshot(petId)
        }
    }

    suspend fun applyCareAction(petType: PetType, action: CareAction): PetStatusSnapshot {
        val petId = petIdOf(petType)
        val taskId = taskIdFor(action)
        return db.withTransaction {
            val isRewardEligible = action != CareAction.MEDICINE
            val isFirstCompletionToday = isRewardEligible && db.dailyTaskStateDao()
                .getTasksForDay(petId, todayKey())
                .none { it.taskId == taskId }
            if (action == CareAction.CHECK_IN && !isFirstCompletionToday) {
                return@withTransaction getStatusSnapshot(petId)
            }
            val currentBond = ensureBondEntity(petId)
            val currentStatus = reconcileStatus(ensureStatusEntity(petId))
            val caredStatus = petNeedsEngine.applyCare(currentStatus.toCareState(), action)
                .toEntity(currentStatus)
                .normalizeMood()
            db.petStatusDao().upsert(caredStatus)
            if (isFirstCompletionToday) {
                val nextBondPoints = (currentBond.bondPoints + CARE_BOND_REWARD).coerceAtMost(100)
                db.petBondDao().upsert(
                    currentBond.copy(
                        bondPoints = nextBondPoints,
                        memoriesUnlocked = max(currentBond.memoriesUnlocked, memoryCountForBond(nextBondPoints))
                    )
                )
                completeDailyTask(petId, action)
            }
            if (hasRecovered(currentStatus, caredStatus)) {
                val recoveryBond = ensureBondEntity(petId)
                db.petBondDao().upsert(
                    recoveryBond.copy(illnessRecoveries = recoveryBond.illnessRecoveries + 1)
                )
            }
            getStatusSnapshot(petId)
        }
    }

    /** Atomic before/after result for a completed visual action; no animation callback mutates twice. */
    suspend fun completeCareScene(petType: PetType, action: CareSceneAction): CareSceneResult = db.withTransaction {
        val before: PetStatusSnapshot = getStatusSnapshot(petType)
        if (action == CareSceneAction.MEDICINE && !isMedicineAvailable(before, System.currentTimeMillis())) {
            return@withTransaction CareSceneResult.Unavailable
        }
        val after: PetStatusSnapshot = if (action.careAction != null) {
            applyCareAction(petType, action.careAction)
        } else {
            recordInteraction(petType)
        }
        CareSceneResult.Completed(before, after)
    }

    suspend fun getDailyTasks(petType: PetType): List<DailyTask> {
        val petId = petIdOf(petType)
        val today = todayKey()
        val completedIds = db.dailyTaskStateDao().getTasksForDay(petId, today).map { it.taskId }.toSet()
        return listOf(
            DailyTask(
                "check_in",
                appContext.getString(R.string.daily_task_check_in_title),
                appContext.getString(R.string.daily_task_check_in_description),
                8,
                "check_in" in completedIds,
            ),
            DailyTask(
                "feed",
                appContext.getString(R.string.daily_task_feed_title),
                appContext.getString(R.string.daily_task_feed_description),
                14,
                "feed" in completedIds,
            ),
            DailyTask(
                "play",
                appContext.getString(R.string.daily_task_play_title),
                appContext.getString(R.string.daily_task_play_description),
                14,
                "play" in completedIds,
            ),
            DailyTask(
                "clean",
                appContext.getString(R.string.daily_task_clean_title),
                appContext.getString(R.string.daily_task_clean_description),
                12,
                "clean" in completedIds,
            ),
            DailyTask(
                "rest",
                appContext.getString(R.string.daily_task_rest_title),
                appContext.getString(R.string.daily_task_rest_description),
                10,
                "rest" in completedIds,
            ),
        )
    }

    suspend fun getMemories(petType: PetType): List<MemoryMoment> {
        val petId = petIdOf(petType)
        val bond = ensureBondEntity(petId)
        val snapshot = getStatusSnapshot(petId)
        val firstDay = if (bond.firstSeenAt > 0L) {
            val date = java.time.Instant.ofEpochMilli(bond.firstSeenAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            appContext.getString(
                R.string.memory_first_day_since,
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(currentLocale())
                    .format(date),
            )
        } else appContext.getString(R.string.memory_first_day_default)
        val memories = mutableListOf(
            MemoryMoment(
                "first_day",
                appContext.getString(R.string.memory_first_day_title),
                firstDay,
            )
        )
        if (snapshot.bond >= 15) {
            memories += MemoryMoment(
                "bond_15",
                appContext.getString(R.string.memory_bond_15_title),
                appContext.getString(R.string.memory_bond_15_subtitle),
            )
        }
        if (snapshot.careStreakDays >= 3) {
            memories += MemoryMoment(
                "streak_3",
                appContext.getString(R.string.memory_streak_3_title),
                appContext.getString(
                    R.string.memory_streak_3_subtitle,
                    snapshot.careStreakDays,
                ),
            )
        }
        if (snapshot.bond >= 35) {
            memories += MemoryMoment(
                "bond_35",
                appContext.getString(R.string.memory_bond_35_title),
                appContext.getString(R.string.memory_bond_35_subtitle),
            )
        }
        if (snapshot.memoriesUnlocked >= 3) {
            memories += MemoryMoment(
                "bond_50",
                appContext.getString(R.string.memory_bond_50_title),
                appContext.getString(R.string.memory_bond_50_subtitle),
            )
        }
        if (snapshot.careStreakDays >= 7) {
            memories += MemoryMoment(
                "streak_7",
                appContext.getString(R.string.memory_streak_7_title),
                appContext.getString(R.string.memory_streak_7_subtitle),
            )
        }
        if (bond.illnessRecoveries > 0) {
            memories += MemoryMoment(
                "first_recovery",
                appContext.getString(R.string.memory_first_recovery_title),
                appContext.getString(R.string.memory_first_recovery_subtitle),
            )
        }
        val collectionState: TreasureCollectionStateEntity? = db.treasureCollectionStateDao().getState()
        if (collectionState?.finalCollectorPetId == petId && collectionState.completedAt > 0L) {
            memories += MemoryMoment(
                "treasure_collection_complete",
                appContext.getString(R.string.memory_treasure_collection_title),
                appContext.getString(R.string.memory_treasure_collection_subtitle),
            )
        }
        return memories
    }

    suspend fun getCatalog(selectedType: PetType): List<PetCatalogItem> {
        val ownedProducts = db.ownedProductDao().getAll()
            .filter(::isEligibleEntitlement)
            .associateBy { it.productId }
        return PetType.entries.map { petType ->
            val productId = premiumPetProductIds[petType]
            val owned = productId == null || ownedProducts.containsKey(productId)
            val state = when {
                petType == selectedType -> CatalogItemState.SELECTED
                owned -> CatalogItemState.OWNED
                else -> CatalogItemState.LOCKED
            }
            PetCatalogItem(
                id = petIdOf(petType),
                displayName = appContext.getString(petType.displayNameResId),
                description = appContext.getString(petType.descriptionResId),
                previewResId = petType.spriteResId,
                petType = petType,
                productId = productId,
                isPremium = productId != null,
                state = state,
                coinPrice = premiumPetCoinPrices[petType],
                badge = when {
                    productId != null -> appContext.getString(R.string.selection_premium_badge)
                    petType == selectedType -> appContext.getString(R.string.store_badge_current)
                    else -> appContext.getString(R.string.store_badge_base)
                }
            )
        }
    }

    suspend fun grantOwnedProduct(productId: String, source: String) {
        grantOwnedProductInternal(productId, source)
    }

    /**
     * Records and fulfills one Play purchase exactly once on this device.
     * The ledger row and the wallet/entitlement update share one transaction.
     */
    suspend fun grantPlayPurchaseOnce(
        purchaseToken: String,
        productId: String,
        quantity: Int,
        purchaseTime: Long,
        source: String,
    ): Boolean {
        if (purchaseToken.isBlank() || quantity <= 0) return false
        ensureWalletMigrated()
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val inserted = db.processedPurchaseDao().insert(
                ProcessedPurchaseEntity(
                    purchaseToken = purchaseToken,
                    productId = productId,
                    quantity = quantity,
                    purchaseTime = purchaseTime,
                    source = source,
                    grantedAt = now,
                    lastSeenAt = now,
                )
            )
            if (inserted == -1L) return@withTransaction false

            val coinProduct = com.pixelpals.app.data.catalog.CoinProduct.CATALOG
                .firstOrNull { it.productId == productId }
            if (coinProduct != null) {
                val amount = (coinProduct.coinAmount.toLong() * quantity)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                val wallet = ensureBondEntity(walletId)
                db.petBondDao().upsert(wallet.copy(softCurrency = wallet.softCurrency + amount))
            } else {
                grantOwnedProductInternal(productId, source)
            }
            true
        }
    }

    suspend fun markPlayPurchaseConsumed(purchaseToken: String) {
        if (purchaseToken.isBlank()) return
        db.processedPurchaseDao().markConsumed(purchaseToken)
    }

    suspend fun markPlayPurchaseSeen(purchaseToken: String, productId: String) {
        if (purchaseToken.isBlank() || productId.isBlank()) return
        db.processedPurchaseDao().markSeen(purchaseToken, productId)
    }

    suspend fun markPlayPurchaseAcknowledged(purchaseToken: String, productId: String) {
        if (purchaseToken.isBlank() || productId.isBlank()) return
        db.processedPurchaseDao().markAcknowledged(purchaseToken, productId)
    }

    suspend fun reconcilePlayEntitlements(activeProductIds: Set<String>) {
        db.withTransaction {
            if (activeProductIds.isEmpty()) {
                db.ownedProductDao().deletePlayEntitlements()
            } else {
                db.ownedProductDao().deletePlayEntitlementsNotIn(activeProductIds.toList())
            }
        }
    }

    private suspend fun grantOwnedProductInternal(productId: String, source: String) {
        val productType = when {
            productId.startsWith("acc_") -> "accessory"
            productId.startsWith("pet_") -> "pet"
            productId.startsWith("coins_") -> "currency"
            productId.startsWith("pack_") -> "bundle"
            else -> "bundle"
        }
        db.ownedProductDao().upsert(
            OwnedProductEntity(
                productId = productId,
                productType = productType,
                source = source,
                purchasedAt = System.currentTimeMillis(),
                restoredAt = if (source == "restore" || source == "reconcile") {
                    System.currentTimeMillis()
                } else {
                    null
                }
            )
        )
    }

    suspend fun isProductOwned(productId: String): Boolean {
        return db.ownedProductDao().getByProductId(productId)?.let(::isEligibleEntitlement) == true
    }

    /**
     * Compra un pet premium con monedas del MONEDERO GLOBAL.
     * Devuelve un resultado tipado y nunca descuenta dos veces el mismo derecho.
     */
    suspend fun purchasePetWithCoins(petType: PetType): CoinSpendResult {
        ensureWalletMigrated()
        val productId = premiumPetProductIds[petType]
            ?: return CoinSpendResult.Failure("Pet is not purchasable")
        val price = premiumPetCoinPrices[petType]
            ?: return CoinSpendResult.Failure("Pet has no coin price")
        return db.withTransaction {
            val existing = db.ownedProductDao().getByProductId(productId)
            if (existing?.let(::isEligibleEntitlement) == true) {
                return@withTransaction CoinSpendResult.AlreadyOwned
            }
            val wallet = ensureBondEntity(walletId)
            if (wallet.softCurrency < price) return@withTransaction CoinSpendResult.InsufficientFunds
            db.petBondDao().upsert(wallet.copy(softCurrency = wallet.softCurrency - price))
            db.ownedProductDao().upsert(
                OwnedProductEntity(
                    productId = productId,
                    productType = "pet",
                    source = "soft_currency",
                    purchasedAt = System.currentTimeMillis(),
                )
            )
            CoinSpendResult.Purchased
        }
    }

    /**
     * Otorga monedas al MONEDERO GLOBAL (las monedas son del jugador, no del pet).
     * El parámetro [petType] se conserva por compatibilidad de llamadas.
     */
    suspend fun grantCoins(petType: PetType?, amount: Int) {
        ensureWalletMigrated()
        val bond = ensureBondEntity(walletId)
        db.petBondDao().upsert(bond.copy(softCurrency = bond.softCurrency + amount))
    }

    /**
     * Compra un pack de monedas (IAP real): las monedas van al monedero global.
     */
    suspend fun grantCoinPack(coinProduct: com.pixelpals.app.data.catalog.CoinProduct, petType: PetType?, source: String) {
        ensureWalletMigrated()
        db.withTransaction {
            val bond = ensureBondEntity(walletId)
            db.petBondDao().upsert(bond.copy(softCurrency = bond.softCurrency + coinProduct.coinAmount))
        }
    }

    /** Lee el balance del MONEDERO GLOBAL. */
    suspend fun getCoinBalance(petType: PetType?): Int {
        ensureWalletMigrated()
        return ensureBondEntity(walletId).softCurrency
    }

    /**
     * Fusión ÚNICA (v1.6): las monedas guardadas por pet (bug anterior) se
     * suman al monedero global y los saldos por pet se ponen a 0.
     * Idempotente vía flag en preferencias.
     */
    private suspend fun ensureWalletMigrated() {
        val key = "coins_wallet_migrated_v1"
        if (coinPrefs.getBoolean(key, false)) return
        db.withTransaction {
            val bonds = db.petBondDao().getAll()
            val total = bonds.filter { it.petId != walletId }.sumOf { it.softCurrency }
            bonds.filter { it.petId != walletId && it.softCurrency > 0 }.forEach {
                db.petBondDao().upsert(it.copy(softCurrency = 0))
            }
            val wallet = db.petBondDao().getByPetId(walletId) ?: PetBondEntity(petId = walletId)
            db.petBondDao().upsert(wallet.copy(softCurrency = wallet.softCurrency + total))
        }
        coinPrefs.edit().putBoolean(key, true).apply()
    }

    fun getPersonality(petType: PetType): PetPersonality {
        return when (petType) {
            PetType.BLOOP -> PetPersonality.DREAMY
            PetType.NUBE_MICHI -> PetPersonality.SWEET
            PetType.JELLY -> PetPersonality.BOUNCY
            PetType.CORGI -> PetPersonality.LOYAL
            PetType.GINGER -> PetPersonality.ELEGANT
            PetType.ANGEL -> PetPersonality.ANGELIC
            PetType.PATITO -> PetPersonality.CURIOUS
            PetType.DIABLILLO -> PetPersonality.CHAOTIC
            PetType.MOKI -> PetPersonality.CURIOUS
            PetType.YUKI -> PetPersonality.SWEET
            PetType.PIRU -> PetPersonality.BOUNCY
            PetType.TARO -> PetPersonality.ELEGANT
            PetType.MENTA -> PetPersonality.DREAMY
            PetType.TELA -> PetPersonality.CHAOTIC
            PetType.LUMI -> PetPersonality.DREAMY
        }
    }

    private suspend fun completeDailyTask(petId: String, action: CareAction) = db.withTransaction {
        val taskId = taskIdFor(action)
        val dayKey = todayKey()
        val existing = db.dailyTaskStateDao().getTasksForDay(petId, dayKey).associateBy { it.taskId }
        if (existing.containsKey(taskId)) return@withTransaction
        val reward = when (taskId) {
            "feed", "play" -> 14
            "clean" -> 12
            "rest" -> 10
            else -> 8
        }
        db.dailyTaskStateDao().upsert(
            DailyTaskStateEntity(
                id = "$petId:$dayKey:$taskId",
                petId = petId,
                taskId = taskId,
                dayKey = dayKey,
                completedAt = System.currentTimeMillis(),
                rewardCoins = reward
            )
        )
        val bond = ensureBondEntity(petId)
        var streak = bond.careStreakDays
        var lastCompletionDay = bond.lastDailyCompletionDay
        val tasks = db.dailyTaskStateDao().getTasksForDay(petId, dayKey)
        if (tasks.size >= 3 && lastCompletionDay != dayKey) {
            streak = if (lastCompletionDay.isNotBlank() && daysBetween(lastCompletionDay, dayKey) == 1L) {
                bond.careStreakDays + 1
            } else {
                1
            }
            lastCompletionDay = dayKey
        }
        db.petBondDao().upsert(
            bond.copy(
                careStreakDays = streak,
                lastCheckInDay = if (taskId == "check_in") dayKey else bond.lastCheckInDay,
                lastDailyCompletionDay = lastCompletionDay
            )
        )
        // La recompensa en monedas va al MONEDERO GLOBAL (v1.6+).
        ensureWalletMigrated()
        val wallet = ensureBondEntity(walletId)
        db.petBondDao().upsert(wallet.copy(softCurrency = wallet.softCurrency + reward))
    }

    private suspend fun applyMutation(
        petId: String,
        mutation: PetStatusEntity.() -> PetStatusEntity
    ): PetStatusSnapshot = db.withTransaction {
        val current = reconcileStatus(ensureStatusEntity(petId))
        val mutated = mutation(current).normalizeMood()
        db.petStatusDao().upsert(mutated)
        toSnapshot(mutated, ensureBondEntity(petId))
    }

    private fun PetStatusEntity.normalizeMood(): PetStatusEntity {
        val resolvedMood = deriveMood(this)
        val resolvedHealth = ((energy + satiety + hygiene) / 3).coerceIn(20, 100)
        return copy(health = resolvedHealth, mood = resolvedMood.name)
    }

    private suspend fun ensureStatusEntity(petId: String): PetStatusEntity {
        val now = timeProvider.getCurrentTimeMillis()
        return db.petStatusDao().getByPetId(petId) ?: PetStatusEntity(
            petId = petId,
            lastUpdatedAt = now,
            lastInteractionAt = now,
        ).normalizeMood().also {
            db.petStatusDao().upsert(it)
        }
    }

    private suspend fun ensureBondEntity(petId: String): PetBondEntity {
        return db.petBondDao().getByPetId(petId) ?: PetBondEntity(petId = petId).also {
            db.petBondDao().upsert(it)
        }
    }

    private fun toSnapshot(status: PetStatusEntity, bond: PetBondEntity): PetStatusSnapshot {
        val bondLevel = bond.bondPoints.coerceIn(0, 100)
        val mood = runCatching { PetMood.valueOf(status.mood) }.getOrElse { deriveMood(status) }
        val condition = runCatching { PetCondition.valueOf(status.condition) }.getOrDefault(PetCondition.HEALTHY)
        return PetStatusSnapshot(
            petId = status.petId,
            health = status.health,
            energy = status.energy,
            hunger = status.satiety,
            hygiene = status.hygiene,
            bond = bondLevel,
            mood = mood,
            careStreakDays = bond.careStreakDays,
            softCurrency = bond.softCurrency,
            dominantSuggestion = dominantSuggestionFor(status, mood),
            memoriesUnlocked = max(bond.memoriesUnlocked, memoryCountForBond(bondLevel)),
            condition = condition,
            recoveryProgress = status.recoveryProgress,
            medicineAvailableAt = petNeedsEngine.getMedicineAvailableAt(status.toCareState()),
            lastInteractionAt = status.lastInteractionAt,
        )
    }

    private fun dominantSuggestionFor(status: PetStatusEntity, mood: PetMood): CareAction {
        val condition = runCatching { PetCondition.valueOf(status.condition) }.getOrDefault(PetCondition.HEALTHY)
        return when {
            condition == PetCondition.SICK && timeProvider.getCurrentTimeMillis() >= petNeedsEngine.getMedicineAvailableAt(status.toCareState()) -> CareAction.MEDICINE
            status.satiety < 45 -> CareAction.FEED
            status.hygiene < 50 -> CareAction.CLEAN
            status.energy < 45 -> CareAction.REST
            mood == PetMood.BORED -> CareAction.PLAY
            else -> CareAction.CHECK_IN
        }
    }

    private fun deriveMood(entity: PetStatusEntity): PetMood {
        val health = entity.health
        val energy = entity.energy
        val satiety = entity.satiety
        val hygiene = entity.hygiene
        val hoursWithoutAttention = if (isCareActive(entity.petId)) {
            max(
                0L,
                ChronoUnit.HOURS.between(
                    epochMillisToDateTime(entity.lastInteractionAt),
                    epochMillisToDateTime(timeProvider.getCurrentTimeMillis())
                )
            )
        } else {
            0L
        }
        return when {
            satiety < 35 -> PetMood.HUNGRY
            energy < 35 -> PetMood.SLEEPY
            hygiene < 40 -> PetMood.DIRTY
            health > 88 && energy > 70 && satiety > 65 -> PetMood.EXCITED
            hoursWithoutAttention >= 6 -> PetMood.BORED
            health > 70 -> PetMood.HAPPY
            else -> PetMood.BORED
        }
    }

    fun moodLabel(mood: PetMood): String {
        return appContext.getString(
            when (mood) {
                PetMood.HAPPY -> R.string.mood_happy
                PetMood.SLEEPY -> R.string.mood_sleepy
                PetMood.HUNGRY -> R.string.mood_hungry
                PetMood.DIRTY -> R.string.mood_dirty
                PetMood.BORED -> R.string.mood_bored
                PetMood.EXCITED -> R.string.mood_excited
            }
        )
    }

    fun careActionLabel(action: CareAction): String {
        return appContext.getString(
            when (action) {
                CareAction.FEED -> R.string.action_feed
                CareAction.CLEAN -> R.string.action_clean
                CareAction.PLAY -> R.string.action_play
                CareAction.REST -> R.string.action_rest
                CareAction.CHECK_IN -> R.string.action_check_in
                CareAction.MEDICINE -> R.string.action_medicine
            }
        )
    }

    fun personalityLabel(personality: PetPersonality): String {
        return appContext.getString(
            when (personality) {
                PetPersonality.SWEET -> R.string.personality_sweet
                PetPersonality.DREAMY -> R.string.personality_dreamy
                PetPersonality.BOUNCY -> R.string.personality_bouncy
                PetPersonality.LOYAL -> R.string.personality_loyal
                PetPersonality.ELEGANT -> R.string.personality_elegant
                PetPersonality.ANGELIC -> R.string.personality_angelic
                PetPersonality.CURIOUS -> R.string.personality_curious
                PetPersonality.CHAOTIC -> R.string.personality_chaotic
            }
        )
    }

    fun dashboardCompanionLine(petType: PetType, snapshot: PetStatusSnapshot): String {
        return appContext.getString(
            R.string.dashboard_companion_line_format,
            appContext.getString(petType.displayNameResId),
            moodLabel(snapshot.mood),
            personalityLabel(getPersonality(petType)),
        )
    }

    fun selectionSpotlight(petType: PetType, snapshot: PetStatusSnapshot): String {
        return appContext.getString(
            R.string.selection_spotlight_format,
            appContext.getString(petType.displayNameResId),
            moodLabel(snapshot.mood),
            careActionLabel(snapshot.dominantSuggestion),
        )
    }

    fun premiumPetPerk(petType: PetType): String {
        return appContext.getString(
            when (petType) {
                PetType.ANGEL -> R.string.premium_pet_perk_angel
                PetType.DIABLILLO -> R.string.premium_pet_perk_diablillo
                PetType.LUMI -> R.string.premium_pet_perk_lumi
                else -> R.string.premium_pet_perk_default
            }
        )
    }

    private fun isEligibleEntitlement(product: OwnedProductEntity): Boolean {
        return BuildConfig.DEBUG || product.source !in setOf("debug_preview", "preview_unlock")
    }

    private fun petIdOf(petType: PetType): String {
        return when (petType) {
            PetType.BLOOP -> "bloop"
            PetType.NUBE_MICHI -> "nube_michi"
            PetType.JELLY -> "jelly"
            PetType.CORGI -> "corgi"
            PetType.GINGER -> "ginger"
            PetType.ANGEL -> "angel"
            PetType.PATITO -> "patito"
            PetType.DIABLILLO -> "diablillo"
            PetType.MOKI -> "moki"
            PetType.YUKI -> "yuki"
            PetType.PIRU -> "piru"
            PetType.TARO -> "taro"
            PetType.MENTA -> "menta"
            PetType.TELA -> "tela"
            PetType.LUMI -> "lumi"
        }
    }

    private fun taskIdFor(action: CareAction): String {
        return when (action) {
            CareAction.FEED -> "feed"
            CareAction.CLEAN -> "clean"
            CareAction.PLAY -> "play"
            CareAction.REST -> "rest"
            CareAction.CHECK_IN -> "check_in"
            CareAction.MEDICINE -> "medicine"
        }
    }

    private fun reconcileStatus(entity: PetStatusEntity): PetStatusEntity {
        val isActive = isCareActive(entity.petId)
        val state = if (isActive) freezeInactiveElapsedTime(entity.toCareState()) else entity.toCareState()
        val reconciled = petNeedsEngine.reconcile(state, isActive)
        return reconciled.toEntity(entity).normalizeMood()
    }

    private fun freezeInactiveElapsedTime(state: PetCareState): PetCareState {
        val selectedAt = selectedPetStore.getSelectedAt() ?: return state
        if (selectedAt <= state.lastUpdatedAt) return state
        val inactiveDuration = selectedAt - state.lastUpdatedAt
        return state.copy(
            conditionStartedAt = shiftTimestamp(state.conditionStartedAt, inactiveDuration),
            criticalNeedsStartedAt = shiftTimestamp(state.criticalNeedsStartedAt, inactiveDuration),
            lastUpdatedAt = selectedAt,
            lastInteractionAt = shiftTimestamp(state.lastInteractionAt, inactiveDuration),
            lastCareAt = shiftTimestamp(state.lastCareAt, inactiveDuration),
        )
    }

    private fun shiftTimestamp(timestamp: Long, duration: Long): Long {
        return if (timestamp > 0L) timestamp + duration else 0L
    }

    private fun hasRecovered(before: PetStatusEntity, after: PetStatusEntity): Boolean {
        val previous = runCatching { PetCondition.valueOf(before.condition) }.getOrDefault(PetCondition.HEALTHY)
        val current = runCatching { PetCondition.valueOf(after.condition) }.getOrDefault(PetCondition.HEALTHY)
        return previous in setOf(PetCondition.SICK, PetCondition.RECOVERING) && current == PetCondition.HEALTHY
    }

    private fun isCareActive(petId: String): Boolean {
        return selectedPetStore.isPetEnabled() && petIdOf(selectedPetStore.load()) == petId
    }

    private fun PetStatusEntity.toCareState(): PetCareState = PetCareState(
        energy = energy,
        satiety = satiety,
        hygiene = hygiene,
        condition = runCatching { PetCondition.valueOf(condition) }.getOrDefault(PetCondition.HEALTHY),
        conditionStartedAt = conditionStartedAt,
        criticalNeedsStartedAt = criticalNeedsStartedAt,
        recoveryProgress = recoveryProgress.coerceIn(0, 100),
        lastUpdatedAt = lastUpdatedAt,
        lastInteractionAt = lastInteractionAt,
        lastCareAt = lastCareAt,
        lastMedicineAt = lastMedicineAt,
    )

    private fun PetCareState.toEntity(source: PetStatusEntity): PetStatusEntity = source.copy(
        energy = energy,
        satiety = satiety,
        hygiene = hygiene,
        condition = condition.name,
        conditionStartedAt = conditionStartedAt,
        criticalNeedsStartedAt = criticalNeedsStartedAt,
        recoveryProgress = recoveryProgress,
        lastUpdatedAt = lastUpdatedAt,
        lastInteractionAt = lastInteractionAt,
        lastCareAt = lastCareAt,
        lastMedicineAt = lastMedicineAt,
    )

    private fun memoryCountForBond(bondPoints: Int): Int {
        return when {
            bondPoints >= 70 -> 3
            bondPoints >= 35 -> 2
            bondPoints >= 12 -> 1
            else -> 0
        }
    }

    private fun todayKey(): String = java.time.Instant.ofEpochMilli(timeProvider.getCurrentTimeMillis())
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toString()

    private fun currentLocale(): Locale = appContext.resources.configuration.locales[0]

    private fun daysBetween(fromDay: String, toDay: String): Long {
        return runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(fromDay), LocalDate.parse(toDay))
        }.getOrDefault(Long.MAX_VALUE)
    }

    private fun epochMillisToDateTime(epochMillis: Long) = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()

    suspend fun getTreasureCollection(petType: PetType): TreasureCollection {
        ensureWalletMigrated()
        return db.withTransaction {
            val petId: String = petIdOf(petType)
            val treasures: List<TreasureItem> = db.treasureDao().getAllTreasuresSnapshot()
            val treasureByEmoji: Map<String, TreasureItem> = treasures.associateBy { item -> item.emoji }
            val discoveredCount: Int = TreasureCatalog.all.count { definition ->
                (treasureByEmoji[definition.emoji]?.totalFound ?: 0) > 0
            }
            reconcileTreasureMilestones(
                discoveredCount = discoveredCount,
                completionPetId = null,
                completionTime = treasures.maxOfOrNull { item -> item.lastFoundAt } ?: 0L,
            )
            val bond: PetBondEntity = ensureBondEntity(petId)
            val isPetActive: Boolean = isCareActive(petId)
            val hasGiftedToday: Boolean = bond.lastTreasureGiftDay == todayKey()
            val favorites: Set<String> = TreasureCatalog.getFavorites(getPersonality(petType))
            val canGiftToday: Boolean = isPetActive && !hasGiftedToday
            val items: List<TreasureCollectionItem> = TreasureCatalog.all.map { definition ->
                val stored: TreasureItem? = treasureByEmoji[definition.emoji]
                val inventoryCount: Int = stored?.count?.coerceAtLeast(0) ?: 0
                val totalFound: Int = stored?.totalFound?.coerceAtLeast(0) ?: 0
                TreasureCollectionItem(
                    id = definition.id,
                    emoji = definition.emoji,
                    name = appContext.getString(definition.nameResourceId),
                    story = appContext.getString(definition.storyResourceId),
                    hint = appContext.getString(definition.hintResourceId),
                    inventoryCount = inventoryCount,
                    totalFound = totalFound,
                    lastFoundAt = stored?.lastFoundAt ?: 0L,
                    isFavorite = definition.emoji in favorites,
                    canGift = canGiftToday && totalFound > 0 && inventoryCount > 0,
                )
            }
            val nextBadge: TreasureBadge? = TreasureBadge.getNext(discoveredCount)
            TreasureCollection(
                summary = TreasureCollectionSummary(
                    discoveredCount = discoveredCount,
                    totalCount = TreasureCatalog.all.size,
                    badge = TreasureBadge.getForProgress(discoveredCount),
                    nextMilestone = nextBadge?.milestone,
                    nextRewardCoins = nextBadge?.rewardCoins,
                    isPetActive = isPetActive,
                    hasGiftedToday = hasGiftedToday,
                    currentBond = bond.bondPoints.coerceIn(0, 100),
                ),
                items = items,
            )
        }
    }

    suspend fun giftTreasure(
        petType: PetType,
        treasureId: String,
        acceptsNoBondReward: Boolean = false,
    ): TreasureGiftResult = db.withTransaction {
        val petId: String = petIdOf(petType)
        if (!isCareActive(petId)) return@withTransaction TreasureGiftResult.PetNotActive
        val definition = TreasureCatalog.getById(treasureId)
            ?: TreasureCatalog.getByEmoji(treasureId)
            ?: return@withTransaction TreasureGiftResult.TreasureUnavailable
        val bond: PetBondEntity = ensureBondEntity(petId)
        if (bond.lastTreasureGiftDay == todayKey()) {
            return@withTransaction TreasureGiftResult.AlreadyGiftedToday
        }
        val treasure: TreasureItem = db.treasureDao().getTreasure(definition.emoji)
            ?: return@withTransaction TreasureGiftResult.TreasureUnavailable
        if (treasure.totalFound <= 0 || treasure.count <= 0) {
            return@withTransaction TreasureGiftResult.TreasureUnavailable
        }
        if (bond.bondPoints >= MAX_BOND && !acceptsNoBondReward) {
            return@withTransaction TreasureGiftResult.MaximumBondConfirmationRequired
        }
        val isFavorite: Boolean = definition.emoji in TreasureCatalog.getFavorites(getPersonality(petType))
        val requestedBondGain: Int = if (isFavorite) FAVORITE_GIFT_BOND_REWARD else GIFT_BOND_REWARD
        val nextBond: Int = (bond.bondPoints + requestedBondGain).coerceAtMost(MAX_BOND)
        val bondGained: Int = nextBond - bond.bondPoints.coerceAtMost(MAX_BOND)
        val remainingCount: Int = (treasure.count - 1).coerceAtLeast(0)
        db.treasureDao().updateTreasure(treasure.copy(count = remainingCount))
        db.petBondDao().upsert(
            bond.copy(
                bondPoints = nextBond,
                memoriesUnlocked = max(bond.memoriesUnlocked, memoryCountForBond(nextBond)),
                lastTreasureGiftDay = todayKey(),
                treasuresGifted = bond.treasuresGifted + 1,
                favoriteTreasuresGifted = bond.favoriteTreasuresGifted + if (isFavorite) 1 else 0,
            )
        )
        TreasureGiftResult.Success(
            treasureId = definition.id,
            emoji = definition.emoji,
            isFavorite = isFavorite,
            bondGained = bondGained,
            remainingCount = remainingCount,
        )
    }

    suspend fun maybeAwardTreasureFromInteraction(petType: PetType): TreasureDiscoveryResult? {
        ensureWalletMigrated()
        return db.withTransaction {
            val petId: String = petIdOf(petType)
            val bond: PetBondEntity = ensureBondEntity(petId)
            val interactionCount: Int = bond.bondPoints / INTERACTION_BOND_STEP
            val treasureCount: Int = getLifetimeTreasureCount()
            val milestone: Int = when {
                treasureCount == 0 && interactionCount >= FIRST_INTERACTION_DISCOVERY -> 1
                else -> interactionCount / INTERACTION_DISCOVERY_INTERVAL
            }
            if (milestone <= bond.lastTreasureInteractionMilestone || milestone <= 0) {
                return@withTransaction null
            }
            db.petBondDao().upsert(bond.copy(lastTreasureInteractionMilestone = milestone))
            addTreasureInternal(petId, pickTreasureEmoji())
        }
    }

    suspend fun maybeAwardTreasureFromActiveMinute(petType: PetType): TreasureDiscoveryResult? {
        ensureWalletMigrated()
        return db.withTransaction {
            val petId: String = petIdOf(petType)
            val bond: PetBondEntity = ensureBondEntity(petId)
            val treasureCount: Int = getLifetimeTreasureCount()
            val milestone: Int = when {
                treasureCount == 0 && bond.activeMinutes >= FIRST_ACTIVE_MINUTE_DISCOVERY -> 1
                else -> bond.activeMinutes / ACTIVE_MINUTE_DISCOVERY_INTERVAL
            }
            if (milestone <= bond.lastTreasureActiveMilestone || milestone <= 0) {
                return@withTransaction null
            }
            db.petBondDao().upsert(bond.copy(lastTreasureActiveMilestone = milestone))
            addTreasureInternal(petId, pickTreasureEmoji())
        }
    }

    private suspend fun getLifetimeTreasureCount(): Int {
        val catalogEmoji: Set<String> = TreasureCatalog.all.map { definition -> definition.emoji }.toSet()
        return db.treasureDao().getAllTreasuresSnapshot()
            .filter { item -> item.emoji in catalogEmoji }
            .sumOf { item -> item.totalFound }
    }

    private suspend fun pickTreasureEmoji(): String {
        val allTreasures: List<String> = TreasureCatalog.all.map { definition -> definition.emoji }
        val found: Map<String, Int> = db.treasureDao().getAllTreasuresSnapshot()
            .associate { item -> item.emoji to item.totalFound }
        val unseen: List<String> = allTreasures.filter { emoji -> (found[emoji] ?: 0) == 0 }
        if (unseen.isNotEmpty()) return unseen.random()
        val minimumFound: Int = allTreasures.minOf { emoji -> found[emoji] ?: 0 }
        return allTreasures.filter { emoji -> (found[emoji] ?: 0) == minimumFound }.random()
    }

    private suspend fun addTreasureInternal(petId: String, emoji: String): TreasureDiscoveryResult {
        val now: Long = timeProvider.getCurrentTimeMillis()
        val existing: TreasureItem? = db.treasureDao().getTreasure(emoji)
        val isNewDiscovery: Boolean = existing == null || existing.totalFound <= 0
        val updatedTreasure: TreasureItem = existing?.copy(
            count = existing.count + 1,
            lastFoundAt = now,
            totalFound = existing.totalFound + 1,
        ) ?: TreasureItem(
            emoji = emoji,
            count = 1,
            firstFoundAt = now,
            lastFoundAt = now,
            totalFound = 1,
        )
        if (existing == null) {
            db.treasureDao().insertTreasure(updatedTreasure)
        } else {
            db.treasureDao().updateTreasure(updatedTreasure)
        }
        val bond: PetBondEntity = ensureBondEntity(petId)
        val nextBond: Int = (bond.bondPoints + if (isNewDiscovery) NEW_DISCOVERY_BOND_REWARD else 0)
            .coerceAtMost(MAX_BOND)
        db.petBondDao().upsert(
            bond.copy(
                bondPoints = nextBond,
                memoriesUnlocked = max(bond.memoriesUnlocked, memoryCountForBond(nextBond)),
            )
        )
        val wallet: PetBondEntity = ensureBondEntity(walletId)
        db.petBondDao().upsert(wallet.copy(softCurrency = wallet.softCurrency + DISCOVERY_COIN_REWARD))
        val discoveredCount: Int = TreasureCatalog.all.count { definition ->
            val foundItem: TreasureItem? = db.treasureDao().getTreasure(definition.emoji)
            (foundItem?.totalFound ?: 0) > 0
        }
        val milestoneReward: MilestoneReward = reconcileTreasureMilestones(
            discoveredCount = discoveredCount,
            completionPetId = petId,
            completionTime = now,
        )
        return TreasureDiscoveryResult(
            treasureId = requireNotNull(TreasureCatalog.getByEmoji(emoji)).id,
            emoji = emoji,
            isNewDiscovery = isNewDiscovery,
            coinsGained = DISCOVERY_COIN_REWARD + milestoneReward.coins,
            bondGained = nextBond - bond.bondPoints.coerceAtMost(MAX_BOND),
            milestone = milestoneReward.highestBadge,
        )
    }

    private suspend fun reconcileTreasureMilestones(
        discoveredCount: Int,
        completionPetId: String?,
        completionTime: Long,
    ): MilestoneReward {
        val stateDao = db.treasureCollectionStateDao()
        val state: TreasureCollectionStateEntity = stateDao.getState()
            ?: TreasureCollectionStateEntity()
        val rewards: List<TreasureBadge> = TreasureBadge.getRewardsAfter(
            state.lastRewardedMilestone,
            discoveredCount,
        )
        val rewardCoins: Int = rewards.sumOf { badge -> badge.rewardCoins }
        if (rewardCoins > 0) {
            val wallet: PetBondEntity = ensureBondEntity(walletId)
            db.petBondDao().upsert(wallet.copy(softCurrency = wallet.softCurrency + rewardCoins))
        }
        val hasCompleted: Boolean = discoveredCount >= TreasureCatalog.all.size
        val isNewCompletion: Boolean = hasCompleted && state.completedAt == 0L
        val nextState: TreasureCollectionStateEntity = state.copy(
            lastRewardedMilestone = rewards.lastOrNull()?.milestone ?: state.lastRewardedMilestone,
            completedAt = if (isNewCompletion) completionTime else state.completedAt,
            finalCollectorPetId = if (isNewCompletion) completionPetId.orEmpty() else state.finalCollectorPetId,
        )
        if (nextState != state || stateDao.getState() == null) stateDao.upsert(nextState)
        return MilestoneReward(rewards.lastOrNull(), rewardCoins)
    }

    private companion object {
        const val INTERACTION_REWARD_COOLDOWN_MS: Long = 60_000L
        const val CARE_BOND_REWARD: Int = 8
        const val MAX_BOND: Int = 100
        const val NEW_DISCOVERY_BOND_REWARD: Int = 1
        const val GIFT_BOND_REWARD: Int = 2
        const val FAVORITE_GIFT_BOND_REWARD: Int = 5
        const val DISCOVERY_COIN_REWARD: Int = 10
        const val INTERACTION_BOND_STEP: Int = 3
        const val FIRST_INTERACTION_DISCOVERY: Int = 3
        const val INTERACTION_DISCOVERY_INTERVAL: Int = 12
        const val FIRST_ACTIVE_MINUTE_DISCOVERY: Int = 1
        const val ACTIVE_MINUTE_DISCOVERY_INTERVAL: Int = 4
    }

    private data class MilestoneReward(
        val highestBadge: TreasureBadge?,
        val coins: Int,
    )
}
