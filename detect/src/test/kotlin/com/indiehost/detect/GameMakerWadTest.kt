package com.indiehost.detect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GameMakerWadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `reads WAD 16 out of a Studio 1_4 data file`() {
        val file = tmp.newFile("data.win")
        file.writeBytes(wad(version = 16, withCode = true))

        val header = GameMakerWad.read(file)!!
        assertEquals(16, header.wadVersion)
        assertTrue(header.hasCodeChunk)
        assertTrue(header.supportedByButterscotch)
    }

    @Test
    fun `reads each supported version`() {
        for (version in 8..17) {
            val file = tmp.newFile("v$version.win")
            file.writeBytes(wad(version = version, withCode = true))
            assertEquals(version, GameMakerWad.read(file)!!.wadVersion)
        }
    }

    @Test
    fun `flags a modern runtime version as unsupported`() {
        val file = tmp.newFile("modern.win")
        file.writeBytes(wad(version = 18, withCode = true))

        val header = GameMakerWad.read(file)!!
        assertEquals(18, header.wadVersion)
        assertFalse(header.supportedByButterscotch)
    }

    @Test
    fun `detects a build with no CODE chunk`() {
        val file = tmp.newFile("yyc.win")
        file.writeBytes(wad(version = 16, withCode = false))
        assertFalse(GameMakerWad.read(file)!!.hasCodeChunk)
    }

    @Test
    fun `rejects files that are not GameMaker data files`() {
        val notWad = tmp.newFile("readme.txt")
        notWad.writeText("this is not a data file")
        assertNull(GameMakerWad.read(notWad))

        val truncated = tmp.newFile("short.win")
        truncated.writeBytes(byteArrayOf('F'.code.toByte(), 'O'.code.toByte()))
        assertNull(GameMakerWad.read(truncated))
    }

    // --- synthetic data file ------------------------------------------------

    /**
     * Builds a minimal well-formed data file: FORM header, GEN8 chunk with
     * isDebuggerDisabled/bytecodeVersion, then an optional CODE chunk and a
     * trailing ENDF chunk.
     */
    private fun wad(version: Int, withCode: Boolean): ByteArray {
        val out = java.io.ByteArrayOutputStream()

        fun u32(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 24) and 0xFF)
        }

        out.write("FORM".toByteArray())
        u32(0) // patched below
        out.write("GEN8".toByteArray())
        u32(24) // chunk length including the 8 byte header
        out.write(1) // isDebuggerDisabled
        out.write(version) // bytecodeVersion
        out.write(0) // padding (u16)
        out.write(0)
        out.write(ByteArray(12)) // filler: GEN8 is 24 bytes including its 8 byte header

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
