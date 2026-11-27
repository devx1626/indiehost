package com.indiehost.library

import com.indiehost.detect.Engine

/** Where a library row came from. */
enum class GameSource {
    /** A Butterscotch shortcut pinned on the home screen. */
    PINNED,

    /** A folder imported by the SAF scanner. */
    SCANNED,
}

/**
 * A game the user has pointed us at. Only the folder location is stored: the
 * game files themselves are never copied into the repo, an APK or a backup.
 *
 * [folderPath] is an opaque location string. For a scanned game it is the SAF
 * document URI; for a pinned game it may be empty because Butterscotch owns
 * the files.
 */
data class Game(
    val id: String,
    val title: String,
    val folderPath: String,
    val engine: Engine,
    val wadVersion: Int? = null,
    /** File name inside `filesDir/covers`, never a full path or a game asset. */
    val coverPath: String? = null,
    val addedAt: Long = 0L,
    val source: GameSource = GameSource.PINNED,
    /** The `game-<uuid>` shortcut the user explicitly linked to this scan. */
    val linkedShortcutId: String? = null,
    /** SAF tree URI, when [folderPath] is a document inside that tree. */
    val treeUri: String? = null,
    /** The detector's verdict at import time; false keeps Launch disabled. */
    val runnable: Boolean = true,
    /** Human-readable reason when [runnable] is false. */
    val blocker: String? = null,
)
