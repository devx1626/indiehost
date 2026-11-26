package com.indiehost.launcher

import android.content.Context
import android.net.Uri

/**
 * Remembers which SAF tree the user chose to scan. Only the URI is stored: the
 * grant itself lives with the system and is checked on every resume.
 */
object LibraryStore {

    private const val PREFS = "indiehost.library"
    private const val KEY_TREE = "scan_tree"

    fun treeUri(context: Context): Uri? =
        prefs(context).getString(KEY_TREE, null)?.let(Uri::parse)

    fun setTreeUri(context: Context, uri: Uri) {
        prefs(context).edit().putString(KEY_TREE, uri.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
