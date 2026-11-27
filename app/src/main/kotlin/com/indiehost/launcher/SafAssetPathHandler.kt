package com.indiehost.launcher

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader

/**
 * Serves one SAF document for each request under `/game/`.
 *
 * Called by WebViewAssetLoader on a worker thread. The stream is returned to
 * WebView, which closes it; nothing is buffered or written to disk.
 */
class SafAssetPathHandler(
    private val resolver: SafTreeResolver,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        val segments = WebPaths.segments(path) ?: return null
        val stream = runCatching { resolver.open(path) }.getOrNull() ?: return null
        return WebResourceResponse(WebPaths.mimeFor(segments.last()), null, stream)
    }
}
