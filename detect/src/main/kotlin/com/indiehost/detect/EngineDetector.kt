package com.indiehost.detect

import java.io.File

/**
 * Fingerprints an imported game folder. Pure Kotlin, no Android APIs, so this
 * whole class is covered by host-side unit tests.
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

    fun detect(folder: File): Detection {
        if (!folder.isDirectory) return Detection(Engine.UNKNOWN, false, blocker = "Not a folder")

        detectGameMaker(folder)?.let { return it }
        detectRpgMaker(folder)?.let { return it }
        detectRenPy(folder)?.let { return it }
        detectLove(folder)?.let { return it }
        detectScummVm(folder)?.let { return it }

        return Detection(Engine.UNKNOWN, false, blocker = "No known engine signature in this folder")
    }

    // --- GameMaker ---------------------------------------------------------

    private fun detectGameMaker(folder: File): Detection? {
        val dataFile = listOf("data.win", "game.droid", "game.unx")
            .map { File(folder, it) }
            .firstOrNull { it.isFile }
            ?: return null

        val header = GameMakerWad.read(dataFile)
            ?: return Detection(
                Engine.GAMEMAKER_VM,
                runnable = false,
                blocker = "${dataFile.name} is not a readable GameMaker data file",
            )

        val notes = mutableListOf("data file: ${dataFile.name}, WAD version ${header.wadVersion}")

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

    private fun detectRpgMaker(folder: File): Detection? {
        val roots = listOf(folder, File(folder, "www")).filter { it.isDirectory }
        val js = roots.map { File(it, "js") }.firstOrNull { it.isDirectory } ?: return null
        if (roots.none { File(it, "index.html").isFile }) return null

        val mz = File(js, "rmmz_core.js").isFile
        val mv = File(js, "rpg_core.js").isFile
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

    private fun detectRenPy(folder: File): Detection? {
        val gameDir = File(folder, "game")
        val hasScripts = gameDir.isDirectory &&
            (gameDir.listFiles()?.any { it.extension == "rpy" || it.extension == "rpyc" } == true)
        val hasRenpy = File(folder, "renpy").isDirectory
        if (!hasScripts && !hasRenpy) return null
        return Detection(Engine.RENPY, runnable = true, notes = listOf("game/ scripts found"))
    }

    // --- LOVE --------------------------------------------------------------

    private fun detectLove(folder: File): Detection? {
        val love = folder.listFiles()?.firstOrNull { it.extension == "love" } ?: return null
        return Detection(Engine.LOVE, runnable = true, notes = listOf("package: ${love.name}"))
    }

    // --- ScummVM -----------------------------------------------------------

    private fun detectScummVm(folder: File): Detection? {
        val marker = folder.walkTopDown().maxDepth(2)
            .firstOrNull { it.isFile && it.extension == "scummvm" }
            ?: return null
        return Detection(Engine.SCUMMVM, runnable = true, notes = listOf("marker: ${marker.name}"))
    }
}
