package com.indiehost.launcher

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.indiehost.detect.EngineDetector
import com.indiehost.library.Game
import com.indiehost.runtimes.GameMakerRuntimeAdapter
import com.indiehost.runtimes.LaunchOutcome
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { MilestoneZeroScreen() }
            }
        }
    }
}

/**
 * Milestone 0 deliberately has no library, settings or onboarding. It answers
 * one question: does this device read this game's bytecode version, and can we
 * get the runner on screen.
 */
@Composable
private fun MilestoneZeroScreen() {
    val context = LocalContext.current
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

    Column(Modifier.padding(24.dp)) {
        Text("Milestone 0", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(report)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pick data.win")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val current = game ?: return@Button
                val activity = context as? Activity ?: return@Button
                report = when (val outcome = GameMakerRuntimeAdapter().launch(activity, current)) {
                    is LaunchOutcome.Launched -> "Opened ${outcome.packageName}\n" +
                        "Now import the same data.win there once."
                    is LaunchOutcome.RuntimeNotInstalled ->
                        "Runner not installed:\n${outcome.packageName}\n${outcome.installUrl}"
                    is LaunchOutcome.Blocked -> "Blocked: ${outcome.reason}"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open GameMaker runner (Butterscotch)")
        }
    }
}
