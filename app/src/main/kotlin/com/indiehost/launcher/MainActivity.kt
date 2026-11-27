package com.indiehost.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.documentfile.provider.DocumentFile
import com.indiehost.detect.Engine
import com.indiehost.library.Game
import com.indiehost.library.GameRepository
import com.indiehost.library.RoomGameRepository
import com.indiehost.runtimes.GameMakerRuntimeAdapter
import com.indiehost.runtimes.LaunchOutcome
import com.indiehost.runtimes.RpgMakerRuntimeAdapter
import com.indiehost.runtimes.RunnerShortcut
import com.indiehost.runtimes.RuntimeRegistry

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
    private val registry = RuntimeRegistry(
        listOf(
            runtime,
            RpgMakerRuntimeAdapter(Engine.RPGMAKER_MV),
            RpgMakerRuntimeAdapter(Engine.RPGMAKER_MZ),
        ),
    )

    private lateinit var repository: GameRepository
    private lateinit var folderStore: GameFolderStore
    private lateinit var coverStore: CoverStore

    private var games by mutableStateOf<List<RunnerShortcut>>(emptyList())
    private var library by mutableStateOf<List<Game>>(emptyList())
    private var covers by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    private var canListGames by mutableStateOf(false)

    private var scanning by mutableStateOf(false)
    private var scanMessage by mutableStateOf("")

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
        repository = RoomGameRepository(applicationContext)
        folderStore = GameFolderStore(this)
        coverStore = CoverStore(this)
        loadLibrary()
        refreshGames()
        loadApps()

        setContent {
            SteamTheme {
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
                            library = library,
                            covers = covers,
                            scanning = scanning,
                            scanMessage = scanMessage,
                            onAddFolder = ::addFolder,
                            onLaunchItem = ::launchItem,
                            onLinkPin = ::linkPin,
                            onPickCover = ::pickCover,
                            onRemoveCover = ::removeCover,
                            onRemoveGame = ::removeGame,
                            onOpenDrawer = { drawerOpen = true },
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
        loadLibrary()
        checkFolderPermissions()
        if (appsLoaded) refreshRecents() else loadApps()
    }

    private fun loadLibrary() {
        Thread({
            reloadLibrary(emptyList())
        }, "library-load").start()
    }

    /**
     * Re-reads the library and its decoded covers. Blocks: call from a worker
     * thread. [fallback] keeps the last good rows when the database read fails.
     */
    private fun reloadLibrary(fallback: List<Game> = library) {
        val loaded = runCatching { repository.all() }.getOrElse { fallback }
        val images = loaded.mapNotNull { game ->
            game.coverPath?.let { path ->
                coverStore.load(path)?.let { bitmap -> game.id to bitmap }
            }
        }.toMap()
        library = loaded
        covers = images
    }

    private fun checkFolderPermissions() {
        if (folderStore.all().any { !folderStore.hasPermission(it) }) {
            scanMessage = getString(R.string.folder_permission_lost)
        }
    }

    private fun addFolder(uri: Uri) {
        if (scanning) return
        if (!folderStore.add(uri)) {
            scanMessage = getString(R.string.folder_not_remembered)
            return
        }
        scanFolders()
    }

    /**
     * Scans every remembered tree. Rows the scan does not see are left alone;
     * only an explicit Remove deletes a game.
     */
    private fun scanFolders() {
        if (scanning) return
        scanning = true
        scanMessage = getString(R.string.scanning_folders)

        Thread({
            val trees = folderStore.all()
            val existing = runCatching { repository.all() }.getOrElse { emptyList() }
            val imported = mutableListOf<Game>()
            val skipped = mutableListOf<String>()
            var failure: String? = null

            for (tree in trees) {
                if (!folderStore.hasPermission(tree)) {
                    failure = getString(R.string.folder_permission_lost)
                    continue
                }
                val document = DocumentFile.fromTreeUri(this, tree)
                if (document == null) {
                    skipped += tree.toString()
                    continue
                }
                val result = GameScanner().scan(
                    SafGameFolder(this, tree, document),
                    tree.toString(),
                    existing + imported,
                )
                imported += result.games
                skipped += result.skipped
                result.error?.let { skipped += it }
            }

            runCatching { imported.forEach { repository.add(it) } }
                .onFailure { failure = getString(R.string.scan_failed) }

            reloadLibrary(existing + imported)
            scanning = false
            scanMessage = when {
                failure != null -> failure!!
                skipped.isEmpty() -> getString(R.string.scan_result, imported.size)
                else -> getString(R.string.scan_result_skipped, imported.size, skipped.size)
            }
        }, "folder-scan").start()
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

    private fun launchItem(item: LibraryItem) {
        val game = item.game
        if (game == null) {
            item.shortcutId?.let { id -> games.firstOrNull { it.id == id }?.let(::launchGame) }
            return
        }
        val blocker = game.blocker
        if (blocker != null) {
            status = blocker
            return
        }

        val adapter = registry.forEngine(game.engine)
        if (adapter == null) {
            status = getString(R.string.not_hosted_short)
            return
        }

        val pin = game.linkedShortcutId?.let { id -> games.firstOrNull { it.id == id } }
        status = when (val outcome = adapter.launch(this, game, pin)) {
            is LaunchOutcome.Launched -> ""
            is LaunchOutcome.RuntimeNotInstalled ->
                getString(R.string.runner_not_installed, outcome.installUrl)
            is LaunchOutcome.Blocked -> outcome.reason
        }
    }

    private fun linkPin(game: Game, shortcutId: String?) {
        Thread({
            runCatching { repository.add(game.copy(linkedShortcutId = shortcutId)) }
            reloadLibrary()
        }, "library-link").start()
    }

    private fun pickCover(game: Game, uri: Uri) {
        Thread({
            val name = coverStore.save(game.id, uri)
            if (name == null) {
                status = getString(R.string.cover_failed)
                return@Thread
            }
            runCatching { repository.add(game.copy(coverPath = name)) }
                .onSuccess { status = getString(R.string.cover_saved) }
                .onFailure { status = getString(R.string.cover_failed) }
            reloadLibrary()
        }, "cover-save").start()
    }

    private fun removeCover(game: Game) {
        val path = game.coverPath ?: return
        Thread({
            coverStore.delete(path)
            runCatching { repository.add(game.copy(coverPath = null)) }
            reloadLibrary()
        }, "cover-remove").start()
    }

    private fun removeGame(game: Game) {
        Thread({
            game.coverPath?.let(coverStore::delete)
            runCatching { repository.remove(game.id) }
            reloadLibrary()
        }, "library-remove").start()
        status = getString(R.string.removed_game)
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
        openRunner(getString(R.string.opened_runner))
    }

    private fun openRunner(message: String) {
        status = when (val outcome = runtime.launch(this, runnerOnlyGame(getString(R.string.runner_name)))) {
            is LaunchOutcome.Launched -> message
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
