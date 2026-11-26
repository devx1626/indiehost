package com.indiehost.launcher

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import com.indiehost.detect.GameEntry
import com.indiehost.detect.GameFolder
import java.io.InputStream

/**
 * [GameFolder] over a SAF document. Nothing is copied: the detector reads the
 * folder in place, so a multi-hundred-megabyte `data.win` is only opened and
 * skipped through.
 *
 * SAF calls can fail (revoked grant, missing document), and the detector treats
 * a missing entry as "not there" rather than an error, so every call here is
 * guarded instead of throwing.
 */
class SafGameFolder(
    private val document: DocumentFile,
    private val resolver: ContentResolver,
) : GameFolder {

    override val name: String = document.name.orEmpty()

    override fun entries(): List<GameEntry> =
        runCatching { document.listFiles() }
            .getOrDefault(emptyArray())
            .map { GameEntry(it.name.orEmpty(), it.isDirectory) }

    override fun child(name: String): GameFolder? {
        val child = runCatching { document.findFile(name) }.getOrNull() ?: return null
        return if (child.isDirectory) SafGameFolder(child, resolver) else null
    }

    override fun open(name: String): InputStream? {
        val child = runCatching { document.findFile(name) }.getOrNull() ?: return null
        if (!child.isFile) return null
        return runCatching { resolver.openInputStream(child.uri) }.getOrNull()
    }
}
