package com.indiehost.library

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.indiehost.detect.Engine

/**
 * Stored form of [Game]. `folderPath` keeps its name for compatibility but holds
 * a SAF tree/document URI string, never a filesystem path the app assumes exists.
 *
 * Game data is never stored here: only metadata the user can regenerate by
 * re-scanning the folder.
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
)
