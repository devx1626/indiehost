package com.indiehost.detect

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity tests for the [GameFolder] path, including a folder whose file cannot
 * be opened (the SAF failure mode) and a non-seekable stream.
 */
class GameFolderDetectorTest {

    private val detector = EngineDetector()

    @Test
    fun `detects a WAD 16 game through an in-memory folder`() {
        val folder = FakeFolder("UNDERTALE").apply {
            file("data.win", wad(16, withCode = true))
        }

        val result = detector.detect(folder)
        assertEquals(Engine.GAMEMAKER_VM, result.engine)
        assertTrue(result.runnable)
        assertEquals(16, result.wadVersion)
    }

    @Test
    fun `an unreadable data file becomes a blocker, not a crash`() {
        val folder = FakeFolder("broken").apply {
            file("data.win", ByteArray(32))
            unreadable += "data.win"
        }

        val result = detector.detect(folder)
        assertEquals(Engine.GAMEMAKER_VM, result.engine)
        assertFalse(result.runnable)
        assertTrue(result.blocker!!.contains("data.win"))
    }

    @Test
    fun `a YYC build is rejected through the folder path too`() {
        val folder = FakeFolder("yyc").apply {
            file("data.win", wad(16, withCode = false))
        }

        val result = detector.detect(folder)
        assertFalse(result.runnable)
        assertTrue(result.blocker!!.contains("CODE"))
    }

    @Test
    fun `reads RPG Maker MV and MZ from nested folders`() {
        val mv = FakeFolder("mv").apply {
            dir("www").apply {
                file("index.html", "<html></html>".toByteArray())
                file("js/rpg_core.js", "// mv".toByteArray())
            }
        }
        assertEquals(Engine.RPGMAKER_MV, detector.detect(mv).engine)
        assertTrue(detector.detect(mv).runnable)

        val mz = FakeFolder("mz").apply {
            dir("www").apply {
                file("index.html", "<html></html>".toByteArray())
                file("js/rmmz_core.js", "// mz".toByteArray())
            }
        }
        assertEquals(Engine.RPGMAKER_MZ, detector.detect(mz).engine)
    }

    @Test
    fun `finds a ScummVM marker two levels down`() {
        val folder = FakeFolder("scumm").apply {
            dir("games").dir("monkey").file("monkey.scummvm", ByteArray(0))
        }
        assertEquals(Engine.SCUMMVM, detector.detect(folder).engine)
    }

    @Test
    fun `an empty folder is unknown`() {
        val result = detector.detect(FakeFolder("empty"))
        assertEquals(Engine.UNKNOWN, result.engine)
        assertFalse(result.runnable)
    }

    @Test
    fun `stream reader matches the file reader`() {
        val bytes = wad(17, withCode = true)
        val header = GameMakerWad.read(ByteArrayInputStream(bytes))!!
        assertEquals(17, header.wadVersion)
        assertTrue(header.hasCodeChunk)
        assertTrue(header.supportedByButterscotch)
    }

    @Test
    fun `stream reader rejects foreign bytes`() {
        assertNull(GameMakerWad.read(ByteArrayInputStream("nope".toByteArray())))
        assertNotNull(GameMakerWad.read(ByteArrayInputStream(wad(16, withCode = true))))
    }

    // --- helpers ------------------------------------------------------------

    private class FakeFolder(override val name: String) : GameFolder {
        private val files = mutableMapOf<String, ByteArray>()
        private val dirs = mutableMapOf<String, FakeFolder>()
        val unreadable = mutableSetOf<String>()

        fun dir(name: String): FakeFolder {
            val folder = FakeFolder(name)
            dirs[name] = folder
            return folder
        }

        /** Creates `a/b/c` style paths inside this folder. */
        fun file(path: String, bytes: ByteArray) = apply {
            val parts = path.split('/')
            var current = this
            for (part in parts.dropLast(1)) current = current.dir(part)
            current.files[parts.last()] = bytes
        }

        override fun entries(): List<GameEntry> =
            files.keys.map { GameEntry(it, false) } + dirs.keys.map { GameEntry(it, true) }

        override fun child(name: String): GameFolder? = dirs[name]

        override fun open(name: String) =
            if (name in unreadable) null else files[name]?.inputStream()
    }

    private fun wad(version: Int, withCode: Boolean): ByteArray {
        val out = java.io.ByteArrayOutputStream()

        fun u32(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 24) and 0xFF)
        }

        out.write("FORM".toByteArray())
        u32(0)
        out.write("GEN8".toByteArray())
        u32(24)
        out.write(1)
        out.write(version)
        out.write(0)
        out.write(0)
        out.write(ByteArray(12))
        if (withCode) {
            out.write("CODE".toByteArray())
            u32(12)
            u32(0)
        }
        out.write("ENDF".toByteArray())
        u32(8)

        val bytes = out.toByteArray()
        val size = bytes.size
        bytes[4] = (size and 0xFF).toByte()
        bytes[5] = ((size ushr 8) and 0xFF).toByte()
        bytes[6] = ((size ushr 16) and 0xFF).toByte()
        bytes[7] = ((size ushr 24) and 0xFF).toByte()
        return bytes
    }
}
