package com.indiehost.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * "Recent apps" without a system recents API.
 *
 * Android gives launchers no public way to read the activity stack, so there
 * are two honest sources:
 *
 * 1. Usage access (the switch in Settings -> Apps -> Special access -> Usage
 *    access). When the user grants it we ask the system for the apps that were
 *    brought to the foreground recently, which is the real list.
 * 2. Our own launches, remembered locally. This always works, needs no
 *    permission, and quietly covers the apps you start from IndieHost.
 *
 * Both feed the same row, ordered by the most recent time each app was used.
 */
object Recents {

    private const val PREFS = "indiehost.recents"
    private const val KEY_LAUNCHES = "launches"
    private const val MAX_REMEMBERED = 12

    /** Three days is long enough to fill a row without scanning the whole log. */
    private const val WINDOW_MS = 3L * 24 * 60 * 60 * 1000

    private const val LIMIT = 8

    /** True when the user has granted usage access to IndieHost. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * The apps to show in the recent row, resolved against the drawer list so
     * every entry has a label and an icon. Blocks on the usage log, so call it
     * off the main thread.
     */
    fun pick(
        context: Context,
        apps: List<LauncherApp>,
        usageAccess: Boolean,
    ): List<LauncherApp> {
        val byPackage = apps.associateBy { it.packageName }
        val merged = LinkedHashSet<String>()

        // System order first (newest first), then anything we launched ourselves
        // that the usage log has not caught up with yet.
        if (usageAccess) {
            systemRecency(context)
                .entries
                .sortedByDescending { it.value }
                .forEach { merged.add(it.key) }
        }
        rememberedLaunches(context).forEach { merged.add(it) }

        return merged
            .mapNotNull { byPackage[it] }
            .take(LIMIT)
    }

    /** Remembers that we launched [packageName] a moment ago. */
    fun record(context: Context, packageName: String) {
        if (packageName == context.packageName) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = rememberedLaunches(context).filter { it != packageName }
        val updated = (listOf(packageName) + existing).take(MAX_REMEMBERED)
        // `apply` is enough: losing this list on a hard kill only empties a row.
        prefs.edit().putString(KEY_LAUNCHES, updated.joinToString("\n")).apply()
    }

    private fun rememberedLaunches(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAUNCHES, "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /** Package -> time it was last brought to the foreground, from the usage log. */
    private fun systemRecency(context: Context): Map<String, Long> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val now = System.currentTimeMillis()
        val events = try {
            manager.queryEvents(now - WINDOW_MS, now)
        } catch (e: Exception) {
            return emptyMap()
        } ?: return emptyMap()

        val event = UsageEvents.Event()
        val latest = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != RESUMED_EVENT) continue
            val packageName = event.packageName ?: continue
            if (packageName == context.packageName) continue
            val previous = latest[packageName]
            if (previous == null || previous < event.timeStamp) {
                latest[packageName] = event.timeStamp
            }
        }
        return latest
    }

    /** ACTIVITY_RESUMED from API 29; the older constant below it means the same thing. */
    private val RESUMED_EVENT: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
}
