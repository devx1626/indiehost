package com.indiehost.runtimes

import android.app.Activity
import android.content.Intent
import com.indiehost.detect.Engine
import com.indiehost.library.Game
import java.io.File

/**
 * RPG Maker MV/MZ runtime for games the M1 scanner found in a SAF tree.
 *
 * The game is not copied and no server is started. `GameWebViewActivity` in the
 * app module serves the SAF documents to WebView through a local asset loader;
 * this module only names the Activity by a manifest action so the dependency
 * direction stays app → runtimes, never the reverse.
 */
class RpgMakerRuntimeAdapter(
    override val engine: Engine,
) : RuntimeAdapter {

    init {
        require(engine == Engine.RPGMAKER_MV || engine == Engine.RPGMAKER_MZ) {
            "RpgMakerRuntimeAdapter only supports MV and MZ"
        }
    }

    /** Exposed for tests; the value is shared with the app's manifest. */
    val launchAction: String get() = GAME_WEBVIEW_ACTION

    /**
     * A SAF tree cannot be represented as a `File`, so a cheap pre-flight here
     * would be wrong. `GameWebViewActivity` validates the actual documents.
     */
    override fun canRun(folder: File): Boolean = false

    override fun prepare(folder: File, game: Game): PreparedGame = PreparedGame(
        dataFile = null,
        wadVersion = null,
        notes = listOf("RPG Maker assets are validated from the SAF tree at launch"),
    )

    override fun launch(
        activity: Activity,
        game: Game,
        shortcut: RunnerShortcut?,
    ): LaunchOutcome {
        val intent = Intent(GAME_WEBVIEW_ACTION)
            .setPackage(activity.packageName)
            .putExtra(EXTRA_GAME_ID, game.id)

        return runCatching { activity.startActivity(intent) }
            .fold(
                onSuccess = { LaunchOutcome.Launched(activity.packageName) },
                onFailure = {
                    LaunchOutcome.Blocked(
                        "Could not open the RPG Maker runtime. Reinstall IndieHost and try again."
                    )
                },
            )
    }

    /** WebView's DOM storage holds MV/MZ saves; export is a later milestone. */
    override fun supportsSaves(): Boolean = true

    companion object {
        const val GAME_WEBVIEW_ACTION = "com.indiehost.launcher.GAME_WEBVIEW"
        const val EXTRA_GAME_ID = "com.indiehost.launcher.game_id"
    }
}
