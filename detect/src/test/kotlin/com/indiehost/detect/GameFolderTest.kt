package com.indiehost.detect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GameFolderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val detector = EngineDetector()

    @Test
    fun `file and GameFolder paths agree on a WAD 16 game`() {
        val folder = tmp.newFolder("UNDERTALE")
        writeWad(File(folder, "data.win"), version = 16)

        val viaFile = detector.detect(folder)
        val viaFolder = detector.detect(FileGameFolder(folder))

        assertEquals(viaFile, viaFolder)
        assertTrue(viaFolder.runnable)
        assertEquals(Engine.GAMEMAKER_VM, viaFolder.engine)
        assertEquals(16, viaFolder.wadVersion)
    }

    @Test
    fun `detects RPG Maker MV through the folder seam`() {
        val folder = tmp.newFolder("mv")
        File(folder, "www/js").mkdirs()
        File(folder, "www/index.html").writeText("<html></html>")
        File(folder, "www/js/rpg_core.js").writeText("// mv")

        val result = detector.detect(FileGameFolder(folder))
        assertEquals(Engine.RPGMAKER_MV, result.engine)
        assertTrue(result.runnable)
    }

    @Test
    fun `an unknown folder through the seam is not runnable`() {
        val result = detector.detect(FileGameFolder(tmp.newFolder("empty")))
        assertEquals(Engine.UNKNOWN, result.engine)
        assertFalse(result.runnable)
        assertNotNull(result.blocker)
    }

    @Test
    fun `FileGameFolder lists children and opens only files`() {
        val folder = tmp.newFolder("tree")
        File(folder, "sub").mkdirs()
        File(folder, "data.win").writeText("x")

        val root = FileGameFolder(folder)
        assertTrue(root.isDirectory)
        assertEquals(listOf("data.win", "sub"), root.entries().map { it.name })
        assertTrue(root.child("sub")!!.isDirectory)
        assertFalse(root.child("data.win")!!.isDirectory)
        assertNotNull(root.open("data.win"))
        assertEquals(null, root.open("missing.win"))
        assertEquals(null, root.child("../escape"))
    }

    private fun writeWad(file: File, version: Int) {
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
        file.writeBytes(out.toByteArray())
    }
}
