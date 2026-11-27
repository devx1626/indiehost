package com.indiehost.launcher

import com.indiehost.detect.Engine
import com.indiehost.library.Game

/** Why a scanned row cannot launch yet. */
enum class LibraryReason {
    NEEDS_PIN,
    WEBVIEW_M2,
    RUNTIME_M3,
    DETECTOR_BLOCKED,
}

/**
 * One row of the merged library. Pinned shortcuts and scanned games are
 * separate sources, so [shortcutId] is the explicit link between them.
 */
data class LibraryItem(
    val key: String,
    val title: String,
    val game: Game?,
    val shortcutId: String?,
    val launchable: Boolean,
    val reason: LibraryReason?,
    val coverPath: String?,
)

/** The part of a pinned shortcut the merge needs; keeps the merge host-testable. */
data class LibraryPin(val id: String, val title: String)

/**
 * Pinned shortcuts first, then scanned games. A pin that a scanned game links
 * to is hidden: the scanned row represents it and can show its icon.
 *
 * No title matching is attempted. Butterscotch owns the shortcut id and the
 * label, and a wrong match would launch the wrong game.
 */
fun mergeLibrary(games: List<Game>, pins: List<LibraryPin>): List<LibraryItem> {
    val pinIds = pins.map { it.id }.toSet()
    val linkedIds = games.mapNotNull { it.linkedShortcutId }.toSet()

    val scanned = games.map { game ->
        val linked = game.linkedShortcutId
        val reason = when {
            game.blocker != null -> LibraryReason.DETECTOR_BLOCKED
            game.engine == Engine.GAMEMAKER_VM && linked != null && linked in pinIds -> null
            game.engine == Engine.GAMEMAKER_VM -> LibraryReason.NEEDS_PIN
            game.engine == Engine.RPGMAKER_MV || game.engine == Engine.RPGMAKER_MZ ->
                if (game.treeUri.isNullOrBlank()) LibraryReason.WEBVIEW_M2 else null
            game.engine == Engine.RENPY || game.engine == Engine.LOVE ||
                game.engine == Engine.SCUMMVM -> LibraryReason.RUNTIME_M3
            else -> LibraryReason.RUNTIME_M3
        }
        LibraryItem(
            key = "game:${game.id}",
            title = game.title,
            game = game,
            shortcutId = linked,
            launchable = reason == null,
            reason = reason,
            coverPath = game.coverPath,
        )
    }

    val pinOnly = pins
        .filter { it.id !in linkedIds }
        .map { pin ->
            LibraryItem(
                key = "pin:${pin.id}",
                title = pin.title,
                game = null,
                shortcutId = pin.id,
                launchable = true,
                reason = null,
                coverPath = null,
            )
        }

    return pinOnly + scanned
}
