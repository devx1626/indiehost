package com.indiehost.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** One launchable app on the device, ready to draw and start. */
class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?,
) {
    internal val component: ComponentName get() = ComponentName(packageName, activityName)
}

/**
 * Everything the app drawer needs.
 *
 * Uses [LauncherApps] rather than a raw PackageManager query: it is the API the
 * system intends for home apps, it returns exactly the activities that appear in
 * an app drawer (no services, no hidden settings pages), and it starts them
 * through [LauncherApps.startMainActivity] the same way the stock launcher does.
 */
object InstalledApps {

    /**
     * Loads the drawer list. Blocks, so call it off the main thread: reading
     * labels and icons for a hundred apps takes a moment on a Helio A22.
     */
    fun load(context: Context): List<LauncherApp> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return emptyList()
        val activities = try {
            launcherApps.getActivityList(null, Process.myUserHandle())
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: IllegalStateException) {
            return emptyList()
        }

        return activities
            .mapNotNull { info ->
                val component = info.componentName ?: return@mapNotNull null
                // A home app should not list itself; the Home key is how you get back.
                if (component.packageName == context.packageName) return@mapNotNull null

                val packageName = component.packageName
                // LauncherActivityInfo already resolves the visible label and icon;
                // no PackageManager query is needed (or desirable on Android 11+).
                val label = info.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: packageName
                val icon = runCatching { info.getIcon(0) }.getOrNull()?.toImageBitmap()

                LauncherApp(
                    packageName = packageName,
                    activityName = component.className,
                    label = label,
                    icon = icon,
                )
            }
            // Apps can register more than one launcher activity; one tile each.
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Returns false when the app refused to start (uninstalled mid-tap, profile locked). */
    fun launch(context: Context, app: LauncherApp): Boolean {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        return try {
            // Android 14 added an options Bundle; older releases ignore it.
            launcherApps.startMainActivity(app.component, Process.myUserHandle(), Rect(), Bundle())
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Turns an app icon into something Compose can draw.
 *
 * Deliberately does not depend on drawable-to-bitmap helpers from a support
 * library: this is the whole conversion, and it works for adaptive icons too.
 */
internal fun Drawable.toImageBitmap(maxSizePx: Int = 256): ImageBitmap? = runCatching {
    // LauncherApps hands out shared drawables; never mutate the cached instance.
    val drawable = constantState?.newDrawable()?.mutate() ?: this

    val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxSizePx
    val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxSizePx
    val scale = (maxSizePx.toFloat() / max(intrinsicWidth, intrinsicHeight)).coerceAtMost(1f)
    val width = (intrinsicWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (intrinsicHeight * scale).roundToInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bitmap.asImageBitmap()
}.getOrNull()
