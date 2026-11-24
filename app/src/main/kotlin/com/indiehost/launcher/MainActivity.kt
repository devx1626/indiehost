package com.indiehost.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.indiehost.detect.Engine
import com.indiehost.library.Game
import com.indiehost.runtimes.GameMakerRuntimeAdapter
import com.indiehost.runtimes.LaunchOutcome
import com.indiehost.runtimes.RunnerShortcut

/**
 * The home app.
 *
 * Two screens: [HomeScreen] with recents and the pinned games, and [AppDrawer]
 * with everything installed. Both are built from system data - [InstalledApps]
 * and Butterscotch's pinned shortcuts - so the launcher holds no state of its
 * own beyond which of the two screens is showing.
 */
class MainActivity : ComponentActivity() {

    private val runtime = GameMakerRuntimeAdapter()

    private var games by mutableStateOf<List<RunnerShortcut>>(emptyList())
    private var canListGames by mutableStateOf(false)

    private var apps by mutableStateOf<List<LauncherApp>>(emptyList())
    private var recents by mutableStateOf<List<LauncherApp>>(emptyList())
    private var usageAccess by mutableStateOf(false)
    private var loadingApps by mutableStateOf(true)

    private var drawerOpen by mutableStateOf(false)
    private var status by mutableStateOf("")

    @Volatile
    private var appsLoaded = false

    @Volatile
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshGames()
        loadApps()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    // Back closes the drawer; on the home screen there is nowhere
                    // to go, which is what a home screen should do.
                    BackHandler(enabled = drawerOpen) { drawerOpen = false }

                    if (drawerOpen) {
                        AppDrawer(
                            apps = apps,
                            loading = loadingApps,
                            onClose = { drawerOpen = false },
                            onLaunch = ::launchApp,
                            onAppInfo = ::openAppInfo,
                        )
                    } else {
                        HomeScreen(
                            games = games,
                            canListGames = canListGames,
                            recents = recents,
                            loadingApps = loadingApps,
                            usageAccess = usageAccess,
                            status = status,
                            onOpenDrawer = { drawerOpen = true },
                            onLaunchGame = ::launchGame,
                            onLaunchApp = ::launchApp,
                            onOpenRunner = ::openRunner,
                            onChooseHomeApp = ::openHomeSettings,
                            onAllowUsage = ::openUsageSettings,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Pressing Home while already here returns to the home screen.
        drawerOpen = false
    }

    override fun onResume() {
        super.onResume()
        // Games are pinned in another app and usage access can be granted from
        // Settings, so both lists can change while we sit in the background.
        refreshGames()
        if (appsLoaded) refreshRecents() else loadApps()
    }

    private fun refreshGames() {
        canListGames = runtime.canListShortcuts(this)
        games = if (canListGames) runtime.shortcuts(this) else emptyList()
    }

    /** Reads the drawer list off the main thread; labels and icons are expensive. */
    private fun loadApps() {
        if (loading || appsLoaded) return
        loading = true
        loadingApps = true
        Thread {
            val loaded = InstalledApps.load(this)
            val access = Recents.hasUsageAccess(this)
            apps = loaded
            usageAccess = access
            recents = Recents.pick(this, loaded, access)
            loadingApps = false
            appsLoaded = true
            loading = false
        }.start()
    }

    private fun refreshRecents() {
        val loaded = apps
        if (loaded.isEmpty()) return
        Thread {
            val access = Recents.hasUsageAccess(this)
            recents = Recents.pick(this, loaded, access)
            usageAccess = access
        }.start()
    }

    private fun launchGame(shortcut: RunnerShortcut) {
        status = when (val outcome = runtime.launchShortcut(this, shortcut)) {
            is LaunchOutcome.Launched -> ""
            is LaunchOutcome.RuntimeNotInstalled ->
                "Butterscotch is not installed\n${outcome.installUrl}"
            is LaunchOutcome.Blocked -> outcome.reason
        }
    }

    private fun launchApp(app: LauncherApp) {
        Recents.record(this, app.packageName)
        status = if (InstalledApps.launch(this, app)) {
            drawerOpen = false
            ""
        } else {
            "Could not open ${app.label}."
        }
    }

    private fun openAppInfo(app: LauncherApp) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", app.packageName, null),
        )
        runCatching { startActivity(intent) }
    }

    private fun openRunner() {
        status = when (val outcome = runtime.launch(this, runnerOnlyGame())) {
            is LaunchOutcome.Launched -> "Opened Butterscotch"
            is LaunchOutcome.RuntimeNotInstalled ->
                "Butterscotch is not installed\n${outcome.installUrl}"
            is LaunchOutcome.Blocked -> outcome.reason
        }
    }

    private fun openHomeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { status = "Open Settings and set IndieHost as your home app." }
    }

    private fun openUsageSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .onFailure { status = "Open Settings, Apps, Special access, Usage access." }
    }
}

/** [GameMakerRuntimeAdapter.launch] only needs the engine, never the game files. */
private fun runnerOnlyGame(): Game =
    Game(id = "open-runner", title = "Butterscotch", folderPath = "", engine = Engine.GAMEMAKER_VM)
