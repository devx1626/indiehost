package com.indiehost.launcher

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.indiehost.detect.Engine
import com.indiehost.detect.EngineDetector
import com.indiehost.library.Game
import com.indiehost.library.GameSource

/** A folder the scan could not turn into a game, and the short reason. */
data class ScanSkip(val folder: String, val reason: String)

data class ScanResult(val games: List<Game>, val skipped: List<ScanSkip>)

/**
 * Walks a SAF tree and fingerprints every folder that looks like a game.
 *
 * The user usually picks either the folder that holds their games or one game's
 * folder, so the scan looks at the root and two levels below it. It never
 * recurses without a bound: a folder with too many entries is reported and not
 * descended into, which keeps a picked `/` from walking the whole card.
 *
 * Blocks. Call from a worker thread.
 */
object GameScanner {

    private const val MAX_DEPTH = 2
    private const val MAX_ENTRIES = 400

    fun scan(context: Context, treeUri: Uri): ScanResult {
        val resolver = context.contentResolver
        val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            ?: return ScanResult(emptyList(), listOf(ScanSkip("", "The folder could not be opened")))

        val games = mutableListOf<Game>()
        val skipped = mutableListOf<ScanSkip>()
        visit(root, treeUri, "", 0, resolver, games, skipped)
        return ScanResult(games, skipped)
    }

    private fun visit(
        dir: DocumentFile,
        treeUri: Uri,
        path: String,
        depth: Int,
        resolver: ContentResolver,
        games: MutableList<Game>,
        skipped: MutableList<ScanSkip>,
    ) {
        val label = dir.name?.takeIf { it.isNotBlank() } ?: "Untitled folder"
        val folder = SafGameFolder(dir, resolver)
        val entries = folder.entries()

        if (entries.size > MAX_ENTRIES) {
            skipped += ScanSkip(label, "Too many files to scan (${entries.size})")
            return
        }

        val detection = try {
            EngineDetector().detect(folder)
        } catch (e: Exception) {
            skipped += ScanSkip(label, "Could not read this folder")
            null
        }

        if (detection != null && detection.engine != Engine.UNKNOWN) {
            if (detection.runnable) {
                games += Game(
                    id = "scan:$treeUri:$path",
                    title = label,
                    folderPath = dir.uri.toString(),
                    engine = detection.engine,
                    wadVersion = detection.wadVersion,
                    addedAt = System.currentTimeMillis(),
                    source = GameSource.SCANNED,
                )
            } else {
                skipped += ScanSkip(label, detection.blocker ?: "Not launchable")
            }
        }

        if (depth >= MAX_DEPTH) return
        for (entry in entries.filter { it.isDirectory }) {
            val child = runCatching { dir.findFile(entry.name) }.getOrNull() ?: continue
            if (!child.isDirectory) continue
            val childPath = if (path.isEmpty()) entry.name else "$path/${entry.name}"
            visit(child, treeUri, childPath, depth + 1, resolver, games, skipped)
        }
    }
}
