package com.indiehost.library

import com.indiehost.detect.Engine

/** Where a library row came from. */
enum class GameSource {
    /** A shortcut Butterscotch let the user pin. */
    PINNED,

    /** A folder the user scanned. */
    SCANNED,
}

/**
 * A game the user has pointed us at. Only the folder path is stored: the game
 * files themselves are never copied into the repo, an APK or a backup.
 */
data class Game(
    val id: String,
    val title: String,
    val folderPath: String,
    val engine: Engine,
    val wadVersion: Int? = null,
    val coverPath: String? = null,
    val addedAt: Long = 0L,
    val source: GameSource = GameSource.PINNED,
    /** A pinned Butterscotch shortcut the user explicitly linked, never a title guess. */
    val linkedShortcutId: String? = null,
)
