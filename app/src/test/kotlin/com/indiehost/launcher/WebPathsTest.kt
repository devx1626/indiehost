package com.indiehost.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPathsTest {

    @Test
    fun `entry candidates match both detector layouts`() {
        assertEquals(
            listOf(
                "www/index.html" to "www/js/rpg_core.js",
                "index.html" to "js/rpg_core.js",
            ),
            WebPaths.ENTRY_CANDIDATES,
        )
        assertEquals(
            "https://appassets.androidplatform.net:${WebPaths.portFor("scan-1")}/game/index.html",
            WebPaths.urlFor("scan-1", "index.html"),
        )
    }

    @Test
    fun `only the reserved HTTPS origin and game path are local`() {
        val port = WebPaths.portFor("scan-1")
        assertTrue(WebPaths.isLocal(WebPaths.urlFor("scan-1", "www/index.html"), port))
        assertFalse(WebPaths.isLocal(WebPaths.urlFor("scan-2", "www/index.html"), port))
        assertFalse(WebPaths.isLocal("https://example.com:$port/game/www/index.html", port))
        assertFalse(WebPaths.isLocal("http://appassets.androidplatform.net:$port/game/www/index.html", port))
        assertFalse(WebPaths.isLocal("https://appassets.androidplatform.net:$port/other", port))
        assertFalse(WebPaths.isLocal("https://user@appassets.androidplatform.net:$port/game/index.html", port))
        assertFalse(WebPaths.isLocal("not a url", port))
    }

    @Test
    fun `segments reject traversal and hidden encodings`() {
        assertEquals(
            listOf("www", "js", "rpg_core.js"),
            WebPaths.segments("www/js/rpg_core.js"),
        )
        assertNull(WebPaths.segments("../secret"))
        assertNull(WebPaths.segments("/etc/passwd"))
        assertNull(WebPaths.segments("www/../../x"))
        assertNull(WebPaths.segments("www//js"))
        assertNull(WebPaths.segments("www/%2e%2e/secret"))
        assertNull(WebPaths.segments("www\\secret"))
        assertNull(WebPaths.segments(List(WebPaths.MAX_SEGMENTS + 1) { "a" }.joinToString("/")))
        assertNull(WebPaths.segments("a".repeat(WebPaths.MAX_SEGMENT_LENGTH + 1)))
    }

    @Test
    fun `mime types cover the assets an RPG Maker game loads`() {
        assertEquals("text/html", WebPaths.mimeFor("index.html"))
        assertEquals("text/javascript", WebPaths.mimeFor("rpg_core.js"))
        assertEquals("text/css", WebPaths.mimeFor("game.css"))
        assertEquals("image/png", WebPaths.mimeFor("icon.png"))
        assertEquals("image/jpeg", WebPaths.mimeFor("photo.JPG"))
        assertEquals("audio/ogg", WebPaths.mimeFor("theme.ogg"))
        assertEquals("application/octet-stream", WebPaths.mimeFor("tile.rpgmvp"))
        assertEquals("application/octet-stream", WebPaths.mimeFor("icon.svg"))
    }

    @Test
    fun `game ports are stable and distinct`() {
        val a = WebPaths.portFor("scan-1")
        assertEquals(a, WebPaths.portFor("scan-1"))
        assertTrue(a in 20000..59999)
        assertTrue(a != WebPaths.portFor("scan-2"))
    }
}
