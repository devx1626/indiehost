package com.indiehost.library

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Version 1 is the M1 schema. Destructive migration is allowed during M1 only;
 * after the library is in daily use, a real migration is required.
 */
@Database(entities = [GameEntity::class], version = 1, exportSchema = false)
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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
