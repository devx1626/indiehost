package com.indiehost.launcher

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Reduced-motion gate, with the Android read isolated from the pure rule. */
object Motion {

    /** Android's animator duration scale is 0 when the user asked for no motion. */
    fun enabled(scale: Float): Boolean = scale > 0f

    /** The only transition length used by the launcher, in milliseconds. */
    const val TRANSITION_MS = 150
}

@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        mutableStateOf(Motion.enabled(scale))
    }.value
}
