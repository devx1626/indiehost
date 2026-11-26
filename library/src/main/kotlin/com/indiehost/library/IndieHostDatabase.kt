package com.indiehost.library

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Version 1 schema. Destructive migration is allowed only while the library is
 * pre-release (see ADR-0006); a real migration policy starts at 1.0.
 *
 * The database is app-private and excluded from backup by `allowBackup="false"`,
 * so a full uninstall clears it and the user restores the library by re-scanning.
 */
@Database(entities = [GameEntity::class], version = 1, exportSchema = false)
@TypeConverters(GameTypeConverters::class)
abstract class IndieHostDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    companion object {
        private const val NAME = "indiehost.db"

        fun create(context: Context): IndieHostDatabase =
            Room.databaseBuilder(context.applicationContext, IndieHostDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
