package com.pixelpals.app.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreasureCollectionMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrate7To8PreservesInventoryAndAddsGiftState(): Unit {
        context.deleteDatabase(DATABASE_NAME)
        val database: SupportSQLiteDatabase = createVersionSevenDatabase()
        AppDatabase.MIGRATION_7_8.migrate(database)
        database.query("SELECT count, totalFound, firstFoundAt, lastFoundAt FROM treasures WHERE emoji = '🌙'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(100L, cursor.getLong(2))
            assertEquals(200L, cursor.getLong(3))
        }
        database.query(
            "SELECT lastTreasureGiftDay, treasuresGifted, favoriteTreasuresGifted FROM pet_bond WHERE petId = 'bloop'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
        database.query("SELECT lastRewardedMilestone, completedAt, finalCollectorPetId FROM treasure_collection_state").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            assertEquals(0L, cursor.getLong(1))
            assertEquals("", cursor.getString(2))
        }
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun createVersionSevenDatabase(): SupportSQLiteDatabase {
        val configuration: SupportSQLiteOpenHelper.Configuration =
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase): Unit {
                        db.execSQL(
                            """
                            CREATE TABLE `treasures` (
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
                            CREATE TABLE `pet_bond` (
                                `petId` TEXT NOT NULL,
                                `bondPoints` INTEGER NOT NULL,
                                `careStreakDays` INTEGER NOT NULL,
                                `softCurrency` INTEGER NOT NULL,
                                `memoriesUnlocked` INTEGER NOT NULL,
                                `firstSeenAt` INTEGER NOT NULL,
                                `lastCheckInDay` TEXT NOT NULL,
                                `lastDailyCompletionDay` TEXT NOT NULL,
                                `lastTreasureInteractionMilestone` INTEGER NOT NULL,
                                `lastTreasureActiveMilestone` INTEGER NOT NULL,
                                `activeMinutes` INTEGER NOT NULL,
                                `illnessRecoveries` INTEGER NOT NULL,
                                PRIMARY KEY(`petId`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO treasures VALUES('🌙', 3, 100, 200)")
                        db.execSQL(
                            """
                            INSERT INTO pet_bond VALUES(
                                'bloop', 42, 3, 7, 2, 10, '2026-08-24', '2026-08-24', 1, 2, 9, 1
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ): Unit = Unit
                })
                .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private companion object {
        const val DATABASE_NAME: String = "treasure-collection-migration-test"
    }
}
