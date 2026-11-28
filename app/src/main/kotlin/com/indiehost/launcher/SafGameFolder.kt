package com.indiehost.launcher

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.indiehost.detect.GameEntry
import com.indiehost.detect.GameFolder
import java.io.InputStream

/**
 * A SAF document tree seen through the detector's [GameFolder] seam.
 *
 * Children are fetched with **one** DocumentsContract query per folder instead
 * of one query per file through `DocumentFile.listFiles()`. On a Helio A22 with
 * a slow SD-card provider that difference dominates scan time.
 */
class SafGameFolder private constructor(
    private val context: Context,
    private val treeUri: Uri,
    private val folderUri: Uri,
    private val folderName: String,
) : GameFolder {

    constructor(context: Context, treeUri: Uri, document: DocumentFile) : this(
        context,
        treeUri,
        document.uri,
        document.name ?: "Unnamed folder",
    )

    private data class Child(val name: String, val documentId: String, val isDirectory: Boolean)

    private val children: List<Child> by lazy {
        val documentId = runCatching { DocumentsContract.getDocumentId(folderUri) }.getOrNull()
            ?: return@lazy emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val cursor = runCatching {
            context.contentResolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
        }.getOrNull() ?: return@lazy emptyList()

        cursor.use { rows ->
            val idIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            if (idIndex < 0 || nameIndex < 0) return@use emptyList()

            buildList {
                while (rows.moveToNext()) {
                    val name = rows.getString(nameIndex) ?: continue
                    val id = rows.getString(idIndex) ?: continue
                    val mime = if (mimeIndex >= 0) rows.getString(mimeIndex) else null
                    add(Child(name, id, mime == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        }
    }

    override val name: String get() = folderName

    override val isDirectory: Boolean get() = true

    override val location: String get() = folderUri.toString()

    override fun entries(): List<GameEntry> =
        children
            .map { GameEntry(it.name, it.isDirectory) }
            .sortedBy { it.name.lowercase() }

    override fun child(name: String): GameFolder? {
        val match = children.firstOrNull { it.name == name } ?: return null
        val uri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, match.documentId)
        }.getOrNull() ?: return null
        return SafGameFolder(context, treeUri, uri, match.name)
    }

    override fun open(name: String): InputStream? {
        val match = children.firstOrNull { it.name == name } ?: return null
        if (match.isDirectory) return null
        val uri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, match.documentId)
        }.getOrNull() ?: return null
        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    private companion object {
        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
