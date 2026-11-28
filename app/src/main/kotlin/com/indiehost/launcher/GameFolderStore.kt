package com.indiehost.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Remembers the SAF trees the user added.
 *
 * Only tree URIs live here; no game data and no filesystem paths. The
 * persisted read grant is what lets a later scan work after a restart.
 */
class GameFolderStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<Uri> = prefs.getStringSet(KEY_TREES, emptySet())
        .orEmpty()
        .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        .distinct()

    /** Returns false when the provider would not grant a persistable read. */
    fun add(uri: Uri): Boolean {
        val persisted = runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        if (!persisted) return false

        prefs.edit()
            .putStringSet(KEY_TREES, (all().map { it.toString() } + uri.toString()).toSet())
            .apply()
        return true
    }

    fun remove(uri: Uri) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs.edit()
            .putStringSet(KEY_TREES, (all().map { it.toString() } - uri.toString()).toSet())
            .apply()
    }

    /** True when the persisted grant is still present. */
    fun hasPermission(uri: Uri): Boolean =
        appContext.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }

    private companion object {
        const val PREFS = "indiehost.folders"
        const val KEY_TREES = "trees"
    }
}
