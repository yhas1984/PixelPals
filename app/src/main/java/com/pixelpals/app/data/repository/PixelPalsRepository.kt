package com.pixelpals.app.data.repository

import android.content.Context
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.DailyTaskStateEntity
import com.pixelpals.app.database.OwnedProductEntity
import com.pixelpals.app.database.PetBondEntity
import com.pixelpals.app.database.PetStatusEntity
import com.pixelpals.app.database.TreasureItem
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.DailyTask
import com.pixelpals.app.status.MemoryMoment
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetPersonality
import com.pixelpals.app.status.PetStatusSnapshot
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.room.withTransaction
import kotlin.math.max
import kotlin.math.min

class PixelPalsRepository(context: Context) {
    private val appContext: Context = context.applicationContext
    private val db = AppDatabase.getDatabase(appContext)
    private val cosmeticPrefs = appContext.getSharedPreferences("pixelpals_cosmetics", Context.MODE_PRIVATE)

    private val premiumPetProductIds = mapOf(
        PetType.ANGEL to "pet_angel_premium",
        PetType.DIABLILLO to "pet_diablillo_premium"
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
        db.ownedProductDao().getByProductId(productId) != null

    /** Compra un cosmético con monedas blandas. Devuelve true si se completó. */
    suspend fun purchaseCosmeticWithCoins(petId: String, cosmeticId: String): Boolean {
        val cosmetic = com.pixelpals.app.data.catalog.CosmeticCatalog.findById(appContext, cosmeticId)
            ?: return false
        val price = cosmetic.coinPrice ?: return false
        return db.withTransaction {
            val bond = ensureBondEntity(petId)
            if (bond.softCurrency < price) return@withTransaction false
            db.petBondDao().upsert(bond.copy(softCurrency = bond.softCurrency - price))
            db.ownedProductDao().upsert(
                com.pixelpals.app.database.OwnedProductEntity(
                    productId = cosmetic.productId,
                    productType = "cosmetic",
                    source = "soft_currency",
                    purchasedAt = System.currentTimeMillis(),
                )
            )
            true
        }
    }

    suspend fun getStatusSnapshot(petType: PetType): PetStatusSnapshot {
        return getStatusSnapshot(petIdOf(petType))
    }

    suspend fun getStatusSnapshot(petId: String): PetStatusSnapshot {
        val statusEntity = ensureStatusEntity(petId)
        val bondEntity = ensureBondEntity(petId)
        val decayed = applyDecay(statusEntity)
        // Solo persistir si el decay modificó algo (evita escrituras redundantes).
        if (decayed != statusEntity) {
            db.petStatusDao().upsert(decayed)
        }
        return toSnapshot(decayed, bondEntity)
    }

    suspend fun recordActiveMinute(petType: PetType): PetStatusSnapshot {
        val petId = petIdOf(petType)
        val bond = ensureBondEntity(petId)
        db.petBondDao().upsert(bond.copy(activeMinutes = bond.activeMinutes + 1))
        return applyMutation(petId) {
            copy(
                hunger = (hunger - 2).coerceAtLeast(0),
                hygiene = (hygiene - 1).coerceAtLeast(0),
                energy = (energy - 1).coerceAtLeast(0),
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun recordInteraction(petType: PetType): PetStatusSnapshot {
        val petId = petIdOf(petType)
        return db.withTransaction {
            val currentStatus = applyDecay(ensureStatusEntity(petId))
            val currentBond = ensureBondEntity(petId)
            if (
                currentBond.bondPoints > 0 &&
                System.currentTimeMillis() - currentStatus.lastInteractionAt < INTERACTION_REWARD_COOLDOWN_MS
            ) {
                return@withTransaction getStatusSnapshot(petId)
            }
            val snapshot = applyMutation(petId) {
                copy(
                    energy = (energy + 2).coerceAtMost(100),
                    hunger = (hunger - 1).coerceAtLeast(0),
                    lastInteractionAt = System.currentTimeMillis(),
                    lastUpdatedAt = System.currentTimeMillis()
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
            snapshot
        }
    }

    suspend fun applyCareAction(petType: PetType, action: CareAction): PetStatusSnapshot {
        val petId = petIdOf(petType)
        val taskId = taskIdFor(action)
        return db.withTransaction {
            val isFirstCompletionToday = db.dailyTaskStateDao()
                .getTasksForDay(petId, todayKey())
                .none { it.taskId == taskId }
            if (action == CareAction.CHECK_IN && !isFirstCompletionToday) {
                return@withTransaction getStatusSnapshot(petId)
            }
            val currentBond = ensureBondEntity(petId)
            applyMutation(petId) {
                when (action) {
                    CareAction.FEED -> copy(hunger = (hunger + 18).coerceAtMost(100), health = (health + 4).coerceAtMost(100))
                    CareAction.CLEAN -> copy(hygiene = (hygiene + 20).coerceAtMost(100), health = (health + 2).coerceAtMost(100))
                    CareAction.PLAY -> copy(energy = (energy - 6).coerceAtLeast(0), hunger = (hunger - 4).coerceAtLeast(0), health = (health + 5).coerceAtMost(100))
                    CareAction.REST -> copy(energy = (energy + 20).coerceAtMost(100), health = (health + 3).coerceAtMost(100))
                    CareAction.CHECK_IN -> copy(health = (health + 1).coerceAtMost(100))
                }.copy(
                    lastInteractionAt = System.currentTimeMillis(),
                    lastUpdatedAt = System.currentTimeMillis()
                )
            }
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
            getStatusSnapshot(petId)
        }
    }

    suspend fun getDailyTasks(petType: PetType): List<DailyTask> {
        val petId = petIdOf(petType)
        val today = todayKey()
        val completedIds = db.dailyTaskStateDao().getTasksForDay(petId, today).map { it.taskId }.toSet()
        return listOf(
            DailyTask("check_in", "Saludar", "Pásate a verlo y hazle sentir acompañado.", 8, "check_in" in completedIds),
            DailyTask("feed", "Snack favorito", "Dale algo rico para subirle el ánimo.", 14, "feed" in completedIds),
            DailyTask("play", "Momento de juego", "Hazle caso un rato y comparte energía.", 14, "play" in completedIds),
            DailyTask("clean", "Asear", "Déjalo guapo para lucirse por la pantalla.", 12, "clean" in completedIds),
            DailyTask("rest", "Siesta tierna", "Ayúdale a recuperar energía con un descanso.", 10, "rest" in completedIds)
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
            "Siempre contigo desde $date"
        } else "Primer encuentro"
        val memories = mutableListOf(
            MemoryMoment("first_day", "Primer día juntos", firstDay)
        )
        if (snapshot.bond >= 15) {
            memories += MemoryMoment("bond_15", "Confianza inicial", "Tu pet ya te reconoce y te sigue con más calma.")
        }
        if (snapshot.careStreakDays >= 3) {
            memories += MemoryMoment("streak_3", "Racha de cuidado", "Lleváis ${snapshot.careStreakDays} días seguidos compartiendo rutina.")
        }
        if (snapshot.bond >= 35) {
            memories += MemoryMoment("bond_35", "Rutina favorita", "Ya espera tus mimos, snacks y juegos del día.")
        }
        if (snapshot.memoriesUnlocked >= 3) {
            memories += MemoryMoment("bond_50", "Vínculo fuerte", "Se nota en sus reacciones, su calma y su forma de saludarte.")
        }
        if (snapshot.careStreakDays >= 7) {
            memories += MemoryMoment("streak_7", "Semana juntos", "Una semana completa cuidándoos mutuamente.")
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
                displayName = petType.displayName,
                description = petType.description,
                previewResId = petType.spriteResId,
                petType = petType,
                productId = productId,
                isPremium = productId != null,
                state = state,
                badge = when {
                    productId != null -> "Premium"
                    petType == selectedType -> "Compi actual"
                    else -> "Base"
                }
            )
        }
    }

    suspend fun grantOwnedProduct(productId: String, source: String) {
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
                restoredAt = if (source == "restore") System.currentTimeMillis() else null
            )
        )
    }

    suspend fun isProductOwned(productId: String): Boolean {
        return db.ownedProductDao().getByProductId(productId)?.let(::isEligibleEntitlement) == true
    }

    /**
     * Otorga monedas al pet activo. Si [petType] es null, se suman al "wallet global" del primer pet.
     */
    suspend fun grantCoins(petType: PetType?, amount: Int) {
        val petId = petType?.let { petIdOf(it) } ?: "wallet"
        val bond = ensureBondEntity(petId)
        db.petBondDao().upsert(bond.copy(softCurrency = bond.softCurrency + amount))
    }

    /**
     * Compra un pack de monedas (IAP real).
     */
    suspend fun grantCoinPack(coinProduct: com.pixelpals.app.data.catalog.CoinProduct, petType: PetType?, source: String) {
        db.withTransaction {
            grantOwnedProduct(coinProduct.productId, source)
            val petId = petType?.let { petIdOf(it) } ?: "wallet"
            val bond = ensureBondEntity(petId)
            db.petBondDao().upsert(bond.copy(softCurrency = bond.softCurrency + coinProduct.coinAmount))
        }
    }

    /** Lee el balance de monedas del pet. */
    suspend fun getCoinBalance(petType: PetType?): Int {
        val petId = petType?.let { petIdOf(it) } ?: "wallet"
        return ensureBondEntity(petId).softCurrency
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
                softCurrency = bond.softCurrency + reward,
                careStreakDays = streak,
                lastCheckInDay = if (taskId == "check_in") dayKey else bond.lastCheckInDay,
                lastDailyCompletionDay = lastCompletionDay
            )
        )
    }

    private suspend fun applyMutation(
        petId: String,
        mutation: PetStatusEntity.() -> PetStatusEntity
    ): PetStatusSnapshot = db.withTransaction {
        val current = applyDecay(ensureStatusEntity(petId))
        val mutated = mutation(current).normalizeMood()
        db.petStatusDao().upsert(mutated)
        toSnapshot(mutated, ensureBondEntity(petId))
    }

    private fun PetStatusEntity.normalizeMood(): PetStatusEntity {
        val resolvedMood = deriveMood(this)
        val resolvedHealth = ((energy + hunger + hygiene) / 3).coerceIn(20, 100)
        return copy(health = resolvedHealth, mood = resolvedMood.name)
    }

    private suspend fun ensureStatusEntity(petId: String): PetStatusEntity {
        return db.petStatusDao().getByPetId(petId) ?: PetStatusEntity(petId = petId).normalizeMood().also {
            db.petStatusDao().upsert(it)
        }
    }

    private suspend fun ensureBondEntity(petId: String): PetBondEntity {
        return db.petBondDao().getByPetId(petId) ?: PetBondEntity(petId = petId).also {
            db.petBondDao().upsert(it)
        }
    }

    private fun applyDecay(entity: PetStatusEntity): PetStatusEntity {
        val now = System.currentTimeMillis()
        val hours = max(0L, ChronoUnit.HOURS.between(epochMillisToDateTime(entity.lastUpdatedAt), epochMillisToDateTime(now)))
        if (hours == 0L) return entity.normalizeMood()
        val hunger = (entity.hunger - (hours * 3)).toInt().coerceAtLeast(0)
        val hygiene = (entity.hygiene - (hours * 2)).toInt().coerceAtLeast(0)
        val energy = (entity.energy - hours.toInt()).coerceAtLeast(0)
        return entity.copy(
            hunger = hunger,
            hygiene = hygiene,
            energy = energy,
            lastUpdatedAt = now
        ).normalizeMood()
    }

    private fun toSnapshot(status: PetStatusEntity, bond: PetBondEntity): PetStatusSnapshot {
        val bondLevel = bond.bondPoints.coerceIn(0, 100)
        val mood = runCatching { PetMood.valueOf(status.mood) }.getOrElse { deriveMood(status) }
        return PetStatusSnapshot(
            petId = status.petId,
            health = status.health,
            energy = status.energy,
            hunger = status.hunger,
            hygiene = status.hygiene,
            bond = bondLevel,
            mood = mood,
            careStreakDays = bond.careStreakDays,
            softCurrency = bond.softCurrency,
            dominantSuggestion = dominantSuggestionFor(status, mood),
            memoriesUnlocked = max(bond.memoriesUnlocked, memoryCountForBond(bondLevel))
        )
    }

    private fun dominantSuggestionFor(status: PetStatusEntity, mood: PetMood): CareAction {
        return when {
            status.hunger < 45 -> CareAction.FEED
            status.hygiene < 50 -> CareAction.CLEAN
            status.energy < 45 -> CareAction.REST
            mood == PetMood.BORED -> CareAction.PLAY
            else -> CareAction.CHECK_IN
        }
    }

    private fun deriveMood(entity: PetStatusEntity): PetMood {
        val health = entity.health
        val energy = entity.energy
        val hunger = entity.hunger
        val hygiene = entity.hygiene
        val hoursWithoutAttention = max(
            0L,
            ChronoUnit.HOURS.between(
                epochMillisToDateTime(entity.lastInteractionAt),
                epochMillisToDateTime(System.currentTimeMillis())
            )
        )
        return when {
            hunger < 35 -> PetMood.HUNGRY
            energy < 35 -> PetMood.SLEEPY
            hygiene < 40 -> PetMood.DIRTY
            health > 88 && energy > 70 && hunger > 65 -> PetMood.EXCITED
            hoursWithoutAttention >= 6 -> PetMood.BORED
            health > 70 -> PetMood.HAPPY
            else -> PetMood.BORED
        }
    }

    fun moodLabel(mood: PetMood): String {
        return when (mood) {
            PetMood.HAPPY -> "feliz"
            PetMood.SLEEPY -> "somnoliento"
            PetMood.HUNGRY -> "hambriento"
            PetMood.DIRTY -> "desaliñado"
            PetMood.BORED -> "aburrido"
            PetMood.EXCITED -> "emocionado"
        }
    }

    fun careActionLabel(action: CareAction): String {
        return when (action) {
            CareAction.FEED -> "darle un snack"
            CareAction.CLEAN -> "asearlo"
            CareAction.PLAY -> "jugar un rato"
            CareAction.REST -> "dejarlo descansar"
            CareAction.CHECK_IN -> "saludarlo"
        }
    }

    fun personalityLabel(personality: PetPersonality): String {
        return when (personality) {
            PetPersonality.SWEET -> "dulce"
            PetPersonality.DREAMY -> "soñador"
            PetPersonality.BOUNCY -> "rebotón"
            PetPersonality.LOYAL -> "leal"
            PetPersonality.ELEGANT -> "elegante"
            PetPersonality.ANGELIC -> "angelical"
            PetPersonality.CURIOUS -> "curioso"
            PetPersonality.CHAOTIC -> "travieso"
        }
    }

    fun dashboardCompanionLine(petType: PetType, snapshot: PetStatusSnapshot): String {
        val personality = personalityLabel(getPersonality(petType))
        return when (snapshot.mood) {
            PetMood.EXCITED -> "${petType.displayName} está brillante hoy. Se nota su lado $personality."
            PetMood.HAPPY -> "${petType.displayName} está a gusto contigo y se comporta de forma más $personality."
            PetMood.SLEEPY -> "${petType.displayName} pide calma, mimo y una pausa corta."
            PetMood.HUNGRY -> "${petType.displayName} tiene antojo y agradecería algo rico."
            PetMood.DIRTY -> "${petType.displayName} necesita un pequeño mimo para volver a lucirse."
            PetMood.BORED -> "${petType.displayName} echa de menos atención y algo divertido."
        }
    }

    fun selectionSpotlight(petType: PetType, snapshot: PetStatusSnapshot): String {
        return "${petType.displayName} está ${moodLabel(snapshot.mood)}. Su mejor siguiente paso es ${careActionLabel(snapshot.dominantSuggestion)}."
    }

    fun premiumPetPerk(petType: PetType): String {
        return when (petType) {
            PetType.ANGEL -> "Incluye aura serena, maniobras suaves y detalles celestiales."
            PetType.DIABLILLO -> "Incluye reacciones traviesas, energía alta y efectos más caóticos."
            else -> "Incluye personalidad, presencia y estilo únicos."
        }
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
        }
    }

    private fun taskIdFor(action: CareAction): String {
        return when (action) {
            CareAction.FEED -> "feed"
            CareAction.CLEAN -> "clean"
            CareAction.PLAY -> "play"
            CareAction.REST -> "rest"
            CareAction.CHECK_IN -> "check_in"
        }
    }

    private fun memoryCountForBond(bondPoints: Int): Int {
        return when {
            bondPoints >= 70 -> 3
            bondPoints >= 35 -> 2
            bondPoints >= 12 -> 1
            else -> 0
        }
    }

    private fun todayKey(): String = LocalDate.now().toString()

    private fun daysBetween(fromDay: String, toDay: String): Long {
        return runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(fromDay), LocalDate.parse(toDay))
        }.getOrDefault(Long.MAX_VALUE)
    }

    private fun epochMillisToDateTime(epochMillis: Long) = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()

    suspend fun maybeAwardTreasureFromInteraction(petType: PetType): String? = db.withTransaction {
        val petId = petIdOf(petType)
        val bond = ensureBondEntity(petId)
        val interactionCount = bond.bondPoints / 3
        val treasureCount = db.treasureDao().getAllTreasuresSnapshot().sumOf { it.count }
        val milestone = when {
            treasureCount == 0 && interactionCount >= 3 -> 1
            else -> interactionCount / 12
        }
        val lastMilestone = bond.lastTreasureInteractionMilestone
        if (milestone <= lastMilestone || milestone <= 0) return@withTransaction null
        val treasure = pickTreasureEmoji(petId)
        addTreasureInternal(petId, treasure)
        db.petBondDao().upsert(
            bond.copy(lastTreasureInteractionMilestone = milestone)
        )
        treasure
    }

    suspend fun maybeAwardTreasureFromActiveMinute(petType: PetType): String? = db.withTransaction {
        val petId = petIdOf(petType)
        val bond = ensureBondEntity(petId)
        val activeMinutes = bond.activeMinutes
        val treasureCount = db.treasureDao().getAllTreasuresSnapshot().sumOf { it.count }
        val milestone = when {
            treasureCount == 0 && activeMinutes >= 1 -> 1
            else -> activeMinutes / 4
        }
        val lastMilestone = bond.lastTreasureActiveMilestone
        if (milestone <= lastMilestone || milestone <= 0) return@withTransaction null
        val treasure = pickTreasureEmoji(petId)
        addTreasureInternal(petId, treasure)
        db.petBondDao().upsert(
            bond.copy(lastTreasureActiveMilestone = milestone)
        )
        treasure
    }

    suspend fun consumeTreasure(emoji: String): Int = db.withTransaction {
        val dao = db.treasureDao()
        val existing = dao.getTreasure(emoji) ?: return@withTransaction 0
        val newCount = existing.count - 1
        val now = System.currentTimeMillis()
        if (newCount <= 0) {
            dao.deleteTreasure(existing)
        } else {
            dao.updateTreasure(existing.copy(count = newCount, lastFoundAt = now))
        }
        newCount.coerceAtLeast(0)
    }

    private suspend fun pickTreasureEmoji(petId: String): String {
        val allTreasures = TREASURE_POOL
        val owned = db.treasureDao().getAllTreasuresSnapshot().associate { it.emoji to it.count }
        val unseen = allTreasures.filter { (owned[it] ?: 0) == 0 }
        val basePool = if (unseen.isNotEmpty()) {
            unseen
        } else {
            val minCount = allTreasures.minOf { owned[it] ?: 0 }
            allTreasures.filter { (owned[it] ?: 0) == minCount }
        }
        return basePool.random()
    }

    private suspend fun addTreasureInternal(petId: String, emoji: String) {
        val dao = db.treasureDao()
        val now = System.currentTimeMillis()
        val existing = dao.getTreasure(emoji)
        if (existing != null) {
            dao.updateTreasure(existing.copy(count = existing.count + 1, lastFoundAt = now))
        } else {
            dao.insertTreasure(TreasureItem(emoji, 1, now, now))
        }
        val bond = ensureBondEntity(petId)
        db.petBondDao().upsert(
            bond.copy(
                softCurrency = bond.softCurrency + 10,
                bondPoints = (bond.bondPoints + 2).coerceAtMost(100),
            )
        )
    }

    private companion object {
        const val INTERACTION_REWARD_COOLDOWN_MS: Long = 60_000L
        const val CARE_BOND_REWARD: Int = 8

        val TREASURE_POOL = listOf(
            "🪙", "🌸", "🦴", "⭐", "💎", "🍀", "🐚", "🎀",
            "🍄", "🔑", "🧩", "🎵", "🪶", "🍬", "🌙", "💍", "👑", "🔮", "🍕"
        )
    }
}
