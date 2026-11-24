package com.indiehost.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.indiehost.detect.EngineDetector
import com.indiehost.runtimes.RunnerShortcut
import java.io.File

/**
 * The home screen: recents, the pinned games, and a way into the app drawer.
 *
 * There are no widgets and no wallpaper of our own, by design - this is the
 * thing you press Home to reach, so it stays fast and quiet.
 */
@Composable
internal fun HomeScreen(
    games: List<RunnerShortcut>,
    canListGames: Boolean,
    recents: List<LauncherApp>,
    loadingApps: Boolean,
    usageAccess: Boolean,
    status: String,
    onOpenDrawer: () -> Unit,
    onLaunchGame: (RunnerShortcut) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onOpenRunner: () -> Unit,
    onChooseHomeApp: () -> Unit,
    onAllowUsage: () -> Unit,
) {
    val context = LocalContext.current

    // Milestone 0's bytecode check stays: it is the fastest way to know whether
    // a data.win will run before importing it into Butterscotch.
    var report by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val importDir = File(context.cacheDir, "import").apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(importDir, "data.win")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val detection = EngineDetector().detect(importDir)
        report = buildString {
            appendLine("Engine: ${detection.engine.displayName}")
            appendLine("Runnable: ${if (detection.runnable) "yes" else "no"}")
            detection.wadVersion?.let { appendLine("WAD / bytecode version: $it") }
            detection.blocker?.let { appendLine("Blocker: $it") }
            detection.notes.forEach { appendLine("- $it") }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "IndieHost",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenDrawer) { Text("All apps") }
            }
        }

        item { Text("Recent", style = MaterialTheme.typography.titleMedium) }
        item {
            when {
                recents.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recents, key = { it.packageName }) { app ->
                        AppTile(
                            app = app,
                            modifier = Modifier.width(72.dp),
                            onClick = { onLaunchApp(app) },
                        )
                    }
                }

                loadingApps -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Reading your apps...", style = MaterialTheme.typography.bodySmall)
                }

                !usageAccess -> Column {
                    Text(
                        "Apps you open from here will show up in this row. " +
                            "Allow usage access to also see everything you opened outside IndieHost.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onAllowUsage) { Text("Allow usage access") }
                }

                else -> Text("Nothing opened recently.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
        item { Text("Your games", style = MaterialTheme.typography.titleMedium) }

        if (!canListGames) {
            item {
                Text(
                    "Set IndieHost as your home app to list the games you have in Butterscotch.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onChooseHomeApp, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose home app")
                }
            }
        } else if (games.isEmpty()) {
            item {
                Text(
                    "No games pinned yet. In Butterscotch, open a game's settings and tap " +
                        "\"Add Shortcut to Home Screen\". It will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(games, key = { it.id }) { shortcut ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchGame(shortcut) },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            shortcut.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Tap to play", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (status.isNotEmpty()) {
            item { Text(status, style = MaterialTheme.typography.bodyMedium) }
        }

        item { Spacer(Modifier.height(4.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenRunner, modifier = Modifier.weight(1f)) {
                    Text("Butterscotch")
                }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Check data.win")
                }
            }
        }
        if (report.isNotEmpty()) {
            item { Text(report, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** The app drawer: every launchable app, searchable, one tap to open. */
@Composable
internal fun AppDrawer(
    apps: List<LauncherApp>,
    loading: Boolean,
    onClose: () -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) apps
        else apps.filter { it.label.lowercase().contains(needle) || it.packageName.contains(needle) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "All apps",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("Home") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (apps.isEmpty()) "No apps found." else "No app matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gridItems(visible, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onLaunch(app) },
                        onLongClick = { onAppInfo(app) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: LauncherApp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = app.label,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
