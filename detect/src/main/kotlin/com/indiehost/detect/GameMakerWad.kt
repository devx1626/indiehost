package com.indiehost.detect

import java.io.File
import java.io.InputStream

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
 *
 * Reading is sequential only: the stream overload works on a SAF
 * `InputStream`, where seeking is not available.
 */
object GameMakerWad {

    /** WAD versions Butterscotch advertises support for (8 through 17). */
    val SUPPORTED_WAD_VERSIONS = 8..17

    private const val GEN8_ID_OFFSET = 8

    private const val VERSION_OFFSET = 17
    private const val MAX_CHUNKS = 512
    private const val HEADER_BYTES = 20

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
        if (!file.isFile || file.length() < HEADER_BYTES.toLong()) return null
        return try {
            file.inputStream().use { read(it) }
        } catch (e: Exception) {
            // Truncated or unreadable file: treat as "not a GameMaker data file"
            // rather than pretending we know its version.
            null
        }
    }

    /**
     * Stream overload for callers that cannot seek, such as a SAF document.
     * The stream is not closed here.
     */
    fun read(input: InputStream): Header? {
        return try {
            val head = ByteArray(HEADER_BYTES)
            if (!readFully(input, head)) return null
            if (ascii(head, 0, 4) != "FORM") return null
            if (ascii(head, GEN8_ID_OFFSET, 4) != "GEN8") return null

            val version = head[VERSION_OFFSET].toInt() and 0xFF
            if (version !in 1..30) return null

            // The 20 byte header stops 4 bytes inside the GEN8 chunk, so finish
            // that chunk before walking the rest of the top-level list.
            val gen8Length = leU32(head, 12)
            if (gen8Length < 12L) return null
            if (!skipFully(input, align4(gen8Length) - 12L)) return null

            Header(wadVersion = version, hasCodeChunk = hasCodeChunk(input))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Walks the top-level chunk list looking for CODE. Chunk lengths include the
     * 8 byte header and are padded to a 4 byte boundary.
     */
    private fun hasCodeChunk(input: InputStream): Boolean {
        var seen = 0
        while (seen < MAX_CHUNKS) {
            val header = ByteArray(8)
            if (!readFully(input, header)) return false
            val id = ascii(header, 0, 4)
            val length = leU32(header, 4)
            if (length < 8L) return false
            if (id == "CODE") return true
            if (!skipFully(input, align4(length) - 8L)) return false
            seen++
        }
        return false
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var remaining = count
        val scratch = ByteArray(8 * 1024)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            val read = input.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
            if (read < 0) return false
            remaining -= read.toLong()
        }
        return true
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
