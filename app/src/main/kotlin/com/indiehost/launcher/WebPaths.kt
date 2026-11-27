package com.indiehost.launcher

import java.net.URL

/**
 * The URLs WebView is allowed to use, and the path rules for resolving them
 * into a SAF tree. Kept pure so the security rules are host-testable.
 */
object WebPaths {

    /** Android's reserved domain for locally intercepted assets. */
    const val ORIGIN = "appassets.androidplatform.net"

    /** Every game file is mounted below this path, never at the domain root. */
    const val PREFIX = "/game/"

    /**
     * The two layouts EngineDetector accepts: a `www/` wrapper or files at the
     * folder root. Try them in this order so a `www/` game wins when both exist.
     */
    val ENTRY_CANDIDATES: List<Pair<String, String>> = listOf(
        "www/index.html" to "www/js/rpg_core.js",
        "index.html" to "js/rpg_core.js",
    )

    fun urlFor(indexPath: String): String = "https://$ORIGIN$PREFIX$indexPath"

    /** True only for our HTTPS origin and a path under [PREFIX]. */
    fun isLocal(url: String): Boolean {
        val parsed = runCatching { URL(url) }.getOrNull() ?: return false
        return parsed.protocol == "https" &&
            parsed.host == ORIGIN &&
            parsed.port == -1 &&
            parsed.userInfo == null &&
            parsed.path.startsWith(PREFIX)
    }

    /**
     * Returns decoded path segments, or null for anything that could escape the
     * game folder. Percent escapes are rejected outright: a decoded `%2e%2e`
     * would otherwise become a traversal after this check.
     */
    fun segments(relative: String): List<String>? {
        if (relative.isEmpty() || relative.startsWith('/') || relative.contains('\\')) return null
        if (relative.contains('%')) return null
        val parts = relative.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
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
        "svg" -> "image/svg+xml"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }
}
