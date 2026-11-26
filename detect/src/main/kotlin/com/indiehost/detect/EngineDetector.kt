package com.indiehost.detect

import java.io.File

/**
 * Fingerprints an imported game folder. Pure Kotlin, no Android APIs, so this
 * whole class is covered by host-side unit tests.
 *
 * Detection reads folder *contents* only through [GameFolder], so the same
 * rules apply to a filesystem directory and to a SAF tree on the device.
 */
class EngineDetector {

    data class Detection(
        val engine: Engine,
        /** True only when we are confident this can actually boot on the device. */
        val runnable: Boolean,
        val wadVersion: Int? = null,
        /** Human-readable reason when [runnable] is false. */
        val blocker: String? = null,
        val notes: List<String> = emptyList(),
    )

    /** Filesystem entry point. Kept for existing callers and tests. */
    fun detect(folder: File): Detection {
        if (!folder.isDirectory) return Detection(Engine.UNKNOWN, false, blocker = "Not a folder")
        return detect(FileGameFolder(folder))
    }

    fun detect(folder: GameFolder): Detection {
        detectGameMaker(folder)?.let { return it }
        detectRpgMaker(folder)?.let { return it }
        detectRenPy(folder)?.let { return it }
        detectLove(folder)?.let { return it }
        detectScummVm(folder)?.let { return it }

        return Detection(Engine.UNKNOWN, false, blocker = "No known engine signature in this folder")
    }

    // --- GameMaker ---------------------------------------------------------

    private fun detectGameMaker(folder: GameFolder): Detection? {
        val dataName = listOf("data.win", "game.droid", "game.unx")
            .firstOrNull { folder.isFile(it) }
            ?: return null

        val header = folder.open(dataName)?.use { GameMakerWad.read(it) }
            ?: return Detection(
                Engine.GAMEMAKER_VM,
                runnable = false,
                blocker = "$dataName is not a readable GameMaker data file",
            )

        val notes = mutableListOf("data file: $dataName, WAD version ${header.wadVersion}")

        if (!header.hasCodeChunk) {
            return Detection(
                Engine.GAMEMAKER_VM,
                runnable = false,
                wadVersion = header.wadVersion,
                blocker = "No CODE chunk: this is a YYC or GMRT build with native code, not bytecode",
                notes = notes,
            )
        }
        if (header.wadVersion !in GameMakerWad.SUPPORTED_WAD_VERSIONS) {
            return Detection(
                Engine.GAMEMAKER_VM,
                runnable = false,
                wadVersion = header.wadVersion,
                blocker = "WAD ${header.wadVersion} is outside the supported range " +
                    "${GameMakerWad.SUPPORTED_WAD_VERSIONS.first}..${GameMakerWad.SUPPORTED_WAD_VERSIONS.last}",
                notes = notes,
            )
        }
        return Detection(Engine.GAMEMAKER_VM, runnable = true, wadVersion = header.wadVersion, notes = notes)
    }

    // --- RPG Maker ---------------------------------------------------------

    private fun detectRpgMaker(folder: GameFolder): Detection? {
        val roots = listOfNotNull(folder, folder.child("www"))
        val js = roots.firstOrNull { it.isDirectory("js") }?.child("js") ?: return null
        if (roots.none { it.isFile("index.html") }) return null

        val mz = js.isFile("rmmz_core.js")
        val mv = js.isFile("rpg_core.js")
        return when {
            mz -> Detection(Engine.RPGMAKER_MZ, runnable = true, notes = listOf("www/ HTML5 build"))
            mv -> Detection(Engine.RPGMAKER_MV, runnable = true, notes = listOf("www/ HTML5 build"))
            else -> Detection(
                Engine.RPGMAKER_MV,
                runnable = false,
                blocker = "HTML5 layout but no rpg_core.js / rmmz_core.js found",
            )
        }
    }

    // --- Ren'Py ------------------------------------------------------------

    private fun detectRenPy(folder: GameFolder): Detection? {
        val gameDir = folder.child("game")
        val hasScripts = gameDir != null && gameDir.entries().any { entry ->
            !entry.isDirectory && entry.name.extension().let { it == "rpy" || it == "rpyc" }
        }
        val hasRenpy = folder.child("renpy") != null
        if (!hasScripts && !hasRenpy) return null
        return Detection(Engine.RENPY, runnable = true, notes = listOf("game/ scripts found"))
    }

    // --- LOVE --------------------------------------------------------------

    private fun detectLove(folder: GameFolder): Detection? {
        val love = folder.entries().firstOrNull { !it.isDirectory && it.name.extension() == "love" }
            ?: return null
        return Detection(Engine.LOVE, runnable = true, notes = listOf("package: ${love.name}"))
    }

    // --- ScummVM -----------------------------------------------------------

    /** Looks for a `*.scummvm` marker at the root or up to two levels below it. */
    private fun detectScummVm(folder: GameFolder): Detection? {
        var level = listOf(folder)
        repeat(3) {
            for (dir in level) {
                val marker = dir.entries()
                    .firstOrNull { !it.isDirectory && it.name.extension() == "scummvm" }
                if (marker != null) {
                    return Detection(Engine.SCUMMVM, runnable = true, notes = listOf("marker: ${marker.name}"))
                }
            }
            level = level.flatMap { dir ->
                dir.entries().filter { it.isDirectory }.mapNotNull { dir.child(it.name) }
            }
        }
        return null
    }
}
