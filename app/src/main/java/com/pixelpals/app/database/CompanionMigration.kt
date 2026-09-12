package com.pixelpals.app.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val COMPANION_MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase): Unit {
        db.execSQL("CREATE TABLE IF NOT EXISTS companion_home (petId TEXT NOT NULL PRIMARY KEY, nickname TEXT NOT NULL, environment TEXT NOT NULL, adoptedAt INTEGER NOT NULL, feedCount INTEGER NOT NULL, playCount INTEGER NOT NULL, touchCount INTEGER NOT NULL, lastLearnedAt INTEGER NOT NULL, favoriteObject TEXT NOT NULL, desktopObject TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS home_decoration (petId TEXT NOT NULL, decorationId TEXT NOT NULL, `column` INTEGER NOT NULL, `row` INTEGER NOT NULL, PRIMARY KEY(petId, decorationId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS decoration_inventory (decorationId TEXT NOT NULL PRIMARY KEY, acquiredAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS companion_journal (id TEXT NOT NULL PRIMARY KEY, petId TEXT NOT NULL, kind TEXT NOT NULL, detail TEXT NOT NULL, occurredAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS companion_expedition (slot INTEGER NOT NULL PRIMARY KEY, requestId TEXT NOT NULL, petId TEXT NOT NULL, destination TEXT NOT NULL, startedAt INTEGER NOT NULL, lastWallTime INTEGER NOT NULL, lastUptime INTEGER NOT NULL, bootCount INTEGER NOT NULL, elapsedMs INTEGER NOT NULL)")
    }
}

/** Preserve homes already created with the first internal companion preview. */
val COMPANION_MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase): Unit {
        db.execSQL("ALTER TABLE companion_expedition ADD COLUMN resumeDesktop INTEGER NOT NULL DEFAULT 0")
    }
}

/** Preserve existing homes while adding the optional per-pet exhibited treasure. */
val COMPANION_MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase): Unit {
        db.execSQL("ALTER TABLE companion_home ADD COLUMN selectedTreasureId TEXT DEFAULT NULL")
    }
}
