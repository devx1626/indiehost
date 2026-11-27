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

    fun detect(folder: File): Detection = detect(FileGameFolder(folder))

    fun detect(folder: GameFolder): Detection {
        if (!folder.isDirectory) return Detection(Engine.UNKNOWN, false, blocker = "Not a folder")

        detectGameMaker(folder)?.let { return it }
        detectRpgMaker(folder)?.let { return it }
        detectRenPy(folder)?.let { return it }
        detectLove(folder)?.let { return it }
        detectScummVm(folder)?.let { return it }

        return Detection(Engine.UNKNOWN, false, blocker = "No known engine signature in this folder")
    }

    // --- GameMaker ---------------------------------------------------------

    private fun detectGameMaker(folder: GameFolder): Detection? {
        val dataName = DATA_FILE_NAMES.firstOrNull { folder.child(it)?.isDirectory == false }
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
            .filter { it.isDirectory }
        val js = roots
            .mapNotNull { it.child("js") }
            .firstOrNull { it.isDirectory }
            ?: return null
        if (roots.none { it.child("index.html")?.isDirectory == false }) return null

        val mz = js.child("rmmz_core.js")?.isDirectory == false
        val mv = js.child("rpg_core.js")?.isDirectory == false
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
        val hasScripts = gameDir?.isDirectory == true &&
            gameDir.entries().any { it.name.endsWith(".rpy") || it.name.endsWith(".rpyc") }
        val hasRenpy = folder.child("renpy")?.isDirectory == true
        if (!hasScripts && !hasRenpy) return null
        return Detection(Engine.RENPY, runnable = true, notes = listOf("game/ scripts found"))
    }

    // --- LOVE --------------------------------------------------------------

    private fun detectLove(folder: GameFolder): Detection? {
        val love = folder.entries()
            .firstOrNull { !it.isDirectory && it.name.endsWith(".love") }
            ?: return null
        return Detection(Engine.LOVE, runnable = true, notes = listOf("package: ${love.name}"))
    }

    // --- ScummVM -----------------------------------------------------------

    private fun detectScummVm(folder: GameFolder): Detection? {
        val marker = findScummVmMarker(folder, depth = 2) ?: return null
        return Detection(Engine.SCUMMVM, runnable = true, notes = listOf("marker: $marker"))
    }

    private fun findScummVmMarker(folder: GameFolder, depth: Int): String? {
        if (!folder.isDirectory) return null
        folder.entries()
            .firstOrNull { !it.isDirectory && it.name.endsWith(".scummvm") }
            ?.let { return it.name }
        if (depth == 0) return null
        folder.entries()
            .filter { it.isDirectory }
            .forEach { entry ->
                folder.child(entry.name)?.let { child ->
                    findScummVmMarker(child, depth - 1)?.let { return it }
                }
            }
        return null
    }

    private companion object {
        val DATA_FILE_NAMES = listOf("data.win", "game.droid", "game.unx")
    }
}
