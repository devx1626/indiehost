package com.indiehost.library

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 was the M1 schema. Version 2 adds scan provenance and an availability
 * flag; rows are never deleted by a scan, only marked unavailable.
 */
@Database(entities = [GameEntity::class, ScanRunEntity::class], version = 2, exportSchema = false)
@TypeConverters(GameConverters::class)
abstract class IndieHostDatabase : RoomDatabase() {

    abstract fun games(): GameDao

    companion object {
        private const val NAME = "indiehost.db"

        @Volatile
        private var instance: IndieHostDatabase? = null

        fun get(context: Context): IndieHostDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    IndieHostDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}

/** Additive: neither column loses user data, and the new table starts empty. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN lastSeenScanId TEXT")
        db.execSQL("ALTER TABLE games ADD COLUMN available INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS scan_runs (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "startedAt INTEGER NOT NULL, " +
                "finishedAt INTEGER NOT NULL, " +
                "imported INTEGER NOT NULL, " +
                "skipped INTEGER NOT NULL, " +
                "error TEXT)",
        )
    }
}
