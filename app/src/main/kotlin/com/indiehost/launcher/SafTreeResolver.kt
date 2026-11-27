package com.indiehost.launcher

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream

/**
 * Resolves a path inside one scanned game folder without copying anything.
 *
 * Both URIs come from the M1 scan: [treeUri] carries the persisted grant and
 * [folderUri] is the document the detector recognised. Starting from the
 * document id keeps the lookup inside that folder; no other tree is read.
 */
class SafTreeResolver(
    context: Context,
    private val treeUri: Uri,
    folderUri: Uri,
) {
    private val resolver = context.contentResolver
    private val folderDocumentId: String = DocumentsContract.getDocumentId(folderUri)

    /** Returns null for an unsafe path, a missing document or a revoked grant. */
    fun open(relative: String): InputStream? {
        val segments = WebPaths.segments(relative) ?: return null
        var documentId = folderDocumentId

        for (segment in segments) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            ) ?: return null

            val found = cursor.use { rows ->
                val idIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                var id: String? = null
                while (rows.moveToNext()) {
                    if (rows.getString(nameIndex) == segment) {
                        id = rows.getString(idIndex)
                        break
                    }
                }
                id
            }
            documentId = found ?: return null
        }

        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

        // Refuse oversized local assets before opening a stream. A malicious or
        // broken game page must not make WebView map a huge file into memory.
        val sizeCursor = resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_SIZE),
            null,
            null,
            null,
        )
        sizeCursor?.use { rows ->
            val index = rows.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            if (index >= 0 && rows.moveToNext() && !rows.isNull(index) &&
                rows.getLong(index) > WebPaths.MAX_ASSET_BYTES
            ) {
                return null
            }
        }

        return resolver.openInputStream(documentUri)
    }

    /** True when the document exists and can still be read. */
    fun exists(relative: String): Boolean = open(relative)?.use { true } ?: false
}
