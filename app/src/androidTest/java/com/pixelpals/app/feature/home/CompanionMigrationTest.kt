package com.pixelpals.app.feature.home

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.database.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun everyExportedLegacySchemaPreservesRecordsAcrossMigrationAndReopen(): Unit {
        for (version: Int in 2..10) {
            val name: String = "companion-matrix-$version-test"
            context.deleteDatabase(name)
            try {
                val schema: JSONObject = readSchema(version)
                val helper: SupportSQLiteOpenHelper = createFixture(name, version)
                val expected: Map<String, List<List<String?>>>
                try {
                    val database: SupportSQLiteDatabase = helper.writableDatabase
                    val tables: Set<String> = seedLegacyRecords(database, schema, version)
                    expected = tables.associateWith { snapshot(database, schema, it) }
                } finally { helper.close() }
                repeat(2) { opening: Int ->
                    val room: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(
                        AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
                        AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
                        AppDatabase.MIGRATION_7_8, COMPANION_MIGRATION_8_9, COMPANION_MIGRATION_9_10,
                        COMPANION_MIGRATION_10_11,
                    ).build()
                    try {
                        val database: SupportSQLiteDatabase = room.openHelper.writableDatabase
                        assertEquals(11, database.version)
                        expected.forEach { (table: String, rows: List<List<String?>>) ->
                            assertEquals("v$version opening $opening table $table", rows, snapshot(database, schema, table))
                        }
                        if (version < 8) database.query("SELECT count, totalFound FROM treasures").use { cursor ->
                            while (cursor.moveToNext()) assertEquals(cursor.getInt(0), cursor.getInt(1))
                        }
                    } finally { room.close() }
                }
            } finally { context.deleteDatabase(name) }
        }
    }

    private fun seedLegacyRecords(db: SupportSQLiteDatabase, schema: JSONObject, version: Int): Set<String> {
        val tables: MutableSet<String> = mutableSetOf()
        fun seed(table: String, values: Map<String, Any>) { insertFixture(db, schema, table, values); tables += table }
        seed("pet_bond", mapOf("petId" to "wallet", "softCurrency" to 435))
        for (pet: com.pixelpals.app.core.domain.PetType in com.pixelpals.app.core.domain.PetType.entries) {
            val id: String = pet.name.lowercase(java.util.Locale.ROOT)
            seed("pet_bond", mapOf("petId" to id, "bondPoints" to 123, "careStreakDays" to 7,
                "memoriesUnlocked" to 3, "activeMinutes" to 81, "illnessRecoveries" to 2))
            seed("pet_status", mapOf("petId" to id, "health" to 78, "energy" to 61, "hunger" to 52,
                "hygiene" to 43, "mood" to 84, "lastUpdatedAt" to 1_000_000L, "condition" to "RECOVERING", "recoveryProgress" to 35))
        }
        seed("owned_product", mapOf("productId" to "pet_taro_premium", "productType" to "pet", "source" to "play", "acknowledged" to 1))
        seed("owned_product", mapOf("productId" to "remove_ads", "productType" to "entitlement", "source" to "play", "acknowledged" to 1))
        seed("treasures", mapOf("emoji" to "🌸", "count" to 3, "totalFound" to 8, "firstFoundAt" to 100L, "lastFoundAt" to 900L))
        seed("treasures", mapOf("emoji" to "🌟", "count" to 0, "totalFound" to 5, "firstFoundAt" to 200L, "lastFoundAt" to 800L))
        if (version >= 6) {
            seed("processed_purchase", mapOf("purchaseToken" to "test-granted", "productId" to "pet_taro_premium", "quantity" to 1,
                "purchaseTime" to 500L, "source" to "play", "grantedAt" to 600L, "acknowledgedAt" to 700L, "lastSeenAt" to 800L))
            seed("processed_purchase", mapOf("purchaseToken" to "test-pending", "productId" to "test_coins", "quantity" to 1,
                "purchaseTime" to 900L, "source" to "play", "lastSeenAt" to 950L))
        }
        if (version >= 8) seed("treasure_collection_state", mapOf("id" to 1, "lastRewardedMilestone" to 3, "completedAt" to 700L, "finalCollectorPetId" to "corgi"))
        if (version >= 9) {
            seed("companion_home", mapOf("petId" to "corgi", "nickname" to "Nieve 🐾", "environment" to "GARDEN", "playCount" to 14, "favoriteObject" to "ball"))
            seed("home_decoration", mapOf("petId" to "corgi", "decorationId" to "ball", "column" to 3, "row" to 1))
            seed("decoration_inventory", mapOf("decorationId" to "ball", "acquiredAt" to 600L))
            seed("companion_journal", mapOf("id" to "test-memory", "petId" to "corgi", "kind" to "adoption", "detail" to "Nieve 🐾", "occurredAt" to 700L))
            seed("companion_expedition", mapOf("slot" to 1, "requestId" to "test-journey", "petId" to "corgi", "destination" to "meadow", "elapsedMs" to 15_000L))
        }
        return tables
    }

    private fun snapshot(db: SupportSQLiteDatabase, schema: JSONObject, table: String): List<List<String?>> {
        val entities = schema.getJSONArray("entities")
        val fields = (0 until entities.length()).map { entities.getJSONObject(it) }.first { it.getString("tableName") == table }.getJSONArray("fields")
        val columns: List<String> = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
        return db.query("SELECT ${columns.joinToString { "`$it`" }} FROM `$table`").use { cursor ->
            val rows: MutableList<List<String?>> = mutableListOf()
            while (cursor.moveToNext()) rows += columns.indices.map { if (cursor.isNull(it)) null else cursor.getString(it) }
            rows.sortedBy { it.toString() }
        }
    }

    @Test fun versionEightPreservesWalletPurchasesTreasuresAndStatus(): Unit = runBlocking {
        val name: String = "companion-migration-eight-test"
        context.deleteDatabase(name)
        val helper: SupportSQLiteOpenHelper = createFixture(name, 8)
        val database: SupportSQLiteDatabase = helper.writableDatabase
        val schema: JSONObject = readSchema(8)
        insertFixture(database, schema, "pet_bond", mapOf("petId" to "wallet", "softCurrency" to 435))
        insertFixture(database, schema, "pet_status", mapOf("petId" to "corgi", "health" to 78))
        insertFixture(database, schema, "owned_product", mapOf("productId" to "pet_taro_premium", "source" to "play", "productType" to "pet"))
        insertFixture(database, schema, "treasures", mapOf("emoji" to "🌸", "count" to 3, "totalFound" to 5))
        helper.close()
        val room: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(COMPANION_MIGRATION_8_9, COMPANION_MIGRATION_9_10, COMPANION_MIGRATION_10_11).build()
        try {
            assertEquals(435, room.petBondDao().getByPetId("wallet")?.softCurrency)
            assertEquals(78, room.petStatusDao().getByPetId("corgi")?.health)
            assertEquals("play", room.ownedProductDao().getByProductId("pet_taro_premium")?.source)
            assertEquals(3, room.treasureDao().getTreasure("🌸")?.count)
            assertEquals(5, room.treasureDao().getTreasure("🌸")?.totalFound)
            assertNull(room.companionDao().getExpedition())
            assertNull(room.companionDao().getHome("corgi"))
        } finally { room.close(); context.deleteDatabase(name) }
    }
    @Test fun firstPreviewUpgradePreservesHomeAndOngoingJourney(): Unit = runBlocking {
        val name: String = "companion-migration-preview-test"
        context.deleteDatabase(name)
        val helper: SupportSQLiteOpenHelper = createFixture(name, 9)
        val database: SupportSQLiteDatabase = helper.writableDatabase
        val schema: JSONObject = readSchema(9)
        insertFixture(database, schema, "companion_home", mapOf("petId" to "corgi", "nickname" to "Maple", "environment" to "GARDEN"))
        insertFixture(database, schema, "companion_expedition", mapOf("slot" to 1, "requestId" to "old-trip", "petId" to "corgi", "destination" to "meadow", "elapsedMs" to 10))
        helper.close()
        val room: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(COMPANION_MIGRATION_9_10, COMPANION_MIGRATION_10_11).build()
        try {
            assertEquals("Maple", room.companionDao().getHome("corgi")?.nickname)
            assertEquals("old-trip", room.companionDao().getExpedition()?.requestId)
            assertEquals(false, room.companionDao().getExpedition()?.resumeDesktop)
        } finally { room.close(); context.deleteDatabase(name) }
    }
    private fun readSchema(version: Int): JSONObject = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
        .open("com.pixelpals.app.database.AppDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
    private fun createFixture(name: String, version: Int): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase): Unit {
                val entities = readSchema(version).getJSONArray("entities")
                for (index: Int in 0 until entities.length()) {
                    val entity: JSONObject = entities.getJSONObject(index)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = Unit
        }).build())
    private fun insertFixture(db: SupportSQLiteDatabase, schema: JSONObject, table: String, values: Map<String, Any>): Unit {
        val entities = schema.getJSONArray("entities")
        val entity: JSONObject = (0 until entities.length()).map { entities.getJSONObject(it) }.first { it.getString("tableName") == table }
        val fields = entity.getJSONArray("fields")
        val names: List<String> = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
        val arguments: Array<Any?> = (0 until fields.length()).map { index ->
            val field: JSONObject = fields.getJSONObject(index)
            values[field.getString("columnName")] ?: if (!field.optBoolean("notNull", false)) null else if (field.getString("affinity") == "TEXT") "" else 0
        }.toTypedArray()
        db.execSQL("INSERT INTO `$table` (${names.joinToString { "`$it`" }}) VALUES (${names.joinToString { "?" }})", arguments)
    }
}
