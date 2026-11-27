package com.indiehost.launcher

import com.indiehost.detect.Engine
import com.indiehost.detect.GameEntry
import com.indiehost.detect.GameFolder
import com.indiehost.library.GameSource
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameScannerTest {

    private val scanner = GameScanner()

    @Test
    fun `imports GameMaker and MV and ignores an unknown folder`() {
        val root = folder(
            "Games",
            dirs = mapOf(
                "Undertale" to folder("Undertale", files = mapOf("data.win" to wad(16))),
                "MV" to folder(
                    "MV",
                    dirs = mapOf(
                        "www" to folder(
                            "www",
                            files = mapOf("index.html" to "<html></html>".toByteArray()),
                            dirs = mapOf("js" to folder("js", files = mapOf("rpg_core.js" to "// mv".toByteArray()))),
                        ),
                    ),
                ),
                "Notes" to folder("Notes", files = mapOf("readme.txt" to "hi".toByteArray())),
            ),
        )

        val result = scanner.scan(root, "content://tree", emptyList(), now = 42L)

        assertEquals(2, result.games.size)
        assertEquals(listOf("MV", "Undertale"), result.games.map { it.title })
        assertEquals(Engine.RPGMAKER_MV, result.games[0].engine)
        assertEquals(Engine.GAMEMAKER_VM, result.games[1].engine)
        assertEquals(16, result.games[1].wadVersion)
        assertTrue(result.games.all { it.source == GameSource.SCANNED })
        assertTrue(result.games.all { it.addedAt == 42L })
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `a re-scan preserves the cover and pin link`() {
        val root = folder(
            "Games",
            dirs = mapOf("Undertale" to folder("Undertale", files = mapOf("data.win" to wad(16)))),
        )

        val first = scanner.scan(root, "content://tree", emptyList(), now = 1L).games.single()
        val linked = first.copy(coverPath = "cover.webp", linkedShortcutId = "game-1234")
        val second = scanner.scan(root, "content://tree", listOf(linked), now = 2L).games.single()

        assertEquals(first.id, second.id)
        assertEquals("cover.webp", second.coverPath)
        assertEquals("game-1234", second.linkedShortcutId)
        assertEquals(1L, second.addedAt)
    }

    @Test
    fun `does not descend past two levels`() {
        val deep = folder(
            "Games",
            dirs = mapOf(
                "one" to folder(
                    "one",
                    dirs = mapOf(
                        "two" to folder(
                            "two",
                            dirs = mapOf(
                                "three" to folder("three", files = mapOf("data.win" to wad(16))),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(scanner.scan(deep, "content://tree", emptyList()).games.isEmpty())
    }

    @Test
    fun `an unreadable candidate is skipped with a reason`() {
        val root = folder(
            "Games",
            dirs = mapOf(
                "Broken" to folder(
                    "Broken",
                    files = mapOf("data.win" to wad(16)),
                    failOpen = true,
                ),
            ),
        )

        val result = scanner.scan(root, "content://tree", emptyList())

        assertTrue(result.games.isEmpty())
        assertEquals(1, result.skipped.size)
        assertTrue(result.skipped.single().startsWith("Broken:"))
    }

    @Test
    fun `ids are stable and tree-specific`() {
        val a = GameScanner.idFor("content://tree-a", "content://doc/1")
        val b = GameScanner.idFor("content://tree-a", "content://doc/1")
        val c = GameScanner.idFor("content://tree-b", "content://doc/1")

        assertEquals(a, b)
        assertNotEquals(a, c)
        assertTrue(a.startsWith("scan-"))
        assertFalse(a.contains("content"))
    }

    // --- fakes --------------------------------------------------------------

    private fun folder(
        name: String,
        files: Map<String, ByteArray> = emptyMap(),
        dirs: Map<String, FakeFolder> = emptyMap(),
        failOpen: Boolean = false,
    ): FakeFolder = FakeFolder(name, "content://doc/$name", files, dirs, failOpen)

    private class FakeFolder(
        override val name: String,
        override val location: String,
        private val files: Map<String, ByteArray>,
        private val dirs: Map<String, FakeFolder>,
        private val failOpen: Boolean,
    ) : GameFolder {
        override val isDirectory: Boolean = true

        override fun entries(): List<GameEntry> =
            (dirs.keys.map { GameEntry(it, true) } + files.keys.map { GameEntry(it, false) })
                .sortedBy { it.name }

        override fun child(name: String): GameFolder? =
            dirs[name] ?: files[name]?.let { bytes ->
                FakeFile(name, "$location/$name", bytes, failOpen)
            }

        override fun open(name: String): InputStream? {
            if (failOpen) throw IllegalStateException("permission denied")
            return files[name]?.inputStream()
        }
    }

    private class FakeFile(
        override val name: String,
        override val location: String,
        private val bytes: ByteArray,
        private val failOpen: Boolean,
    ) : GameFolder {
        override val isDirectory: Boolean = false

        override fun entries(): List<GameEntry> = emptyList()

        override fun child(name: String): GameFolder? = null

        override fun open(name: String): InputStream? {
            if (failOpen) throw IllegalStateException("permission denied")
            return bytes.inputStream()
        }
    }

    private fun wad(version: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        out.write("FORM".toByteArray()); u32(0)
        out.write("GEN8".toByteArray()); u32(24)
        out.write(1); out.write(version); out.write(0); out.write(0); out.write(ByteArray(12))
        out.write("CODE".toByteArray()); u32(12); u32(0)
        out.write("ENDF".toByteArray()); u32(8)
        return out.toByteArray()
    }
}
