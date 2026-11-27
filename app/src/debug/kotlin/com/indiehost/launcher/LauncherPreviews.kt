package com.indiehost.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.indiehost.detect.Engine
import com.indiehost.library.Game
import com.indiehost.library.GameSource

/**
 * Device-less previews for the states that are easiest to regress. Open them in
 * Android Studio's Compose preview pane; nothing here is shipped in the APK.
 */

@Preview(name = "Home - empty library", showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    IndieTheme {
        HomeScreen(
            games = emptyList(),
            canListGames = true,
            recents = emptyList(),
            loadingApps = false,
            usageAccess = false,
            status = "",
            library = emptyList(),
            covers = emptyMap(),
            scanning = false,
            scanMessage = "",
            onAddFolder = {},
            onLaunchItem = {},
            onLinkPin = { _, _ -> },
            onPickCover = { _, _ -> },
            onRemoveCover = {},
            onRemoveGame = {},
            onOpenDrawer = {},
            onLaunchApp = {},
            onOpenRunner = {},
            onChooseHomeApp = {},
            onAllowUsage = {},
        )
    }
}

@Preview(name = "Home - scanning with blocked game", showBackground = true)
@Composable
private fun HomeScanningPreview() {
    val blocked = Game(
        id = "scan-blocked",
        title = "YYC Build",
        folderPath = "content://doc/yyc",
        engine = Engine.GAMEMAKER_VM,
        source = GameSource.SCANNED,
        treeUri = "content://tree",
        runnable = false,
        blocker = "No CODE chunk",
    )
    IndieTheme {
        HomeScreen(
            games = emptyList(),
            canListGames = true,
            recents = emptyList(),
            loadingApps = false,
            usageAccess = true,
            status = "",
            library = listOf(blocked),
            covers = emptyMap(),
            scanning = true,
            scanMessage = "Imported 3 games; 1 folder skipped.",
            onAddFolder = {},
            onLaunchItem = {},
            onLinkPin = { _, _ -> },
            onPickCover = { _, _ -> },
            onRemoveCover = {},
            onRemoveGame = {},
            onOpenDrawer = {},
            onLaunchApp = {},
            onOpenRunner = {},
            onChooseHomeApp = {},
            onAllowUsage = {},
        )
    }
}

@Preview(name = "Drawer - no matches", showBackground = true)
@Composable
private fun DrawerNoMatchesPreview() {
    IndieTheme {
        AppDrawer(
            apps = listOf(
                LauncherApp("com.example.signal", ".Main", "Signal", icon = null),
                LauncherApp("com.example.cafe", ".Main", "Café", icon = null),
            ),
            loading = false,
            onClose = {},
            onLaunch = {},
            onAppInfo = {},
            initialQuery = "zzzz",
        )
    }
}

@Preview(name = "Drawer - loading", showBackground = true)
@Composable
private fun DrawerLoadingPreview() {
    IndieTheme {
        AppDrawer(
            apps = emptyList(),
            loading = true,
            onClose = {},
            onLaunch = {},
            onAppInfo = {},
        )
    }
}
