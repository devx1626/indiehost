package com.indiehost.launcher

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.indiehost.detect.GameEntry
import com.indiehost.detect.GameFolder
import java.io.InputStream

/**
 * A SAF document tree seen through the detector's [GameFolder] seam.
 *
 * The children are read once per folder object. A scan is a snapshot, and
 * repeated `listFiles()` calls on a slow provider are expensive.
 */
class SafGameFolder(
    private val context: Context,
    val treeUri: Uri,
    private val document: DocumentFile,
) : GameFolder {

    private val children: Map<String, DocumentFile> by lazy {
        runCatching { document.listFiles() }
            .getOrDefault(emptyArray())
            .mapNotNull { file -> file.name?.let { name -> name to file } }
            .toMap()
    }

    override val name: String get() = document.name ?: "Unnamed folder"

    override val isDirectory: Boolean get() = document.isDirectory

    override val location: String get() = document.uri.toString()

    override fun entries(): List<GameEntry> =
        children.values
            .map { GameEntry(it.name ?: "", it.isDirectory) }
            .filter { it.name.isNotEmpty() }
            .sortedBy { it.name.lowercase() }

    override fun child(name: String): GameFolder? {
        val match = children[name] ?: return null
        return SafGameFolder(context, treeUri, match)
    }

    override fun open(name: String): InputStream? {
        val match = children[name] ?: return null
        if (!match.isFile) return null
        return runCatching { context.contentResolver.openInputStream(match.uri) }.getOrNull()
    }
}
