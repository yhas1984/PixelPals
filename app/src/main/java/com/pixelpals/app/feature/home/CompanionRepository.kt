package com.pixelpals.app.feature.home

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.CoinSpendResult
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.*
import com.pixelpals.app.feature.treasure.TreasureCatalog
import java.util.UUID

class CompanionRepository(
    context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context),
    private val economy: PixelPalsRepository = PixelPalsRepository(context, db),
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val uptime: () -> Long = SystemClock::elapsedRealtime,
    private val boot: () -> Int = { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) },
) {
    val dao: CompanionDao = db.companionDao()
    private val selection = com.pixelpals.app.data.prefs.SelectedPetStore(context)

    suspend fun ensureHome(pet: PetType): CompanionHomeEntity = db.withTransaction {
        val id: String = pet.name.lowercase()
        dao.getHome(id)?.let { return@withTransaction it }
        val home: CompanionHomeEntity = CompanionHomeEntity(id)
        dao.saveHome(home)
        DecorationCatalog.starters.forEachIndexed { index, item ->
            dao.own(DecorationInventoryEntity(item.id, wallTime()))
            dao.place(HomeDecorationEntity(id, item.id, index, if (item.kind == DecorationKind.DISPLAY) 0 else 2))
        }
        home
    }

    suspend fun adopt(pet: PetType, name: String): Unit = db.withTransaction {
        val home: CompanionHomeEntity = ensureHome(pet)
        val nickname: String = name.trim().take(24)
        require(nickname.isNotEmpty())
        val now: Long = wallTime()
        dao.saveHome(home.copy(nickname = nickname, adoptedAt = home.adoptedAt.takeIf { it > 0 } ?: now))
        dao.remember(CompanionJournalEntity("adopt:${home.petId}", home.petId, "adopt", "", now))
    }

    suspend fun setEnvironment(pet: PetType, environment: HomeEnvironment): Unit = db.withTransaction {
        dao.saveHome(ensureHome(pet).copy(environment = environment.name))
    }

    suspend fun place(pet: PetType, id: String, column: Int, row: Int): Boolean = db.withTransaction {
        if (!HomeGrid.isValid(column, row) || dao.getOwned(id) == null) return@withTransaction false
        val petId: String = pet.name.lowercase()
        if (dao.getPlacements(petId).any { it.decorationId != id && it.column == column && it.row == row }) return@withTransaction false
        dao.place(HomeDecorationEntity(petId, id, column, row))
        true
    }

    suspend fun store(pet: PetType, id: String): Unit = dao.store(pet.name.lowercase(), id)

    suspend fun removeDesktopObject(pet: PetType): Unit = db.withTransaction {
        dao.saveHome(ensureHome(pet).copy(desktopObject = ""))
    }

    suspend fun chooseObject(pet: PetType, id: String, forDesktop: Boolean): Boolean = db.withTransaction {
        val item: Decoration = DecorationCatalog.find(id) ?: return@withTransaction false
        if (dao.getOwned(id) == null || item.action == null) return@withTransaction false
        val home: CompanionHomeEntity = ensureHome(pet)
        dao.saveHome(if (forDesktop) home.copy(desktopObject = id) else home.copy(favoriteObject = id))
        true
    }

    suspend fun recordObjectUse(pet: PetType, id: String): Unit = db.withTransaction {
        if (dao.getOwned(id) == null || DecorationCatalog.find(id)?.action == null) return@withTransaction
        val home = ensureHome(pet)
        val now = wallTime()
        dao.remember(CompanionJournalEntity("object:${home.petId}:${now / 30_000}", home.petId, "object", id, now))
        dao.learnedFavorite(home.petId)?.let { dao.saveHome(home.copy(favoriteObject = it)) }
    }

    suspend fun purchase(id: String): CoinSpendResult = economy.purchaseDecorationWithCoins(id)

    suspend fun startExpedition(pet: PetType, destination: ExpeditionDestination): Boolean = db.withTransaction {
        if (dao.getExpedition() != null) return@withTransaction false
        if (economy.getCatalog(PetType.CORGI).none { it.petType == pet && it.state != com.pixelpals.app.data.catalog.CatalogItemState.LOCKED }) return@withTransaction false
        economy.getStatusSnapshot(pet)
        val now: Long = wallTime()
        dao.saveExpedition(CompanionExpeditionEntity(requestId = UUID.randomUUID().toString(),
            petId = pet.name.lowercase(), destination = destination.id, startedAt = now,
            lastWallTime = now, lastUptime = uptime(), bootCount = boot()))
        val saved: CompanionExpeditionEntity = requireNotNull(dao.getExpedition())
        dao.saveExpedition(saved.copy(resumeDesktop = selection.isPetEnabled() && selection.load() == pet))
        true
    }

    suspend fun refreshExpedition(): Unit = db.withTransaction {
        val expedition: CompanionExpeditionEntity = dao.getExpedition() ?: return@withTransaction
        val destination: ExpeditionDestination = ExpeditionDestination.find(expedition.destination) ?: return@withTransaction
        val now: Long = wallTime()
        val ticks: Long = uptime()
        val bootCount: Int = boot()
        val elapsed: Long = ExpeditionClock.advance(expedition.elapsedMs, destination.durationMs - expedition.elapsedMs,
            now - expedition.lastWallTime, ticks - expedition.lastUptime, bootCount == expedition.bootCount)
        if (elapsed == expedition.elapsedMs && expedition.bootCount == bootCount) return@withTransaction
        dao.saveExpedition(expedition.copy(elapsedMs = elapsed, lastWallTime = now, lastUptime = ticks, bootCount = bootCount))
    }

    suspend fun finishExpedition(requestId: String, cancel: Boolean = false): Boolean = db.withTransaction {
        refreshExpedition()
        val expedition: CompanionExpeditionEntity = dao.getExpedition() ?: return@withTransaction false
        if (expedition.requestId != requestId) return@withTransaction false
        val destination: ExpeditionDestination = ExpeditionDestination.find(expedition.destination) ?: return@withTransaction false
        if (!cancel && expedition.elapsedMs < destination.durationMs) return@withTransaction false
        val now: Long = wallTime()
        // Shift illness timers as well as needs, so travel never counts as neglect.
        db.petStatusDao().getByPetId(expedition.petId)?.let { status ->
            val paused: Long = (now - status.lastUpdatedAt).coerceAtLeast(0)
            dao.clearExpedition(requestId)
            db.petStatusDao().upsert(status.copy(lastUpdatedAt = now,
                lastInteractionAt = if (status.lastInteractionAt > 0) status.lastInteractionAt + paused else 0,
                lastCareAt = if (status.lastCareAt > 0) status.lastCareAt + paused else 0,
                lastMedicineAt = if (status.lastMedicineAt > 0) status.lastMedicineAt + paused else 0,
                conditionStartedAt = if (status.conditionStartedAt > 0) status.conditionStartedAt + paused else 0,
                criticalNeedsStartedAt = if (status.criticalNeedsStartedAt > 0) status.criticalNeedsStartedAt + paused else 0))
        }
        if (!cancel) {
            economy.grantExpeditionTreasure(expedition.petId, TreasureCatalog.all[destination.treasureIndex].emoji)
            DecorationCatalog.all.filter { it.expedition == destination.id }.forEach { dao.own(DecorationInventoryEntity(it.id, now)) }
            dao.remember(CompanionJournalEntity("expedition:$requestId", expedition.petId, "expedition", destination.id, now))
        }
        dao.clearExpedition(requestId)
        true
    }
}
