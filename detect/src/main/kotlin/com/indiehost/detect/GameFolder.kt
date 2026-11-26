package com.indiehost.detect

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** One direct child of a [GameFolder]. */
data class GameEntry(val name: String, val isDirectory: Boolean)

/**
 * A folder the detector can read without caring where it lives: a real
 * directory on disk, a SAF document tree, or a test double.
 *
 * All methods are non-throwing: an unreadable child is `null`, an unreadable
 * folder is an empty list. The detector turns that into a blocker, never a
 * crash.
 */
interface GameFolder {
    val name: String

    /** Direct children only, not recursive. */
    fun entries(): List<GameEntry>

    /** The named child if it is a directory, otherwise null. */
    fun child(name: String): GameFolder?

    /** The named child if it is a file. The caller closes the stream. */
    fun open(name: String): InputStream?
}

/** [GameFolder] over a normal filesystem directory. */
class FileGameFolder(private val file: File) : GameFolder {

    override val name: String get() = file.name

    override fun entries(): List<GameEntry> =
        file.listFiles().orEmpty().map { GameEntry(it.name, it.isDirectory) }

    override fun child(name: String): GameFolder? {
        val child = File(file, name)
        return if (child.isDirectory) FileGameFolder(child) else null
    }

    override fun open(name: String): InputStream? {
        val child = File(file, name)
        return if (child.isFile) FileInputStream(child) else null
    }
}

internal fun GameFolder.isFile(name: String): Boolean =
    entries().any { it.name == name && !it.isDirectory }

internal fun GameFolder.isDirectory(name: String): Boolean =
    entries().any { it.name == name && it.isDirectory }

/** Same semantics as `File.extension`: no dot means no extension. */
internal fun String.extension(): String = substringAfterLast('.', "")
