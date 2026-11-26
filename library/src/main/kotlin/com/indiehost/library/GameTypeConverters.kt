package com.indiehost.library

import androidx.room.TypeConverter
import com.indiehost.detect.Engine

/**
 * Enums are stored by name, so the table stays readable and a rename is a
 * schema change rather than a silent reorder. Unknown values degrade instead of
 * crashing a library load after a downgrade.
 */
class GameTypeConverters {

    @TypeConverter
    fun engineToString(engine: Engine): String = engine.name

    @TypeConverter
    fun stringToEngine(value: String): Engine =
        runCatching { Engine.valueOf(value) }.getOrDefault(Engine.UNKNOWN)

    @TypeConverter
    fun sourceToString(source: GameSource): String = source.name

    @TypeConverter
    fun stringToSource(value: String): GameSource =
        runCatching { GameSource.valueOf(value) }.getOrDefault(GameSource.PINNED)
}
