package com.indiehost.launcher

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.indiehost.runtimes.PinCandidate
import com.indiehost.runtimes.PinPolicy

/**
 * Launchers receive pin requests ("Add Shortcut to Home Screen") here.
 *
 * Android only lets an app ask to pin a shortcut when the current home app
 * declares this activity, and a pin belongs to the launcher that accepted it.
 * That means Butterscotch's pin request fails — and its game never appears in
 * our list — unless we answer it ourselves.
 *
 * We have no icon grid to place anything on, and we don't need one: the home
 * screen is built from [LauncherApps]'s list of pinned shortcuts, so accepting
 * the request and finishing is exactly enough. The pin is recorded by the
 * system against IndieHost and shows up the next time we refresh.
 *
 * Only Butterscotch's per-game shortcuts (`game-`) are accepted. Any other app
 * that asks IndieHost to pin something is declined by finishing without
 * calling [LauncherApps.PinItemRequest.accept].
 */
class PinShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < MIN_PIN_API) {
            finish()
            return
        }

        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = launcherApps?.getPinItemRequest(intent)
        if (request == null) {
            finish()
            return
        }

        val shortcut = request.getShortcutInfo()
        val candidate = PinCandidate(
            packageName = shortcut?.`package`,
            shortcutId = shortcut?.id,
            isValid = runCatching { request.isValid }.getOrDefault(false),
            requestType = request.requestType,
        )
        if (!PinPolicy.accept(candidate)) {
            // Finishing without accept() refuses the request. A launcher should
            // not silently adopt shortcuts it cannot launch as games, and a
            // cancelled/invalid request must never be accepted either.
            Log.w(TAG, "Ignored pin request from ${candidate.packageName}/${candidate.shortcutId}")
            finish()
            return
        }

        if (runCatching { request.accept() }.getOrDefault(false)) {
            Log.i(TAG, "Pinned ${candidate.shortcutId}")
        } else {
            Log.w(TAG, "Could not pin ${candidate.shortcutId}")
        }
        finish()
    }

    private companion object {
        const val TAG = "IndieHostPin"

        /** requestPinShortcut and PinItemRequest are Android 8.0 APIs. */
        const val MIN_PIN_API = 26
    }
}
