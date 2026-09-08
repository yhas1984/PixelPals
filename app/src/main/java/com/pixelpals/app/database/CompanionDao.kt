package com.pixelpals.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanionDao {
    @Query("SELECT * FROM companion_home WHERE petId = :petId")
    suspend fun getHome(petId: String): CompanionHomeEntity?
    @Query("SELECT * FROM companion_home WHERE petId = :petId")
    fun observeHome(petId: String): Flow<CompanionHomeEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHome(home: CompanionHomeEntity)
    @Query("SELECT * FROM home_decoration WHERE petId = :petId")
    fun observePlacements(petId: String): Flow<List<HomeDecorationEntity>>
    @Query("SELECT * FROM home_decoration WHERE petId = :petId")
    suspend fun getPlacements(petId: String): List<HomeDecorationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun place(decoration: HomeDecorationEntity)
    @Query("DELETE FROM home_decoration WHERE petId = :petId AND decorationId = :decorationId")
    suspend fun store(petId: String, decorationId: String)
    @Query("SELECT * FROM decoration_inventory")
    fun observeInventory(): Flow<List<DecorationInventoryEntity>>
    @Query("SELECT * FROM decoration_inventory WHERE decorationId = :id")
    suspend fun getOwned(id: String): DecorationInventoryEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun own(item: DecorationInventoryEntity): Long
    @Query("""SELECT * FROM companion_journal
        WHERE petId = :petId AND (kind != 'object' OR EXISTS (
            SELECT 1 FROM (
                SELECT detail, MAX(occurredAt) AS latest FROM companion_journal
                WHERE petId = :petId AND kind = 'object'
                GROUP BY detail, date(occurredAt / 1000, 'unixepoch', 'localtime')
            ) AS daily WHERE daily.detail = companion_journal.detail AND daily.latest = companion_journal.occurredAt
        )) ORDER BY occurredAt DESC LIMIT 100""")
    fun observeJournal(petId: String): Flow<List<CompanionJournalEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun remember(event: CompanionJournalEntity): Long
    @Query("SELECT detail FROM companion_journal WHERE petId = :petId AND kind = 'object' GROUP BY detail ORDER BY COUNT(*) DESC, MAX(occurredAt) DESC LIMIT 1")
    suspend fun learnedFavorite(petId: String): String?
    @Query("SELECT * FROM companion_expedition WHERE slot = 1")
    suspend fun getExpedition(): CompanionExpeditionEntity?
    @Query("SELECT * FROM companion_expedition WHERE slot = 1")
    fun observeExpedition(): Flow<CompanionExpeditionEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveExpedition(expedition: CompanionExpeditionEntity)
    @Query("DELETE FROM companion_expedition WHERE requestId = :requestId")
    suspend fun clearExpedition(requestId: String)
}
