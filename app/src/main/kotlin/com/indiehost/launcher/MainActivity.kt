package com.indiehost.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.indiehost.detect.Engine
import com.indiehost.library.Game
import com.indiehost.library.IndieHostDatabase
import com.indiehost.library.RoomGameRepository
import com.indiehost.runtimes.GameMakerRuntimeAdapter
import com.indiehost.runtimes.LaunchOutcome
import com.indiehost.runtimes.RunnerShortcut

/**
 * The home app.
 *
 * Two screens: [HomeScreen] with recents and the pinned games, and [AppDrawer]
 * with everything installed. Both are built from system data, [InstalledApps]
 * and Butterscotch's pinned shortcuts, so the launcher holds no state of its
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

    private var scannedGames by mutableStateOf<List<Game>>(emptyList())
    private var scanSkipped by mutableStateOf<List<ScanSkip>>(emptyList())
    private var scanning by mutableStateOf(false)
    private var covers by mutableStateOf<Map<String, Bitmap>>(emptyMap())
    private var selectedGame by mutableStateOf<Game?>(null)
    private var coverTarget by mutableStateOf<Game?>(null)

    @Volatile
    private var repository: RoomGameRepository? = null

    /** Registered up front, as the activity-result API requires. */
    private val pickGamesFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) scanLibrary(uri)
        }

    /** The cover is copied immediately, so no persisted grant is needed. */
    private val pickCover =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val game = coverTarget
            coverTarget = null
            if (uri == null || game == null) return@registerForActivityResult
            Thread {
                val name = runCatching { CoverImages.import(this, uri, game.id) }.getOrNull()
                if (name == null) {
                    status = getString(R.string.detail_cover_failed)
                    return@Thread
                }
                val updated = game.copy(coverPath = name)
                runCatching { repo().add(updated) }
                selectedGame = updated
                loadLibrary()
                status = getString(R.string.detail_cover_saved)
            }.start()
        }

    @Volatile
    private var appsLoaded = false

    @Volatile
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshGames()
        loadLibrary()
        loadApps()

        setContent {
            SteamTheme {
                Surface(Modifier.fillMaxSize()) {
                    // Back closes the drawer; on the home screen there is nowhere
                    // to go, which is what a home screen should do.
                    BackHandler(enabled = drawerOpen || selectedGame != null) {
                        if (drawerOpen) drawerOpen = false else selectedGame = null
                    }

                    val detail = selectedGame
                    when {
                        drawerOpen -> AppDrawer(
                            apps = apps,
                            loading = loadingApps,
                            onClose = { drawerOpen = false },
                            onLaunch = ::launchApp,
                            onAppInfo = ::openAppInfo,
                        )

                        detail != null -> GameDetailScreen(
                            game = detail,
                            cover = covers[detail.id],
                            shortcuts = games,
                            onBack = { selectedGame = null },
                            onLaunch = { launchScanned(detail) },
                            onLink = { shortcut -> linkShortcut(detail, shortcut) },
                            onUnlink = { unlinkShortcut(detail) },
                            onChangeCover = { openCoverPicker(detail) },
                            onRemoveCover = { removeCover(detail) },
                            onRemove = { removeGame(detail) },
                        )

                        else -> HomeScreen(
                            games = games,
                            canListGames = canListGames,
                            recents = recents,
                            loadingApps = loadingApps,
                            usageAccess = usageAccess,
                            status = status,
                            scanned = scannedGames,
                            scanSkipped = scanSkipped,
                            scanning = scanning,
                            covers = covers,
                            onScanFolder = { pickGamesFolder.launch(null) },
                            onOpenGame = { selectedGame = it },
                            onLaunchScanned = ::launchScanned,
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
        checkScanAccess()
        loadLibrary()
        if (appsLoaded) refreshRecents() else loadApps()
    }

    private fun refreshGames() {
        canListGames = runtime.canListShortcuts(this)
        games = if (canListGames) runtime.shortcuts(this) else emptyList()
    }

    /**
     * Reads scanned games from Room off the main thread. A failure leaves the
     * previous list alone: losing a library row is worse than showing a stale
     * one, and the status panel explains what happened.
     */
    private fun loadLibrary() {
        Thread {
            val loaded = runCatching { repo().all() }.getOrNull() ?: return@Thread
            scannedGames = loaded
            covers = loaded.mapNotNull { game ->
                val name = game.coverPath ?: return@mapNotNull null
                CoverImages.load(this, name)?.let { game.id to it }
            }.toMap()
            // Keep an open panel in step; a removed game closes it.
            selectedGame = selectedGame?.let { selected -> loaded.firstOrNull { it.id == selected.id } }
        }.start()
    }

    /** The scanner is the only writer; every call takes the persisted read grant. */
    private fun scanLibrary(treeUri: Uri) {
        if (scanning) return
        scanning = true
        status = ""
        scanSkipped = emptyList()

        val keptGrant = try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        } catch (e: Exception) {
            false
        }
        if (!keptGrant) {
            scanning = false
            status = getString(R.string.scan_no_permission)
            return
        }
        LibraryStore.setTreeUri(this, treeUri)

        Thread {
            val result = runCatching { GameScanner.scan(this, treeUri) }.getOrNull()
            if (result == null) {
                status = getString(R.string.scan_failed)
            } else {
                runCatching { repo().upsertAll(result.games) }
                scannedGames = runCatching { repo().all() }.getOrDefault(scannedGames)
                // A short list, so one awkward folder cannot fill the screen.
                scanSkipped = result.skipped.take(5)
                status = when {
                    result.games.isEmpty() -> getString(R.string.scan_none)
                    result.skipped.isEmpty() -> getString(R.string.scan_imported, result.games.size)
                    else -> getString(R.string.scan_imported_skipped, result.games.size, result.skipped.size)
                }
            }
            scanning = false
        }.start()
    }

    /**
     * A persisted grant can be revoked from Settings. Say so, keep the metadata:
     * the user re-picks the folder and the scan rebuilds it.
     */
    private fun checkScanAccess() {
        val tree = LibraryStore.treeUri(this) ?: return
        val granted = contentResolver.persistedUriPermissions
            .any { it.uri == tree && it.isReadPermission }
        if (!granted) status = getString(R.string.scan_permission_lost)
    }

    private fun repo(): RoomGameRepository {
        repository?.let { return it }
        synchronized(this) {
            repository?.let { return it }
            val created = RoomGameRepository(IndieHostDatabase.create(this).gameDao())
            repository = created
            return created
        }
    }

    /** Launch goes through the linked pin, never through a title match. */
    private fun launchScanned(game: Game) {
        val id = game.linkedShortcutId
        if (id == null) {
            selectedGame = game
            return
        }
        val shortcut = games.firstOrNull { it.id == id }
        if (shortcut == null) {
            status = getString(R.string.detail_link_missing)
            return
        }
        launchGame(shortcut)
    }

    private fun linkShortcut(game: Game, shortcut: RunnerShortcut) {
        val updated = game.copy(linkedShortcutId = shortcut.id)
        Thread {
            runCatching { repo().add(updated) }
            selectedGame = updated
            status = getString(R.string.detail_linked_status, shortcut.title)
            loadLibrary()
        }.start()
    }

    private fun unlinkShortcut(game: Game) {
        val updated = game.copy(linkedShortcutId = null)
        Thread {
            runCatching { repo().add(updated) }
            selectedGame = updated
            status = getString(R.string.detail_unlinked_status)
            loadLibrary()
        }.start()
    }

    private fun openCoverPicker(game: Game) {
        coverTarget = game
        pickCover.launch(arrayOf("image/*"))
    }

    private fun removeCover(game: Game) {
        val name = game.coverPath ?: return
        val updated = game.copy(coverPath = null)
        Thread {
            runCatching { repo().add(updated) }
            CoverImages.delete(this, name)
            selectedGame = updated
            status = getString(R.string.detail_cover_removed)
            loadLibrary()
        }.start()
    }

    private fun removeGame(game: Game) {
        Thread {
            runCatching { repo().remove(game.id) }
            game.coverPath?.let { CoverImages.delete(this, it) }
            selectedGame = null
            status = getString(R.string.detail_removed, game.title)
            loadLibrary()
        }.start()
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
                getString(R.string.runner_not_installed, outcome.installUrl)
            is LaunchOutcome.Blocked -> outcome.reason
        }
    }

    private fun launchApp(app: LauncherApp) {
        Recents.record(this, app.packageName)
        status = if (InstalledApps.launch(this, app)) {
            drawerOpen = false
            ""
        } else {
            getString(R.string.could_not_open, app.label)
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
        status = when (val outcome = runtime.launch(this, runnerOnlyGame(getString(R.string.runner_name)))) {
            is LaunchOutcome.Launched -> getString(R.string.opened_runner)
            is LaunchOutcome.RuntimeNotInstalled ->
                getString(R.string.runner_not_installed, outcome.installUrl)
            is LaunchOutcome.Blocked -> outcome.reason
        }
    }

    private fun openHomeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { status = getString(R.string.home_settings_hint) }
    }

    private fun openUsageSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .onFailure { status = getString(R.string.usage_settings_hint) }
    }
}

/** [GameMakerRuntimeAdapter.launch] only needs the engine, never the game files. */
private fun runnerOnlyGame(title: String): Game =
    Game(id = "open-runner", title = title, folderPath = "", engine = Engine.GAMEMAKER_VM)
