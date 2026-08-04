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
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

        try {
            val treasure = db.treasureDao().getTreasure("🌟")
            assertNotNull(treasure)
            assertEquals(2, treasure?.count)

            assertEquals(1, queryCount(db.openHelper.writableDatabase, "pet_status"))
            assertEquals(1, queryCount(db.openHelper.writableDatabase, "pet_bond"))
            assertEquals(1, queryCount(db.openHelper.writableDatabase, "daily_task_state"))
            assertEquals(1, queryCount(db.openHelper.writableDatabase, "owned_product"))
            assertEquals(1, queryCount(db.openHelper.writableDatabase, "equipped_accessory"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
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
