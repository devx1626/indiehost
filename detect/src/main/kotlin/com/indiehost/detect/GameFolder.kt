package com.indiehost.detect

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** One child of a [GameFolder], named exactly as the folder lists it. */
data class GameEntry(val name: String, val isDirectory: Boolean)

/**
 * A read-only view of a game folder.
 *
 * The detector works through this seam so the same rules read a normal
 * filesystem folder and a SAF document tree. Implementations must not assume
 * that a child is seekable or that its full contents fit in memory.
 */
interface GameFolder {
    val name: String
    val isDirectory: Boolean

    fun entries(): List<GameEntry>

    /** Returns null when the named child does not exist. */
    fun child(name: String): GameFolder?

    /** Opens a named file, or returns null when it cannot be read. */
    fun open(name: String): InputStream?
}

/** The plain-filesystem implementation used by tests and the `data.win` checker. */
class FileGameFolder(val file: File) : GameFolder {

    override val name: String get() = file.name

    override val isDirectory: Boolean get() = file.isDirectory

    override fun entries(): List<GameEntry> =
        file.listFiles()
            ?.map { GameEntry(it.name, it.isDirectory) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    override fun child(name: String): GameFolder? {
        if (name.isEmpty() || name.contains('/') || name.contains('\\')) return null
        val child = File(file, name)
        return if (child.exists()) FileGameFolder(child) else null
    }

    override fun open(name: String): InputStream? {
        if (name.isEmpty() || name.contains('/') || name.contains('\\')) return null
        val child = File(file, name)
        if (!child.isFile) return null
        return runCatching { FileInputStream(child) }.getOrNull()
    }
}
