package com.indiehost.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.indiehost.detect.Engine
import com.indiehost.detect.EngineDetector
import com.indiehost.library.Game
import com.indiehost.runtimes.GameMakerRuntimeAdapter
import com.indiehost.runtimes.LaunchOutcome
import com.indiehost.runtimes.RunnerShortcut
import java.io.File

/**
 * Two jobs, in this order:
 *
 * 1. Show the games the user has already imported into Butterscotch and start
 *    one with a tap. That works by launching Butterscotch's pinned game
 *    shortcut, which is possible while IndieHost is the home app.
 * 2. Everything else: open Butterscotch itself, and read a data.win's bytecode
 *    version before the user spends time importing it.
 */
class MainActivity : ComponentActivity() {

    private val runtime = GameMakerRuntimeAdapter()

    private var shortcuts by mutableStateOf<List<RunnerShortcut>>(emptyList())
    private var canListShortcuts by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshShortcuts()
        setContent {
            MaterialTheme {
                Surface { HomeScreen(shortcuts, canListShortcuts, runtime) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Shortcuts are pinned inside another app, so the list can change while
        // we are in the background.
        refreshShortcuts()
    }

    private fun refreshShortcuts() {
        canListShortcuts = runtime.canListShortcuts(this)
        shortcuts = if (canListShortcuts) runtime.shortcuts(this) else emptyList()
    }
}

@Composable
private fun HomeScreen(
    shortcuts: List<RunnerShortcut>,
    canListShortcuts: Boolean,
    runtime: GameMakerRuntimeAdapter,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var status by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("No game loaded yet") }
    var game by remember { mutableStateOf<Game?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Game data stays in the app's private cache. It is never bundled,
        // uploaded or committed.
        val importDir = File(context.cacheDir, "import").apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(importDir, "data.win")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        val detection = EngineDetector().detect(importDir)
        game = Game(
            id = "import",
            title = target.parentFile?.name ?: "imported",
            folderPath = importDir.absolutePath,
            engine = detection.engine,
            wadVersion = detection.wadVersion,
        )
        report = buildString {
            appendLine("Engine: ${detection.engine.displayName}")
            appendLine("Runnable: ${if (detection.runnable) "yes" else "no"}")
            detection.wadVersion?.let { appendLine("WAD / bytecode version: $it") }
            detection.blocker?.let { appendLine("Blocker: $it") }
            detection.notes.forEach { appendLine("- $it") }
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("IndieHost", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (canListShortcuts) {
            if (shortcuts.isEmpty()) {
                Text(
                    "No games pinned yet.\n\n" +
                        "In Butterscotch, long-press a game and pin it to your home screen. " +
                        "It will show up here."
                )
            } else {
                Text("Your games", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                shortcuts.forEach { shortcut ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                val host = activity ?: return@clickable
                                status = when (val outcome = runtime.launchShortcut(host, shortcut)) {
                                    is LaunchOutcome.Launched -> "Opening ${shortcut.title}"
                                    is LaunchOutcome.RuntimeNotInstalled ->
                                        "Butterscotch is not installed\n${outcome.installUrl}"
                                    is LaunchOutcome.Blocked -> outcome.reason
                                }
                            }
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
        } else {
            Text(
                "To list the games you have in Butterscotch, set IndieHost as your home app."
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                        .onFailure { status = "Open Settings and set IndieHost as your home app." }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Choose home app")
            }
        }

        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val host = activity ?: return@Button
                status = when (val outcome = runtime.launch(host, game ?: runnerOnlyGame())) {
                    is LaunchOutcome.Launched -> "Opened Butterscotch"
                    is LaunchOutcome.RuntimeNotInstalled ->
                        "Butterscotch is not installed\n${outcome.installUrl}"
                    is LaunchOutcome.Blocked -> outcome.reason
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Butterscotch")
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Check a data.win")
        }

        Spacer(Modifier.height(16.dp))
        Text(report, style = MaterialTheme.typography.bodyMedium)
    }
}

/** [GameMakerRuntimeAdapter.launch] only needs the engine, never the game files. */
private fun runnerOnlyGame(): Game =
    Game(id = "open-runner", title = "Butterscotch", folderPath = "", engine = Engine.GAMEMAKER_VM)
