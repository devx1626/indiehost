package com.indiehost.detect

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads the runtime ("WAD") version out of a GameMaker: Studio data file.
 *
 * A data file is IFF-style:
 *
 *   0   "FORM"
 *   4   u32  file size
 *   8   "GEN8"            <- general info chunk id
 *   12  u32  chunk length (includes the 8 byte chunk header)
 *   16  u8   isDebuggerDisabled
 *   17  u8   bytecodeVersion   <- this is what we need
 *   18  u16  padding
 *
 * Field order confirmed against UndertaleModTool's `UndertaleGeneralInfo`
 * (`IsDebuggerDisabled`, then `BytecodeVersion`, then `Padding`), which is the
 * same reader UndertaleModTool shows the user on the PC. A version mismatch
 * between the data file and the runner is the most common reason these setups
 * fail, so this is read *before* anything is launched.
 */
object GameMakerWad {

    /** WAD versions Butterscotch advertises support for (8 through 17). */
    val SUPPORTED_WAD_VERSIONS = 8..17

    private const val GEN8_ID_OFFSET = 8L

    /**
     * Offset of the first chunk header. A chunk header is 4 bytes of id followed
     * by a u32 length that *includes* the header, so the walker must start at the
     * GEN8 id at offset 8, not at its length field.
     */
    private const val FIRST_CHUNK_OFFSET = 8L
    private const val VERSION_OFFSET = 17L
    private const val MAX_CHUNKS = 512

    data class Header(
        /** Runtime / "WAD" / bytecode version, 8..17 for the versions we care about. */
        val wadVersion: Int,
        /**
         * Whether a CODE chunk is present. YYC and GMRT builds have no bytecode
         * chunk, so this is the cheap signal that a build is native-compiled and
         * can never run on a bytecode runner.
         */
        val hasCodeChunk: Boolean,
    ) {
        val supportedByButterscotch: Boolean get() = wadVersion in SUPPORTED_WAD_VERSIONS
    }

    /** Returns null when the file is not a GameMaker data file at all. */
    fun read(file: File): Header? {
        if (!file.isFile || file.length() < 20L) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(20)
                raf.seek(0)
                raf.readFully(head)
                if (ascii(head, 0, 4) != "FORM") return null
                if (ascii(head, GEN8_ID_OFFSET.toInt(), 4) != "GEN8") return null

                val version = head[VERSION_OFFSET.toInt()].toInt() and 0xFF
                if (version !in 1..30) return null

                Header(wadVersion = version, hasCodeChunk = hasCodeChunk(raf, file.length()))
            }
        } catch (e: Exception) {
            // Truncated or unreadable file: treat as "not a GameMaker data file"
            // rather than pretending we know its version.
            null
        }
    }

    /**
     * Walks the top-level chunk list looking for CODE. Chunk lengths include the
     * 8 byte header and are padded to a 4 byte boundary.
     */
    private fun hasCodeChunk(raf: RandomAccessFile, fileLength: Long): Boolean {
        var pos = FIRST_CHUNK_OFFSET
        var seen = 0
        while (pos + 8 <= fileLength && seen < MAX_CHUNKS) {
            raf.seek(pos)
            val header = ByteArray(8)
            raf.readFully(header)
            val id = ascii(header, 0, 4)
            val length = leU32(header, 4)
            if (length < 8L) return false
            if (id == "CODE") return true
            pos += align4(length)
            seen++
        }
        return false
    }

    private fun align4(value: Long): Long = (value + 3L) / 4L * 4L

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)

    private fun leU32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
}
