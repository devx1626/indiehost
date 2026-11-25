package com.indiehost.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiehost.detect.EngineDetector
import com.indiehost.runtimes.RunnerShortcut
import java.io.File

private val PanelShape = RoundedCornerShape(6.dp)
private val PillShape = RoundedCornerShape(2.dp)

/**
 * The home screen, laid out like a game client: a chrome bar, a "continue
 * playing" panel, a recent row and the library.
 *
 * There are no widgets and no wallpaper of our own, by design: this is the
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
    // a data.win will run before importing it into Butterscotch. A data.win can
    // be hundreds of megabytes, so the copy and detection run on a worker thread.
    var report by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        checking = true
        report = ""
        Thread({
            val result = inspectDataWin(context, uri)
            report = result
            checking = false
        }, "data-win-check").start()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Steam.Base),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ClientBar(onOpenDrawer) }

        item {
            HeroPanel(
                games = games,
                canListGames = canListGames,
                onLaunchGame = onLaunchGame,
                onChooseHomeApp = onChooseHomeApp,
            )
        }

        item {
            SectionLabel("RECENT", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
        }
        item {
            when {
                recents.isNotEmpty() -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(recents, key = { it.packageName }) { app ->
                        RecentTile(app = app, onClick = { onLaunchApp(app) })
                    }
                }

                loadingApps -> Row(
                    Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Steam.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Reading your apps...", style = MaterialTheme.typography.bodySmall)
                }

                !usageAccess -> Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "Apps you open from here will show up in this row. Allow usage access " +
                            "to also see everything you opened outside IndieHost.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onAllowUsage) {
                        Text("ALLOW USAGE ACCESS", color = Steam.Accent, style = labelStyle())
                    }
                }

                else -> Text(
                    "Nothing opened recently.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item {
            SectionLabel("YOUR LIBRARY", Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }

        when {
            !canListGames -> item {
                Panel(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "Set IndieHost as your home app to list the games you have in Butterscotch.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    AccentButton("CHOOSE HOME APP", onChooseHomeApp, Modifier.fillMaxWidth())
                }
            }

            games.isEmpty() -> item {
                Panel(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "No games pinned yet. In Butterscotch, open a game's settings and tap " +
                            "\"Add Shortcut to Home Screen\". It will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> items(games, key = { it.id }) { shortcut ->
                GameRow(
                    title = shortcut.title,
                    icon = shortcut.icon,
                    onClick = { onLaunchGame(shortcut) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        if (status.isNotEmpty()) {
            item {
                Panel(Modifier.padding(horizontal = 20.dp)) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = Steam.Accent)
                }
            }
        }

        item {
            SectionLabel("TOOLS", Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlineButton("BUTTERSCOTCH", onOpenRunner, Modifier.weight(1f))
                OutlineButton(
                    if (checking) "CHECKING..." else "CHECK DATA.WIN",
                    { picker.launch(arrayOf("*/*")) },
                    Modifier.weight(1f),
                    enabled = !checking,
                )
            }
        }
        if (checking || report.isNotEmpty()) {
            item {
                Panel(Modifier.padding(horizontal = 20.dp)) {
                    if (checking) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                color = Steam.Accent,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Reading data.win...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * Copies the picked document into the cache and fingerprints it.
 *
 * Blocks: call from a worker thread. The copy is deleted afterwards, because a
 * data.win can be hundreds of megabytes and it has no use once inspected.
 */
private fun inspectDataWin(context: Context, uri: Uri): String {
    val importDir = File(context.cacheDir, "import")
    return try {
        importDir.deleteRecursively()
        importDir.mkdirs()
        val target = File(importDir, "data.win")
        val input = context.contentResolver.openInputStream(uri)
            ?: return "Could not open that file. Pick another copy."
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink) }
        }
        val detection = EngineDetector().detect(importDir)
        buildString {
            appendLine("Engine: ${detection.engine.displayName}")
            appendLine("Runnable: ${if (detection.runnable) "yes" else "no"}")
            detection.wadVersion?.let { appendLine("WAD / bytecode version: $it") }
            detection.blocker?.let { appendLine("Blocker: $it") }
            detection.notes.forEach { appendLine("- $it") }
        }.trimEnd()
    } catch (_: Exception) {
        "Could not check that file. Pick another copy."
    } finally {
        importDir.deleteRecursively()
    }
}

/** A thin client-style top bar with the wordmark and the way into the drawer. */
@Composable
private fun ClientBar(onOpenDrawer: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Steam.Base)
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "INDIEHOST",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Steam.TextBright,
            )
            Text("HOME", style = labelStyle(10.sp), color = Steam.TextMuted)
        }
        TextButton(onClick = onOpenDrawer) {
            Text("ALL APPS", style = labelStyle(), color = Steam.Text)
        }
    }
}

/**
 * The brand mark: a cyan ring around a play triangle with two steam curls
 * above it. Same shape as the launcher icon, drawn small enough to sit in a
 * text row.
 */
@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.11f
        drawCircle(
            color = Steam.Accent,
            radius = w * 0.38f,
            center = Offset(w * 0.5f, w * 0.55f),
            style = Stroke(width = stroke),
        )
        val play = Path().apply {
            moveTo(w * 0.43f, w * 0.37f)
            lineTo(w * 0.68f, w * 0.55f)
            lineTo(w * 0.43f, w * 0.73f)
            close()
        }
        drawPath(play, color = Steam.TextBright)
        // Two puffs of steam rising off the top of the mark.
        drawCircle(
            color = Steam.Accent.copy(alpha = 0.55f),
            radius = w * 0.085f,
            center = Offset(w * 0.74f, w * 0.17f),
        )
        drawCircle(
            color = Steam.Accent.copy(alpha = 0.3f),
            radius = w * 0.06f,
            center = Offset(w * 0.88f, w * 0.07f),
        )
    }
}

/**
 * The big panel at the top: the next thing to play, or the setup step that
 * unlocks the library. A store page puts its latest release here; we put the
 * game you actually pinned.
 */
@Composable
private fun HeroPanel(
    games: List<RunnerShortcut>,
    canListGames: Boolean,
    onLaunchGame: (RunnerShortcut) -> Unit,
    onChooseHomeApp: () -> Unit,
) {
    val game = games.firstOrNull()

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(168.dp)
            .clip(PanelShape)
            .background(Steam.HeroGradient)
            .border(1.dp, Steam.Edge, PanelShape),
    ) {
        // Oversized ghost initial, the way a store page bleeds its logo.
        Text(
            text = (game?.title ?: "IH").take(1).uppercase(),
            fontSize = 150.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    if (game != null) "PLAY NEXT" else "GET STARTED",
                    style = labelStyle(10.sp),
                    color = Steam.Accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        game != null -> game.title
                        canListGames -> "Your library is empty"
                        else -> "Make it your home screen"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        game != null -> "GameMaker - Butterscotch"
                        canListGames -> "Pin a game in Butterscotch and it appears here."
                        else -> "Home access is what lets IndieHost list your games."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (game != null) {
                AccentButton(
                    "PLAY",
                    { onLaunchGame(game) },
                    Modifier.width(112.dp),
                )
            } else if (!canListGames) {
                AccentButton(
                    "CHOOSE HOME APP",
                    onChooseHomeApp,
                    Modifier.width(190.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentTile(app: LauncherApp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .clip(PanelShape)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, size = 52.dp, corner = 4.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            fontSize = 11.sp,
            color = Steam.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A library entry: art block, title, runtime and the blue play pill. */
@Composable
private fun GameRow(
    title: String,
    icon: Drawable?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shortcut icons are small; convert once per icon, not on every recomposition.
    val image = remember(icon) { icon?.toImageBitmap(128) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Steam.Card)
            .border(1.dp, Steam.Edge, PanelShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3C6E8F), Color(0xFF1B2838)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    title.take(1).uppercase(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Steam.TextBright.copy(alpha = 0.85f),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Steam.TextBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("GameMaker - installed", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        AccentButton("PLAY", onClick, Modifier.width(80.dp))
    }
}

@Composable
private fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Steam.Accent,
            contentColor = Color(0xFF0B1219),
        ),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Steam.Text),
        border = androidx.compose.foundation.BorderStroke(1.dp, Steam.Edge),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(text, fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

/** A darker panel used for setup text and status lines. */
@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Steam.Card)
            .border(1.dp, Steam.Edge, PanelShape)
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = labelStyle(11.sp), color = Steam.TextMuted, modifier = modifier)
}

@Composable
private fun labelStyle(size: androidx.compose.ui.unit.TextUnit = 11.sp) =
    androidx.compose.ui.text.TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )

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

    Column(
        Modifier
            .fillMaxSize()
            .background(Steam.Base),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Steam.Panel)
                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "ALL APPS",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = Steam.TextBright,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text("HOME", style = labelStyle(), color = Steam.Accent)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search your library", color = Steam.TextMuted) },
            singleLine = true,
            shape = PillShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Steam.Accent,
                unfocusedBorderColor = Steam.Edge,
                focusedTextColor = Steam.TextBright,
                unfocusedTextColor = Steam.Text,
                cursorColor = Steam.Accent,
                focusedContainerColor = Steam.Panel,
                unfocusedContainerColor = Steam.Panel,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Steam.Accent)
            }

            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (apps.isEmpty()) "No apps found." else "No app matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 84.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
            .clip(PanelShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, size = 56.dp, corner = 6.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            fontSize = 11.sp,
            color = Steam.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The real launcher icon, or a lettered plate while it is missing. */
@Composable
private fun AppIcon(app: LauncherApp, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = app.label,
            modifier = Modifier.size(size),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .background(Steam.CardRaised, RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Steam.Accent,
            )
        }
    }
}
