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
            .addMigrations(COMPANION_MIGRATION_8_9, COMPANION_MIGRATION_9_10).build()
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
        val room: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(COMPANION_MIGRATION_9_10).build()
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
