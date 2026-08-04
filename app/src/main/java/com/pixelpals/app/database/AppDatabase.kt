package com.pixelpals.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TreasureItem::class,
        PetStatusEntity::class,
        PetBondEntity::class,
        DailyTaskStateEntity::class,
        OwnedProductEntity::class,
        EquippedAccessoryEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun treasureDao(): TreasureDao
    abstract fun petStatusDao(): PetStatusDao
    abstract fun petBondDao(): PetBondDao
    abstract fun dailyTaskStateDao(): DailyTaskStateDao
    abstract fun ownedProductDao(): OwnedProductDao
    abstract fun equippedAccessoryDao(): EquippedAccessoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pet_bond` ADD COLUMN `lastTreasureInteractionMilestone` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `pet_bond` ADD COLUMN `lastTreasureActiveMilestone` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pet_bond` ADD COLUMN `activeMinutes` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pixelpals_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
