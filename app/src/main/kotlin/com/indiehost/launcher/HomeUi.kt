package com.indiehost.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.indiehost.detect.Engine
import com.indiehost.detect.EngineDetector
import com.indiehost.library.Game
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
    library: List<Game>,
    covers: Map<String, ImageBitmap>,
    scanning: Boolean,
    scanMessage: String,
    onAddFolder: (Uri) -> Unit,
    onLaunchItem: (LibraryItem) -> Unit,
    onLinkPin: (Game, String?) -> Unit,
    onPickCover: (Game, Uri) -> Unit,
    onRemoveCover: (Game) -> Unit,
    onRemoveGame: (Game) -> Unit,
    onOpenDrawer: () -> Unit,
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

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onAddFolder(uri)
    }

    val libraryItems = remember(library, games) {
        mergeLibrary(library, games.map { LibraryPin(it.id, it.title) })
    }
    val pinsById = remember(games) { games.associateBy { it.id } }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    val selected = selectedGameId?.let { id -> libraryItems.firstOrNull { it.game?.id == id } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Indie.Base),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ClientBar(onOpenDrawer) }

        item {
            HeroPanel(
                item = libraryItems.firstOrNull { it.launchable } ?: libraryItems.firstOrNull(),
                canListGames = canListGames,
                onLaunch = onLaunchItem,
                onChooseHomeApp = onChooseHomeApp,
            )
        }

        item {
            SectionLabel(stringResource(R.string.section_recent), Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
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
                    CircularProgressIndicator(Modifier.size(16.dp), color = Indie.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.loading_apps), style = MaterialTheme.typography.bodySmall)
                }

                !usageAccess -> Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        stringResource(R.string.usage_access_explanation),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onAllowUsage) {
                        Text(stringResource(R.string.allow_usage), color = Indie.Accent, style = labelStyle())
                    }
                }

                else -> Text(
                    stringResource(R.string.no_recent),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item {
            SectionLabel(stringResource(R.string.section_library), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }

        when {
            libraryItems.isEmpty() && !canListGames -> item {
                Panel(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        stringResource(R.string.not_home),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    AccentButton(stringResource(R.string.choose_home), onChooseHomeApp, Modifier.fillMaxWidth())
                }
            }

            libraryItems.isEmpty() -> item {
                StateCard(
                    title = stringResource(R.string.library_empty),
                    detail = stringResource(R.string.no_games),
                    actionLabel = stringResource(R.string.add_folder),
                    onAction = { folderPicker.launch(null) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            else -> items(libraryItems, key = { it.key }) { item ->
                val pin = item.shortcutId?.let { pinsById[it] }
                GameRow(
                    item = item,
                    icon = pin?.icon,
                    cover = item.game?.id?.let { covers[it] },
                    subtitle = librarySubtitle(item),
                    onClick = {
                        if (item.game != null) {
                            selectedGameId = item.game.id
                        } else {
                            onLaunchItem(item)
                        }
                    },
                    onPlay = { onLaunchItem(item) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        if (status.isNotEmpty()) {
            item {
                StateCard(
                    title = status,
                    urgency = FeedbackPolicy.urgency(status),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item {
            SectionLabel(stringResource(R.string.section_tools), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlineButton(stringResource(R.string.runner_label), onOpenRunner, Modifier.weight(1f))
                OutlineButton(
                    if (checking) stringResource(R.string.checking) else stringResource(R.string.check_datawin),
                    { picker.launch(arrayOf("*/*")) },
                    Modifier.weight(1f),
                    enabled = !checking,
                )
            }
        }
        item {
            OutlineButton(
                if (scanning) stringResource(R.string.scanning_folders) else stringResource(R.string.add_folder),
                { folderPicker.launch(null) },
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                enabled = !scanning,
            )
        }
        if (scanMessage.isNotEmpty()) {
            item {
                StateCard(
                    title = scanMessage,
                    urgency = FeedbackPolicy.urgency(scanMessage),
                    actionLabel = stringResource(R.string.retry),
                    onAction = { folderPicker.launch(null) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        if (checking || report.isNotEmpty()) {
            item {
                StateCard(
                    title = if (checking) stringResource(R.string.reading_datawin) else stringResource(R.string.check_datawin),
                    detail = if (checking) null else report,
                    progress = checking,
                    urgency = FeedbackPolicy.urgency(report),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    if (selected != null) {
        val game = selected.game
        val availablePins = if (game != null) {
            games.filter { pin ->
                library.none { it.linkedShortcutId == pin.id && it.id != game.id }
            }
        } else {
            emptyList()
        }
        GameDetailDialog(
            item = selected,
            cover = game?.id?.let { covers[it] },
            linkedPinTitle = selected.shortcutId?.let { pinsById[it]?.title },
            availablePins = availablePins,
            onDismiss = { selectedGameId = null },
            onLaunch = {
                onLaunchItem(selected)
                selectedGameId = null
            },
            onLink = { pinId -> game?.let { onLinkPin(it, pinId) } },
            onPickCover = { uri -> game?.let { onPickCover(it, uri) } },
            onRemoveCover = { game?.let(onRemoveCover) },
            onRemove = {
                game?.let(onRemoveGame)
                selectedGameId = null
            },
        )
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
            ?: return context.getString(R.string.check_open_failed)
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink) }
        }
        val detection = EngineDetector().detect(importDir)
        buildString {
            appendLine(context.getString(R.string.check_engine, detection.engine.displayName))
            val runnable = context.getString(if (detection.runnable) R.string.yes else R.string.no)
            appendLine(context.getString(R.string.check_runnable, runnable))
            detection.wadVersion?.let { appendLine(context.getString(R.string.check_wad, it)) }
            detection.blocker?.let { appendLine(context.getString(R.string.check_blocker, it)) }
            detection.notes.forEach { appendLine(context.getString(R.string.check_note, it)) }
        }.trimEnd()
    } catch (_: Exception) {
        context.getString(R.string.check_failed)
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
            .background(Indie.Base)
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.brand),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Indie.TextBright,
            )
            Text(stringResource(R.string.home), style = labelStyle(10.sp), color = Indie.TextMuted)
        }
        TextButton(onClick = onOpenDrawer) {
            Text(stringResource(R.string.all_apps), style = labelStyle(), color = Indie.Text)
        }
    }
}

/**
 * The brand mark: a cyan ring around a play triangle with two rising curls
 * above it. Same shape as the launcher icon, drawn small enough to sit in a
 * text row.
 */
@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.11f
        drawCircle(
            color = Indie.Accent,
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
        drawPath(play, color = Indie.TextBright)
        // Two soft curls rising off the top of the mark.
        drawCircle(
            color = Indie.Accent.copy(alpha = 0.55f),
            radius = w * 0.085f,
            center = Offset(w * 0.74f, w * 0.17f),
        )
        drawCircle(
            color = Indie.Accent.copy(alpha = 0.3f),
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
    item: LibraryItem?,
    canListGames: Boolean,
    onLaunch: (LibraryItem) -> Unit,
    onChooseHomeApp: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(168.dp)
            .clip(PanelShape)
            .background(Indie.HeroGradient)
            .border(1.dp, Indie.Edge, PanelShape),
    ) {
        // Oversized ghost initial, the way a store page bleeds its logo.
        Text(
            text = (item?.title ?: "IH").take(1).uppercase(),
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
                    if (item != null) stringResource(R.string.play_next) else stringResource(R.string.get_started),
                    style = labelStyle(10.sp),
                    color = Indie.Accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        item != null -> item.title
                        canListGames -> stringResource(R.string.library_empty)
                        else -> stringResource(R.string.make_home)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        item != null -> librarySubtitle(item)
                        canListGames -> stringResource(R.string.hint_pin)
                        else -> stringResource(R.string.hint_home_access)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item != null && item.launchable) {
                AccentButton(
                    stringResource(R.string.play),
                    { onLaunch(item) },
                    Modifier.width(112.dp),
                )
            } else if (!canListGames) {
                AccentButton(
                    stringResource(R.string.choose_home),
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
        Box(Modifier.height(30.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                app.label,
                fontSize = 11.sp,
                color = Indie.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A library entry: art block, title, status and either Play or the reason it cannot. */
@Composable
private fun GameRow(
    item: LibraryItem,
    icon: Drawable?,
    cover: ImageBitmap?,
    subtitle: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shortcut icons are small; convert once per icon, not on every recomposition.
    val image = remember(icon) { icon?.toImageBitmap(128) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Indie.Card)
            .border(1.dp, Indie.Edge, PanelShape)
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
            when {
                cover != null -> Image(
                    bitmap = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                image != null -> Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                else -> Text(
                    item.title.take(1).uppercase(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Indie.TextBright.copy(alpha = 0.85f),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Indie.TextBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Fixed slot: a status badge appearing must not reflow the row.
        Box(Modifier.width(84.dp), contentAlignment = Alignment.CenterEnd) {
            if (item.launchable) {
                AccentButton(stringResource(R.string.play), onPlay, Modifier.width(80.dp))
            } else {
                Text(
                    statusLabel(item.reason),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Indie.Accent,
                )
            }
        }
    }
}

@Composable
private fun librarySubtitle(item: LibraryItem): String = when (item.reason) {
    null -> item.game?.engine?.displayName ?: stringResource(R.string.game_installed)
    LibraryReason.NEEDS_PIN -> stringResource(R.string.pin_needed)
    LibraryReason.WEBVIEW_M2 -> stringResource(R.string.runtime_m2)
    LibraryReason.RUNTIME_M3 -> stringResource(R.string.runtime_m3)
    LibraryReason.RENPY_M3 -> stringResource(R.string.renpy_not_hosted)
    LibraryReason.LOVE_M3 -> stringResource(R.string.love_not_hosted)
    LibraryReason.SCUMMVM_M3 -> stringResource(R.string.scummvm_not_hosted)
    LibraryReason.DETECTOR_BLOCKED ->
        item.game?.blocker?.let { stringResource(R.string.detector_blocked, it) }
            ?: stringResource(R.string.runtime_m3)
    LibraryReason.MISSING -> stringResource(R.string.game_missing)
    LibraryReason.DUPLICATE -> stringResource(R.string.game_duplicate)
}

@Composable
private fun statusLabel(reason: LibraryReason?): String = when (reason) {
    LibraryReason.NEEDS_PIN -> stringResource(R.string.pin_needed_short)
    LibraryReason.WEBVIEW_M2 -> stringResource(R.string.runtime_m2_short)
    LibraryReason.RUNTIME_M3 -> stringResource(R.string.runtime_m3_short)
    LibraryReason.RENPY_M3, LibraryReason.LOVE_M3, LibraryReason.SCUMMVM_M3 ->
        stringResource(R.string.not_hosted_short)
    LibraryReason.DETECTOR_BLOCKED -> stringResource(R.string.blocked_short)
    LibraryReason.MISSING -> stringResource(R.string.missing_short)
    LibraryReason.DUPLICATE -> stringResource(R.string.duplicate_short)
    null -> ""
}

@Composable
private fun GameDetailDialog(
    item: LibraryItem,
    cover: ImageBitmap?,
    linkedPinTitle: String?,
    availablePins: List<RunnerShortcut>,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onLink: (String?) -> Unit,
    onPickCover: (Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onRemove: () -> Unit,
) {
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickCover(uri)
    }
    var confirmRemove by remember { mutableStateOf(false) }
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(item.key) {
        if (item.launchable) runCatching { primaryFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PanelShape)
                .background(Indie.Panel)
                .border(1.dp, Indie.Edge, PanelShape)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(10.dp))
            }

            Text(item.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))

            item.game?.let { game ->
                Text(
                    stringResource(R.string.game_engine, game.engine.displayName),
                    style = MaterialTheme.typography.bodySmall,
                )
                game.wadVersion?.let {
                    Text(
                        stringResource(R.string.game_wad, it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (game.folderPath.isNotBlank()) {
                    Text(
                        stringResource(R.string.game_location, game.folderPath),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        stringResource(
                            if (game.coverPath == null) R.string.choose_cover else R.string.replace_cover,
                        ),
                        { coverPicker.launch(arrayOf("image/*")) },
                        Modifier.weight(1f),
                    )
                    if (game.coverPath != null) {
                        OutlineButton(
                            stringResource(R.string.remove_cover),
                            onRemoveCover,
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            item.reason?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    librarySubtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = Indie.Accent,
                )
            }

            Spacer(Modifier.height(12.dp))
            if (item.launchable) {
                AccentButton(
                    stringResource(R.string.play),
                    onLaunch,
                    Modifier.fillMaxWidth().focusRequester(primaryFocus),
                )
                Spacer(Modifier.height(10.dp))
            }

            if (item.game?.engine == Engine.GAMEMAKER_VM) {
                Text(stringResource(R.string.link_pin_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                when {
                    linkedPinTitle != null -> {
                        Text(
                            stringResource(R.string.pin_linked, linkedPinTitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Indie.Accent,
                        )
                        TextButton({ onLink(null) }) {
                            Text(stringResource(R.string.unlink), color = Indie.Accent, style = labelStyle())
                        }
                    }

                    availablePins.isEmpty() -> Text(
                        stringResource(R.string.no_pins_to_link),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    else -> availablePins.forEach { pin ->
                        TextButton({ onLink(pin.id) }) {
                            Text(
                                pin.title,
                                color = Indie.Accent,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (confirmRemove) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.remove_confirm),
                    style = MaterialTheme.typography.bodySmall,
                    color = Indie.TextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        stringResource(R.string.confirm),
                        { confirmRemove = false; onRemove() },
                        Modifier.weight(1f),
                    )
                    OutlineButton(
                        stringResource(R.string.cancel),
                        { confirmRemove = false },
                        Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(
                    stringResource(R.string.remove),
                    { confirmRemove = true },
                    Modifier.weight(1f),
                )
                OutlineButton(stringResource(R.string.close), onDismiss, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Indie.Accent,
            contentColor = Indie.OnAccent,
            disabledContainerColor = Indie.disabled(Indie.Accent),
            disabledContentColor = Indie.OnAccent,
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
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Indie.Text,
            disabledContentColor = Indie.disabled(),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Indie.Edge),
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
            .background(Indie.Card)
            .border(1.dp, Indie.Edge, PanelShape)
            .padding(14.dp),
        content = content,
    )
}

/**
 * One shape for every "nothing here / still working / something failed" state:
 * a short title, an optional reason, and one next action. Reused by the drawer,
 * the library, the scan/check tools and the WebView failure path.
 */
@Composable
private fun StateCard(
    title: String,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    progress: Boolean = false,
    urgency: FeedbackUrgency = FeedbackUrgency.INFO,
    modifier: Modifier = Modifier,
) {
    // The only animation in the launcher; disabled by system motion settings.
    val motion = rememberMotionEnabled()
    val cardModifier = if (motion) modifier.animateContentSize() else modifier
    Panel(cardModifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (progress) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = Indie.Accent,
                    strokeWidth = 2.dp,
                )
            } else {
                BrandMark(Modifier.size(24.dp).clearAndSetSemantics {})
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val tone = when (urgency) {
                    FeedbackUrgency.QUIET -> Indie.TextMuted
                    FeedbackUrgency.INFO -> Indie.Accent
                    FeedbackUrgency.WARN -> Indie.Warning
                    FeedbackUrgency.BLOCKER -> MaterialTheme.colorScheme.error
                }
                Text(title, style = MaterialTheme.typography.bodyLarge, color = tone)
                if (detail != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = Indie.TextMuted)
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onAction) {
                        Text(actionLabel, style = labelStyle(), color = Indie.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = labelStyle(11.sp), color = Indie.TextMuted, modifier = modifier)
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
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    val visible = remember(apps, query) {
        apps.filter { AppSearch.matches(it.label, it.packageName, query) }
            .sortedBy { AppSearch.rank(it.label, it.packageName, query) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Indie.Base),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Indie.Panel)
                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.all_apps),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = Indie.TextBright,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.home), style = labelStyle(), color = Indie.Accent)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_apps), color = Indie.TextMuted) },
            singleLine = true,
            shape = PillShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Indie.Accent,
                unfocusedBorderColor = Indie.Edge,
                focusedTextColor = Indie.TextBright,
                unfocusedTextColor = Indie.Text,
                cursorColor = Indie.Accent,
                focusedContainerColor = Indie.Panel,
                unfocusedContainerColor = Indie.Panel,
            ),
            trailingIcon = if (query.isEmpty()) null else {
                {
                    TextButton(onClick = { query = "" }) {
                        Text(stringResource(R.string.clear_search), style = labelStyle(), color = Indie.Accent)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .focusRequester(searchFocus),
        )

        when {
            loading -> StateCard(
                title = stringResource(R.string.loading_apps),
                progress = true,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            visible.isEmpty() -> StateCard(
                title = if (apps.isEmpty()) stringResource(R.string.no_apps)
                        else stringResource(R.string.no_app_matches, query),
                detail = if (apps.isEmpty()) null else stringResource(R.string.search_hint),
                actionLabel = if (apps.isEmpty() || query.isEmpty()) null else stringResource(R.string.clear_search),
                onAction = { query = "" },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

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
            color = Indie.Text,
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
                .background(Indie.CardRaised, RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Indie.Accent,
            )
        }
    }
}
