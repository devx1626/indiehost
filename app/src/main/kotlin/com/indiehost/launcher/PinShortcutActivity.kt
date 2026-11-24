package com.indiehost.launcher

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.util.Log

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
        if (request == null ||
            request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
        ) {
            finish()
            return
        }

        val shortcut = request.getShortcutInfo()
        if (request.accept()) {
            Log.i(TAG, "Pinned ${shortcut?.id}")
        } else {
            Log.w(TAG, "Could not pin ${shortcut?.id}")
        }
        finish()
    }

    private companion object {
        const val TAG = "IndieHostPin"

        /** requestPinShortcut and PinItemRequest are Android 8.0 APIs. */
        const val MIN_PIN_API = 26
    }
}
