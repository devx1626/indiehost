package com.indiehost.runtimes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process

/**
 * A game that already exists inside the runner app, surfaced as one of the
 * runner's pinned home-screen shortcuts.
 *
 * We never see the shortcut's Intent: Android strips it for launchers. What we
 * get is the id, the label and the icon, which is exactly enough to show the
 * game and ask the system to start it.
 */
class RunnerShortcut internal constructor(
    val id: String,
    val title: String,
    val icon: Drawable?,
    internal val info: ShortcutInfo,
) {
    val packageName: String get() = info.`package` ?: GameMakerRuntimeAdapter.BUTTERSCOTCH_PACKAGE
}

/**
 * Reads and launches Butterscotch's pinned per-game shortcuts.
 *
 * Butterscotch imports a game into its private storage and names the pinned
 * shortcut `game-<uuid>`. Because the game files stay in its folder, this is
 * the only point where both apps meet: no data is copied, nothing is linked,
 * and our launcher stays independent of their AGPL runner.
 *
 * Reading shortcuts requires shortcut host permission, which Android grants to
 * the current home app. If IndieHost is not the home app, [list] returns empty
 * and callers fall back to opening Butterscotch itself.
 */
class ButterscotchShortcuts(private val context: Context) {

    private val launcherApps: LauncherApps?
        get() = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    /** True only while this app is the current home app, i.e. when it can see shortcuts. */
    fun canRead(): Boolean {
        if (Build.VERSION.SDK_INT < MIN_SHORTCUT_API) return false
        val apps = launcherApps ?: return false
        return apps.hasShortcutHostPermission()
    }

    fun list(): List<RunnerShortcut> {
        if (Build.VERSION.SDK_INT < MIN_SHORTCUT_API) return emptyList()
        val apps = launcherApps ?: return emptyList()
        if (!apps.hasShortcutHostPermission()) return emptyList()

        val query = LauncherApps.ShortcutQuery()
            .setPackage(GameMakerRuntimeAdapter.BUTTERSCOTCH_PACKAGE)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)

        val shortcuts = try {
            apps.getShortcuts(query, Process.myUserHandle())
        } catch (e: SecurityException) {
            // Permission was revoked (another app became the home app) between
            // the check above and the query. Treat it as "no games to show".
            null
        } catch (e: IllegalStateException) {
            // User profile is locked or shutting down.
            null
        } ?: return emptyList()

        return shortcuts
            .mapNotNull { it.toRunnerShortcut(apps) }
            .sortedBy { it.title.lowercase() }
    }

    /** Returns false when the request could not be handed to Butterscotch. */
    fun start(shortcut: RunnerShortcut): Boolean {
        if (Build.VERSION.SDK_INT < MIN_SHORTCUT_API) return false
        val apps = launcherApps ?: return false
        if (!apps.hasShortcutHostPermission()) return false
        return try {
            apps.startShortcut(shortcut.info, null, null)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalStateException) {
            false
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun ShortcutInfo.toRunnerShortcut(apps: LauncherApps): RunnerShortcut? {
        val shortcutId = id ?: return null
        // Only Butterscotch's per-game shortcuts ("game-<uuid>") are games;
        // anything else it may pin later is not ours to launch. A shortcut that
        // was disabled or unpinned while we were in the background must not be
        // shown as launchable even if the system query returned it.
        if (!PinPolicy.visible(
                PinCandidate(
                    packageName = `package`,
                    shortcutId = shortcutId,
                    isEnabled = isEnabled,
                    isPinned = isPinned,
                )
            )
        ) return null

        val label = (shortLabel ?: longLabel)?.toString()?.takeIf { it.isNotBlank() }
            ?: shortcutId.removePrefix(GAME_SHORTCUT_PREFIX)

        // ShortcutInfo has no public icon getter; the launcher API does. A
        // stale shortcut can fail to load its icon, in which case the UI falls
        // back to a letter tile.
        val density = context.resources.displayMetrics.densityDpi
        val icon = runCatching { apps.getShortcutIconDrawable(this, density) }.getOrNull()

        return RunnerShortcut(id = shortcutId, title = label, icon = icon, info = this)
    }

    companion object {
        /** LauncherApps shortcut APIs landed in Android 7.1. */
        const val MIN_SHORTCUT_API = 25

        /** How Butterscotch names its game shortcuts. */
        const val GAME_SHORTCUT_PREFIX = "game-"
    }
}
