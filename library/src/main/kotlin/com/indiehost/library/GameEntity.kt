package com.indiehost.library

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.indiehost.detect.Engine

/**
 * The stored shape of [Game]. Enum names are stored as text so a future engine
 * can be added without a schema migration; an unknown name maps to
 * [Engine.UNKNOWN] instead of crashing the library.
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderPath: String,
    val engine: Engine,
    val wadVersion: Int?,
    val coverPath: String?,
    val addedAt: Long,
    val source: GameSource,
    val linkedShortcutId: String?,
    val treeUri: String?,
    val runnable: Boolean,
    val blocker: String?,
    val lastSeenScanId: String?,
    val available: Boolean,
)

class GameConverters {

    @TypeConverter
    fun engineToString(value: Engine): String = value.name

    @TypeConverter
    fun stringToEngine(value: String): Engine =
        runCatching { Engine.valueOf(value) }.getOrDefault(Engine.UNKNOWN)

    @TypeConverter
    fun sourceToString(value: GameSource): String = value.name

    @TypeConverter
    fun stringToSource(value: String): GameSource =
        runCatching { GameSource.valueOf(value) }.getOrDefault(GameSource.SCANNED)
}

fun Game.toEntity(): GameEntity = GameEntity(
    id = id,
    title = title,
    folderPath = folderPath,
    engine = engine,
    wadVersion = wadVersion,
    coverPath = coverPath,
    addedAt = addedAt,
    source = source,
    linkedShortcutId = linkedShortcutId,
    treeUri = treeUri,
    runnable = runnable,
    blocker = blocker,
    lastSeenScanId = lastSeenScanId,
    available = available,
)

fun GameEntity.toGame(): Game = Game(
    id = id,
    title = title,
    folderPath = folderPath,
    engine = engine,
    wadVersion = wadVersion,
    coverPath = coverPath,
    addedAt = addedAt,
    source = source,
    linkedShortcutId = linkedShortcutId,
    treeUri = treeUri,
    runnable = runnable,
    blocker = blocker,
    lastSeenScanId = lastSeenScanId,
    available = available,
)
