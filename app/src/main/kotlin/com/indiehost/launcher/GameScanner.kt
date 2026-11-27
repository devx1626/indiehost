package com.indiehost.launcher

import com.indiehost.detect.Engine
import com.indiehost.detect.EngineDetector
import com.indiehost.detect.GameFolder
import com.indiehost.library.Game
import com.indiehost.library.GameSource
import java.security.MessageDigest

data class ScanResult(
    val games: List<Game>,
    val skipped: List<String>,
    val error: String? = null,
)

/**
 * Walks a folder tree and imports every engine the detector recognises.
 *
 * Pure apart from the [GameFolder] it is handed, so the walk is host-testable.
 * The scan never deletes: a re-scan upserts by stable id and preserves the
 * cover and pin link the user set.
 */
class GameScanner(private val detector: EngineDetector = EngineDetector()) {

    fun scan(
        root: GameFolder,
        treeUri: String,
        existing: List<Game>,
        now: Long = System.currentTimeMillis(),
        scanId: String = "",
    ): ScanResult {
        if (!root.isDirectory) {
            return ScanResult(emptyList(), emptyList(), "The selected location is not a folder")
        }

        val existingById = existing.associateBy { it.id }
        val games = mutableListOf<Game>()
        val skipped = mutableListOf<String>()

        fun visit(folder: GameFolder, depth: Int) {
            val entries = try {
                folder.entries()
            } catch (e: Exception) {
                skipped += "${folder.name}: ${e.message ?: "unreadable"}"
                return
            }

            if (entries.size > MAX_ENTRIES) {
                skipped += "${folder.name}: more than $MAX_ENTRIES entries"
                return
            }

            val detection = try {
                detector.detect(folder)
            } catch (e: Exception) {
                skipped += "${folder.name}: ${e.message ?: "detector failed"}"
                return
            }

            if (detection.engine != Engine.UNKNOWN) {
                val id = idFor(treeUri, folder.location)
                val prior = existingById[id]
                games += Game(
                    id = id,
                    title = folder.name,
                    folderPath = folder.location,
                    engine = detection.engine,
                    wadVersion = detection.wadVersion,
                    coverPath = prior?.coverPath,
                    addedAt = prior?.addedAt ?: now,
                    source = GameSource.SCANNED,
                    linkedShortcutId = prior?.linkedShortcutId,
                    treeUri = treeUri,
                    runnable = detection.runnable,
                    blocker = detection.blocker,
                    lastSeenScanId = scanId,
                    available = true,
                )
                // A recognised game is a leaf: do not import its www/ or game/ internals.
                return
            }

            if (depth >= MAX_DEPTH) return
            entries.filter { it.isDirectory }.forEach { entry ->
                val child = folder.child(entry.name)
                if (child == null) {
                    skipped += "${folder.name}/${entry.name}: disappeared"
                } else {
                    visit(child, depth + 1)
                }
            }
        }

        visit(root, 0)
        return ScanResult(games.sortedBy { it.title.lowercase() }, skipped)
    }

    companion object {
        /** Root plus two levels is enough for a parent folder full of games. */
        const val MAX_DEPTH = 2

        /** A guardrail for a provider that returns an enormous directory. */
        const val MAX_ENTRIES = 500

        /** Stable across scans of the same tree and document. */
        fun idFor(treeUri: String, location: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$treeUri|$location".toByteArray(Charsets.UTF_8))
            return "scan-" + digest.joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
        }
    }
}
