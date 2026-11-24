package com.indiehost.detect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val detector = EngineDetector()

    @Test
    fun `Undertale-shaped folder is a runnable WAD 16 game`() {
        val folder = tmp.newFolder("UNDERTALE")
        writeWad(File(folder, "data.win"), version = 16)

        val result = detector.detect(folder)
        assertEquals(Engine.GAMEMAKER_VM, result.engine)
        assertTrue(result.runnable)
        assertEquals(16, result.wadVersion)
    }

    @Test
    fun `game_droid is accepted too`() {
        val folder = tmp.newFolder("port")
        writeWad(File(folder, "game.droid"), version = 16)
        assertTrue(detector.detect(folder).runnable)
    }

    @Test
    fun `a YYC build is rejected with a reason`() {
        val folder = tmp.newFolder("yyc")
        writeWad(File(folder, "data.win"), version = 16, withCode = false)

        val result = detector.detect(folder)
        assertFalse(result.runnable)
        assertNotNull(result.blocker)
        assertTrue(result.blocker!!.contains("CODE"))
    }

    @Test
    fun `a version outside Butterscotch's range is rejected`() {
        val folder = tmp.newFolder("future")
        writeWad(File(folder, "data.win"), version = 18)

        val result = detector.detect(folder)
        assertFalse(result.runnable)
        assertTrue(result.blocker!!.contains("18"))
    }

    @Test
    fun `distinguishes RPG Maker MV from MZ`() {
        val mv = tmp.newFolder("mv")
        File(mv, "www/js").mkdirs()
        File(mv, "www/index.html").writeText("<html></html>")
        File(mv, "www/js/rpg_core.js").writeText("// mv")
        assertEquals(Engine.RPGMAKER_MV, detector.detect(mv).engine)

        val mz = tmp.newFolder("mz")
        File(mz, "www/js").mkdirs()
        File(mz, "www/index.html").writeText("<html></html>")
        File(mz, "www/js/rmmz_core.js").writeText("// mz")
        assertEquals(Engine.RPGMAKER_MZ, detector.detect(mz).engine)
    }

    @Test
    fun `detects LOVE and RenPy`() {
        val love = tmp.newFolder("love")
        File(love, "mygame.love").writeText("PK")
        assertEquals(Engine.LOVE, detector.detect(love).engine)

        val renpy = tmp.newFolder("renpy")
        File(renpy, "game").mkdirs()
        File(renpy, "game/script.rpy").writeText("label start:")
        assertEquals(Engine.RENPY, detector.detect(renpy).engine)
    }

    @Test
    fun `an empty folder is unknown`() {
        assertEquals(Engine.UNKNOWN, detector.detect(tmp.newFolder("empty")).engine)
    }

    private fun writeWad(file: File, version: Int, withCode: Boolean = true) {
        val out = java.io.ByteArrayOutputStream()
        fun u32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        out.write("FORM".toByteArray()); u32(0)
        out.write("GEN8".toByteArray()); u32(24)
        out.write(1); out.write(version); out.write(0); out.write(0); out.write(ByteArray(12))
        if (withCode) { out.write("CODE".toByteArray()); u32(12); u32(0) }
        out.write("ENDF".toByteArray()); u32(8)
        file.writeBytes(out.toByteArray())
    }
}
