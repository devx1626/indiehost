package com.indiehost.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Cover art the user picked. It lives in app-private storage under a name
 * derived from the game id, so a re-scan keeps the same file and nothing has to
 * be tracked in the database beyond the file name.
 *
 * Images are downsampled twice: while decoding (so a huge photo is never fully
 * decoded) and by scaling to at most [MAX_PX]. No image library is involved.
 */
object CoverImages {

    private const val DIR = "covers"
    private const val MAX_PX = 512

    /** Copies, downsamples and stores the image; returns the stored file name. */
    fun import(context: Context, uri: Uri, gameId: String): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("not an image")

        val scaled = scaleDown(decoded)
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val name = "${sha256(gameId)}.webp"
        File(dir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.WEBP, 85, it) }
        if (scaled !== decoded) decoded.recycle()
        return name
    }

    fun load(context: Context, name: String): Bitmap? {
        val file = File(File(context.filesDir, DIR), name)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun delete(context: Context, name: String) {
        File(File(context.filesDir, DIR), name).delete()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_PX && height / (sample * 2) >= MAX_PX) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_PX) return bitmap
        val scale = MAX_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
