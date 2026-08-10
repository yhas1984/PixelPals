package com.pixelpals.app.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migrate1To2_keepsTreasuresAndAddsNewTables() = runBlocking {
        val dbName = "migration-test.db"
        context.deleteDatabase(dbName)
        createV1Database(dbName).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()

        try {
            val treasure = db.treasureDao().getTreasure("🌟")
            assertNotNull(treasure)
            assertEquals(2, treasure?.count)

            assertEquals(1, queryCount(db.openHelper.writableDatabase, "pet_status"))
            assertEquals(1, queryCount(db.openHelper.writableDatabase, "pet_bond"))
            assertEquals(1, queryCount(db.openHelper.writableDatabase, "daily_task_state"))
            assertEquals(1, queryCount(db.openHelper.writableDatabase, "owned_product"))
            // v4->v5 eliminó el sistema de accesorios (reemplazado por cosméticos en prefs).
            assertEquals(0, queryCount(db.openHelper.writableDatabase, "equipped_accessory"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate2To4_addsTreasureAndActiveMinutesColumns() = runBlocking {
        val dbName = "migration-test-24.db"
        context.deleteDatabase(dbName)
        createV2Database(dbName).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()

        try {
            val bond = db.petBondDao().getByPetId("corgi")
            assertNotNull(bond)
            assertEquals(0, bond?.lastTreasureInteractionMilestone)
            assertEquals(0, bond?.lastTreasureActiveMilestone)
            assertEquals(0, bond?.activeMinutes)
            assertEquals(12, bond?.bondPoints)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun createV2Database(name: String): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `treasures` (
                            `emoji` TEXT NOT NULL,
                            `count` INTEGER NOT NULL,
                            `firstFoundAt` INTEGER NOT NULL,
                            `lastFoundAt` INTEGER NOT NULL,
                            PRIMARY KEY(`emoji`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `pet_status` (
                            `petId` TEXT NOT NULL,
                            `health` INTEGER NOT NULL,
                            `energy` INTEGER NOT NULL,
                            `hunger` INTEGER NOT NULL,
                            `hygiene` INTEGER NOT NULL,
                            `mood` TEXT NOT NULL,
                            `lastUpdatedAt` INTEGER NOT NULL,
                            `lastInteractionAt` INTEGER NOT NULL,
                            PRIMARY KEY(`petId`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `pet_bond` (
                            `petId` TEXT NOT NULL,
                            `bondPoints` INTEGER NOT NULL,
                            `careStreakDays` INTEGER NOT NULL,
                            `softCurrency` INTEGER NOT NULL,
                            `memoriesUnlocked` INTEGER NOT NULL,
                            `firstSeenAt` INTEGER NOT NULL,
                            `lastCheckInDay` TEXT NOT NULL,
                            `lastDailyCompletionDay` TEXT NOT NULL,
                            PRIMARY KEY(`petId`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("INSERT INTO pet_bond(petId, bondPoints, careStreakDays, softCurrency, memoriesUnlocked, firstSeenAt, lastCheckInDay, lastDailyCompletionDay) VALUES('corgi', 12, 0, 30, 0, 0, '', '')")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `daily_task_state` (
                            `id` TEXT NOT NULL,
                            `petId` TEXT NOT NULL,
                            `taskId` TEXT NOT NULL,
                            `dayKey` TEXT NOT NULL,
                            `completedAt` INTEGER NOT NULL,
                            `rewardCoins` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `owned_product` (
                            `productId` TEXT NOT NULL,
                            `productType` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `purchasedAt` INTEGER NOT NULL,
                            `restoredAt` INTEGER,
                            `acknowledged` INTEGER NOT NULL,
                            PRIMARY KEY(`productId`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `equipped_accessory` (
                            `petId` TEXT NOT NULL,
                            `accessoryId` TEXT NOT NULL,
                            `equippedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`petId`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

                override fun onOpen(db: SupportSQLiteDatabase) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun createV1Database(name: String): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `treasures` (
                            `emoji` TEXT NOT NULL,
                            `count` INTEGER NOT NULL,
                            `firstFoundAt` INTEGER NOT NULL,
                            `lastFoundAt` INTEGER NOT NULL,
                            PRIMARY KEY(`emoji`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("INSERT INTO treasures VALUES('🌟', 2, 10, 20)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

                override fun onOpen(db: SupportSQLiteDatabase) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun queryCount(db: SupportSQLiteDatabase, table: String): Int {
        db.query("SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }
}
