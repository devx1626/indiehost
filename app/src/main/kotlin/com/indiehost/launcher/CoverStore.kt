package com.indiehost.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * User-picked cover art, stored under `filesDir/covers`.
 *
 * The picked image is decoded with bounds first, subsampled, then scaled to at
 * most [MAX_PX]. A full-resolution phone screenshot is never kept. The file
 * name is a hash of the game id, so replacing a cover is deterministic.
 */
class CoverStore(private val context: Context) {

    private val directory = File(context.filesDir, "covers")

    fun file(fileName: String): File = File(directory, fileName)

    /** Decodes on the calling thread; callers use a background thread. */
    fun load(fileName: String): ImageBitmap? {
        val file = file(fileName)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }

    /** Returns the stored file name, or null when the image cannot be decoded or written. */
    fun save(gameId: String, uri: Uri): String? {
        val bitmap = decodeDownsampled(uri) ?: return null
        directory.mkdirs()
        val name = fileNameFor(gameId)
        val target = File(directory, name)
        val temp = File(directory, "$name.tmp")

        return try {
            @Suppress("DEPRECATION")
            val written = FileOutputStream(temp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
            }
            if (!written) {
                temp.delete()
                null
            } else {
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                name
            }
        } catch (e: Exception) {
            temp.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(fileName: String) {
        file(fileName).delete()
    }

    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null

        val longest = max(decoded.width, decoded.height)
        if (longest <= MAX_PX) return decoded

        val scale = MAX_PX.toFloat() / longest
        val width = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val height = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    companion object {
        const val MAX_PX = 512

        fun fileNameFor(gameId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(gameId.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            } + ".webp"
        }

        /** Keeps the decoded edge between `max / 2` and `max`. */
        internal fun sampleSizeFor(width: Int, height: Int, max: Int = MAX_PX): Int {
            var sample = 1
            while (width / sample > max || height / sample > max) sample *= 2
            return sample
        }
    }
}
