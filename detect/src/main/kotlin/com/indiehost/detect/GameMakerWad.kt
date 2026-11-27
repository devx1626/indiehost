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
 */
object GameMakerWad {

    /** WAD versions Butterscotch advertises support for (8 through 17). */
    val SUPPORTED_WAD_VERSIONS = 8..17

    private const val GEN8_ID_OFFSET = 8
    private const val VERSION_OFFSET = 17
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
        return runCatching {
            file.inputStream().use { read(it, file.length()) }
        }.getOrNull()
    }

    /**
     * Sequential variant for a SAF document or any other stream.
     *
     * Chunk lengths include their 8-byte header and are padded to a 4-byte
     * boundary. The stream is never rewound: the 20-byte prefix is read once,
     * the rest of GEN8 is skipped, then top-level chunk headers are walked.
     * [length] is optional; when supplied it stops the walk at a truncated end.
     */
    fun read(input: InputStream, length: Long? = null): Header? {
        val head = ByteArray(20)
        if (readFully(input, head) < head.size) return null
        if (ascii(head, 0, 4) != "FORM") return null
        if (ascii(head, GEN8_ID_OFFSET, 4) != "GEN8") return null

        val version = head[VERSION_OFFSET].toInt() and 0xFF
        if (version !in 1..30) return null

        val gen8Length = leU32(head, 12)
        if (gen8Length < 8L) return Header(version, hasCodeChunk = false)

        // The 20-byte prefix already contains the first 12 bytes of GEN8.
        val nextChunk = 8L + align4(gen8Length)
        var pos = head.size.toLong()
        if (nextChunk > pos) {
            if (!skipFully(input, nextChunk - pos)) return Header(version, hasCodeChunk = false)
            pos = nextChunk
        }

        var seen = 0
        while (seen < MAX_CHUNKS) {
            val chunk = ByteArray(8)
            val read = readFully(input, chunk)
            if (read == 0) break
            if (read < chunk.size) return Header(version, hasCodeChunk = false)

            val id = ascii(chunk, 0, 4)
            val chunkLength = leU32(chunk, 4)
            if (chunkLength < 8L) return Header(version, hasCodeChunk = false)
            if (id == "CODE") return Header(version, hasCodeChunk = true)

            val aligned = align4(chunkLength)
            if (length != null && pos + aligned > length) return Header(version, hasCodeChunk = false)
            if (!skipFully(input, aligned - 8L)) return Header(version, hasCodeChunk = false)

            pos += aligned
            seen++
        }
        return Header(version, hasCodeChunk = false)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                // skip() may legitimately return 0; consume one byte instead.
                if (input.read() < 0) return false
                remaining--
            }
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
