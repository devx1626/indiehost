package com.indiehost.library

import com.indiehost.detect.Engine

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
)
