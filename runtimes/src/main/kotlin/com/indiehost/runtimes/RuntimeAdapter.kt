package com.indiehost.runtimes

import android.app.Activity
import com.indiehost.detect.Engine
import com.indiehost.library.Game
import java.io.File

/**
 * One implementation per engine. Adding an engine later should be one class in
 * this module plus one line in the adapter registry, not a refactor.
 */
interface RuntimeAdapter {

    val engine: Engine

    /** True only when this adapter can actually boot [folder] on this hardware. */
    fun canRun(folder: File): Boolean

    /**
     * Validates the import and returns what the runtime needs. Must not copy
     * copyrighted game data anywhere it could be published or backed up.
     */
    fun prepare(folder: File, game: Game): PreparedGame

    /**
     * [shortcut] is the Butterscotch pin a scanned game was explicitly linked to.
     * Engines hosted by IndieHost ignore it.
     */
    fun launch(activity: Activity, game: Game, shortcut: RunnerShortcut? = null): LaunchOutcome

    fun supportsSaves(): Boolean
}

data class PreparedGame(
    val dataFile: File?,
    val wadVersion: Int?,
    val notes: List<String>,
)

sealed interface LaunchOutcome {
    /** The game's runtime took over. */
    data class Launched(val packageName: String) : LaunchOutcome

    /** The runtime is a separate app that is not installed on this device. */
    data class RuntimeNotInstalled(val packageName: String, val installUrl: String) : LaunchOutcome

    data class Blocked(val reason: String) : LaunchOutcome
}
