package com.indiehost.launcher

import java.net.URL
import java.security.MessageDigest

/**
 * The URLs WebView is allowed to use, and the path rules for resolving them
 * into a SAF tree. Kept pure so the security rules are host-testable.
 */
object WebPaths {

    /** Android's reserved domain for locally intercepted assets. */
    const val ORIGIN = "appassets.androidplatform.net"

    /** Every game file is mounted below this path, never at the domain root. */
    const val PREFIX = "/game/"

    /** Bounds for untrusted paths coming from game HTML/JS. */
    const val MAX_SEGMENTS = 24
    const val MAX_SEGMENT_LENGTH = 128
    const val MAX_ASSET_BYTES = 64L * 1024 * 1024

    /**
     * The two layouts EngineDetector accepts: a `www/` wrapper or files at the
     * folder root. Try them in this order so a `www/` game wins when both exist.
     */
    val ENTRY_CANDIDATES: List<Pair<String, String>> = listOf(
        "www/index.html" to "www/js/rpg_core.js",
        "index.html" to "js/rpg_core.js",
    )

    /**
     * Each game gets its own port on the reserved host. Origin = scheme + host +
     * port, so two games cannot read each other's localStorage/IndexedDB even
     * though all requests are intercepted locally.
     */
    fun portFor(gameId: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(gameId.toByteArray(Charsets.UTF_8))
        val value = ((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)
        return PORT_BASE + (value % PORT_RANGE)
    }

    fun urlFor(gameId: String, indexPath: String): String =
        "https://$ORIGIN:${portFor(gameId)}$PREFIX$indexPath"

    /** True only for this game's HTTPS origin and a path under [PREFIX]. */
    fun isLocal(url: String, port: Int): Boolean {
        val parsed = runCatching { URL(url) }.getOrNull() ?: return false
        return parsed.protocol == "https" &&
            parsed.host == ORIGIN &&
            parsed.port == port &&
            parsed.userInfo == null &&
            parsed.path.startsWith(PREFIX)
    }

    private const val PORT_BASE = 20000
    private const val PORT_RANGE = 40000

    /**
     * Returns decoded path segments, or null for anything that could escape the
     * game folder. Percent escapes are rejected outright: a decoded `%2e%2e`
     * would otherwise become a traversal after this check.
     */
    fun segments(relative: String): List<String>? {
        if (relative.isEmpty() || relative.startsWith('/') || relative.contains('\\')) return null
        if (relative.contains('%')) return null
        val parts = relative.split('/')
        if (parts.size > MAX_SEGMENTS) return null
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.length > MAX_SEGMENT_LENGTH }) {
            return null
        }
        return parts
    }

    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "js" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        // SVG can carry scripts that run in the page origin; only hand it to
        // WebView as an image once we have a sanitising policy.
        "svg" -> "application/octet-stream"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }
}
