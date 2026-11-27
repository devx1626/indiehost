package com.indiehost.launcher

import android.app.Activity
import android.app.AlertDialog
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import com.indiehost.detect.Engine
import com.indiehost.library.RoomGameRepository
import com.indiehost.runtimes.RpgMakerRuntimeAdapter

/**
 * Offline host for RPG Maker MV/MZ games.
 *
 * The game stays in the user's SAF folder. WebView requests our reserved HTTPS
 * origin; WebViewAssetLoader hands the path to [SafAssetPathHandler], which reads
 * the document through the persisted grant. No INTERNET permission and no
 * localhost server are involved.
 */
class GameWebViewActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val focusListener = AudioManager.OnAudioFocusChangeListener {}

    private lateinit var webView: WebView
    private lateinit var overlay: LinearLayout
    private lateinit var overlayText: TextView
    private lateinit var overlayBack: Button
    private var showingExit = false

    @Volatile
    private var lastBlockedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = WebView(this)
        setContentView(buildRoot())

        val gameId = intent.getStringExtra(RpgMakerRuntimeAdapter.EXTRA_GAME_ID)
        if (gameId.isNullOrBlank()) {
            fail(getString(R.string.web_missing_game))
            return
        }

        Thread({
            val repository = RoomGameRepository(applicationContext)
            val game = runCatching { repository.find(gameId) }.getOrNull()

            var reason: String? = when {
                game == null -> getString(R.string.web_missing_game)
                game.engine != Engine.RPGMAKER_MV && game.engine != Engine.RPGMAKER_MZ ->
                    getString(R.string.web_wrong_engine)
                game.treeUri.isNullOrBlank() || game.folderPath.isBlank() ->
                    getString(R.string.web_no_folder)
                else -> null
            }

            var resolver: SafTreeResolver? = null
            var indexPath: String? = null
            if (reason == null) {
                resolver = runCatching {
                    SafTreeResolver(
                        applicationContext,
                        Uri.parse(game!!.treeUri),
                        Uri.parse(game.folderPath),
                    )
                }.getOrNull()
                if (resolver == null) {
                    reason = getString(R.string.web_no_access)
                } else {
                    val found = runCatching {
                        WebPaths.ENTRY_CANDIDATES.firstOrNull { (index, core) ->
                            resolver.exists(index) && resolver.exists(core)
                        }
                    }.getOrNull()
                    indexPath = found?.first
                    if (indexPath == null) reason = getString(R.string.web_missing_files)
                }
            }

            val loadedResolver = resolver
            val loadedPath = indexPath
            val failure = reason
            ui.post {
                if (failure != null || loadedResolver == null || loadedPath == null) {
                    fail(failure ?: getString(R.string.web_missing_files))
                } else {
                    startGame(loadedResolver, loadedPath)
                }
            }
        }, "webgame-load").start()
    }

    /** WebView plus a small overlay for loading and blocked-network states. */
    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        overlayText = TextView(this).apply {
            textSize = 16f
            setTextColor(resources.getColor(android.R.color.white, null))
            gravity = Gravity.CENTER
        }
        overlayBack = Button(this).apply {
            text = getString(R.string.back_to_library)
            setOnClickListener { finish() }
        }
        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC171A21.toInt())
            addView(
                overlayText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                overlayBack,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (24 * resources.displayMetrics.density).toInt() },
            )
            visibility = View.GONE
        }
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return root
    }

    private fun showLoading(message: String) {
        webView.post {
            overlayText.text = message
            overlayBack.visibility = View.GONE
            overlay.visibility = View.VISIBLE
        }
    }

    private fun hideOverlay() {
        webView.post { overlay.visibility = View.GONE }
    }

    private fun showBlockedInline() {
        webView.post {
            overlayText.text = getString(R.string.remote_blocked)
            overlayBack.visibility = View.VISIBLE
            overlay.visibility = View.VISIBLE
        }
    }

    private fun startGame(resolver: SafTreeResolver, indexPath: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Defence in depth: the app has no INTERNET permission, and off-origin
            // requests already get a blocked response. This makes WebView reject
            // network loads before our client is invoked too.
            blockNetworkLoads = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler(WebPaths.PREFIX, SafAssetPathHandler(resolver))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest,
            ): WebResourceResponse? = intercept(request.url.toString()) {
                loader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest,
            ): Boolean = blockIfRemote(request.url.toString())

            override fun onPageFinished(view: WebView?, url: String?) {
                hideOverlay()
            }

            private fun intercept(
                url: String?,
                local: () -> WebResourceResponse?,
            ): WebResourceResponse {
                if (!WebPaths.isLocal(url.orEmpty())) {
                    showBlocked()
                    return blockedResponse()
                }
                return runCatching(local).getOrNull() ?: blockedResponse()
            }
        }

        showLoading(getString(R.string.web_loading))
        webView.loadUrl(WebPaths.urlFor(indexPath))
    }

    private fun blockIfRemote(url: String?): Boolean {
        if (WebPaths.isLocal(url.orEmpty())) return false
        showBlocked()
        return true
    }

    /** A local response prevents WebView from attempting a real network fetch. */
    private fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "UTF-8", "Blocked by IndieHost".byteInputStream())

    private fun showBlocked() {
        val now = System.currentTimeMillis()
        if (now - lastBlockedAt < 3_000L) return
        lastBlockedAt = now
        showBlockedInline()
    }

    private fun fail(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.web_cannot_start)
            .setMessage(message)
            .setPositiveButton(R.string.close) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (showingExit) return
        showingExit = true
        AlertDialog.Builder(this)
            .setMessage(R.string.leave_game)
            .setPositiveButton(R.string.leave) { _, _ -> finish() }
            .setNegativeButton(R.string.resume) { _, _ -> showingExit = false }
            .setOnCancelListener { showingExit = false }
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        webView.onResume()
        audioManager.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
        )
    }

    override fun onPause() {
        webView.onPause()
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(focusListener)
        super.onPause()
    }

    override fun onDestroy() {
        webView.loadUrl("about:blank")
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
