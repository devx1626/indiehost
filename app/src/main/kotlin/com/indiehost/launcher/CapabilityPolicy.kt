package com.indiehost.launcher

import com.indiehost.detect.Engine

/** Local-only facts that decide which launcher actions are possible right now. */
data class LauncherCapabilities(
    val isHome: Boolean,
    val butterscotchInstalled: Boolean,
    val hasGameFolder: Boolean,
    val webViewAvailable: Boolean = true,
)

/**
 * Capability decisions are pure so the UI can explain a disabled action instead
 * of silently doing nothing. Nothing here starts an app or links a runtime.
 */
object CapabilityPolicy {

    fun canLaunchGame(engine: Engine, caps: LauncherCapabilities): Boolean = when (engine) {
        Engine.GAMEMAKER_VM -> caps.butterscotchInstalled
        Engine.RPGMAKER_MV, Engine.RPGMAKER_MZ -> caps.webViewAvailable && caps.hasGameFolder
        else -> false
    }

    fun cannotLaunchReason(engine: Engine, caps: LauncherCapabilities): String? = when {
        canLaunchGame(engine, caps) -> null
        engine == Engine.GAMEMAKER_VM && !caps.butterscotchInstalled ->
            "Butterscotch is not installed."
        engine == Engine.RPGMAKER_MV || engine == Engine.RPGMAKER_MZ ->
            "WebView or a scanned games folder is unavailable."
        else -> "This engine has no runtime."
    }
}
