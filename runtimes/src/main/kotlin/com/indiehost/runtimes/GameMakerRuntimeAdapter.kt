package com.indiehost.runtimes

import android.app.Activity
import android.content.Context
import com.indiehost.detect.Engine
import com.indiehost.detect.EngineDetector
import com.indiehost.detect.GameMakerWad
import com.indiehost.library.Game
import java.io.File

/**
 * Milestone 0 GameMaker runtime.
 *
 * Undertale and every other VM GameMaker game is a `data.win` plus a runner.
 * The runner we target is Butterscotch's Android build, which is AGPL-3.0.
 * To keep our launcher's licence clean we do NOT link the native runner into
 * our APK: Butterscotch is an app the user installs (Play Store), and we only
 * open it or ask the system to start one of its shortcuts.
 *
 * Its `GameActivity` is `exported="false"` and there is no import Intent, so a
 * game cannot be handed over directly. What does work without touching their
 * code: the user pins a game inside Butterscotch, and Android lets the current
 * home app list and start that pinned shortcut. [shortcuts] and
 * [launchShortcut] use exactly that; [launch] stays as the fallback for when
 * IndieHost is not the home app.
 */
class GameMakerRuntimeAdapter(
    private val detector: EngineDetector = EngineDetector(),
) : RuntimeAdapter {

    override val engine: Engine = Engine.GAMEMAKER_VM

    override fun canRun(folder: File): Boolean {
        val detection = detector.detect(folder)
        return detection.engine == Engine.GAMEMAKER_VM && detection.runnable
    }

    override fun prepare(folder: File, game: Game): PreparedGame {
        val dataFile = DATA_FILE_NAMES
            .map { File(folder, it) }
            .firstOrNull { it.isFile }
            ?: return PreparedGame(null, null, listOf("No data.win / game.droid in ${folder.name}"))

        val header = GameMakerWad.read(dataFile)
            ?: return PreparedGame(dataFile, null, listOf("${dataFile.name} is not a readable GameMaker data file"))

        val notes = buildList {
            add("${dataFile.name}: WAD ${header.wadVersion}")
            if (!header.hasCodeChunk) {
                add("YYC/GMRT build (no CODE chunk): a bytecode runner cannot run this")
            } else {
                add("Butterscotch Android supports WAD ${GameMakerWad.SUPPORTED_WAD_VERSIONS}")
            }
        }
        return PreparedGame(dataFile, header.wadVersion, notes)
    }

    override fun launch(activity: Activity, game: Game): LaunchOutcome {
        val intent = activity.packageManager.getLaunchIntentForPackage(BUTTERSCOTCH_PACKAGE)
            ?: return LaunchOutcome.RuntimeNotInstalled(BUTTERSCOTCH_PACKAGE, PLAY_STORE_URL)
        activity.startActivity(intent)
        return LaunchOutcome.Launched(BUTTERSCOTCH_PACKAGE)
    }

    /**
     * Games the user has already pinned inside Butterscotch. Empty when
     * IndieHost is not the home app, when nothing is pinned, or on Android
     * versions without launcher shortcut support.
     */
    fun shortcuts(context: Context): List<RunnerShortcut> =
        ButterscotchShortcuts(context.applicationContext).list()

    /** True while this app is the current home app, i.e. allowed to read shortcuts. */
    fun canListShortcuts(context: Context): Boolean =
        ButterscotchShortcuts(context.applicationContext).canRead()

    /** Starts one pinned game directly; nothing is copied and nothing is linked. */
    fun launchShortcut(activity: Activity, shortcut: RunnerShortcut): LaunchOutcome =
        if (ButterscotchShortcuts(activity.applicationContext).start(shortcut)) {
            LaunchOutcome.Launched(shortcut.packageName)
        } else {
            LaunchOutcome.Blocked(
                "Butterscotch did not accept the launch request for ${shortcut.title}. " +
                    "Set IndieHost as your home app, then try again."
            )
        }

    /** GameMaker's own `save.ini` style saves work; Butterscotch stores them per game. */
    override fun supportsSaves(): Boolean = true

    companion object {
        const val BUTTERSCOTCH_PACKAGE = "net.perfectdreams.butterscotch"
        const val PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=net.perfectdreams.butterscotch"

        private val DATA_FILE_NAMES = listOf("data.win", "game.droid", "game.unx")
    }
}
