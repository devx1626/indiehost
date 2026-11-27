package com.indiehost.launcher

import com.indiehost.detect.Engine
import com.indiehost.library.Game
import com.indiehost.library.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMergeTest {

    @Test
    fun `pins come first and a linked pin is hidden`() {
        val pin = LibraryPin("game-1", "Undertale")
        val game = game(id = "scan-1", title = "Undertale", linked = "game-1")

        val items = mergeLibrary(listOf(game), listOf(pin))

        assertEquals(1, items.size)
        assertEquals("game:scan-1", items.single().key)
        assertEquals("game-1", items.single().shortcutId)
        assertTrue(items.single().launchable)
    }

    @Test
    fun `an unlinked GameMaker scan needs a pin`() {
        val item = mergeLibrary(
            listOf(game(id = "scan-1", title = "Undertale")),
            emptyList(),
        ).single()

        assertFalse(item.launchable)
        assertEquals(LibraryReason.NEEDS_PIN, item.reason)
    }

    @Test
    fun `a link to a pin that is no longer pinned needs a pin`() {
        val item = mergeLibrary(
            listOf(game(id = "scan-1", title = "Undertale", linked = "game-gone")),
            emptyList(),
        ).single()

        assertFalse(item.launchable)
        assertEquals(LibraryReason.NEEDS_PIN, item.reason)
        assertEquals("game-gone", item.shortcutId)
    }

    @Test
    fun `scanned MV and MZ rows are launchable`() {
        val items = mergeLibrary(
            listOf(
                game(id = "scan-mv", title = "MV", engine = Engine.RPGMAKER_MV),
                game(id = "scan-mz", title = "MZ", engine = Engine.RPGMAKER_MZ),
            ),
            emptyList(),
        )

        assertEquals(listOf(null, null), items.map { it.reason })
        assertTrue(items.all { it.launchable })
    }

    @Test
    fun `an MV row without a scanned folder still waits for WebView`() {
        val item = mergeLibrary(
            listOf(
                Game(
                    id = "pin-mv",
                    title = "MV",
                    folderPath = "",
                    engine = Engine.RPGMAKER_MV,
                    source = GameSource.PINNED,
                ),
            ),
            emptyList(),
        ).single()

        assertFalse(item.launchable)
        assertEquals(LibraryReason.WEBVIEW_M2, item.reason)
    }

    @Test
    fun `RenPy LOVE and ScummVM say why they are not hosted`() {
        val items = mergeLibrary(
            listOf(
                game(id = "scan-r", title = "Ren", engine = Engine.RENPY),
                game(id = "scan-l", title = "Love", engine = Engine.LOVE),
                game(id = "scan-s", title = "Scumm", engine = Engine.SCUMMVM),
            ),
            emptyList(),
        )

        assertEquals(
            listOf(
                LibraryReason.RENPY_M3,
                LibraryReason.LOVE_M3,
                LibraryReason.SCUMMVM_M3,
            ),
            items.map { it.reason },
        )
        assertTrue(items.none { it.launchable })
    }

    @Test
    fun `a detector blocker wins over a linked pin`() {
        val item = mergeLibrary(
            listOf(
                game(
                    id = "scan-1",
                    title = "YYC",
                    linked = "game-1",
                    runnable = false,
                    blocker = "No CODE chunk",
                ),
            ),
            listOf(LibraryPin("game-1", "YYC")),
        ).single()

        assertFalse(item.launchable)
        assertEquals(LibraryReason.DETECTOR_BLOCKED, item.reason)
    }

    @Test
    fun `a pin with no scan is a launchable row`() {
        val item = mergeLibrary(emptyList(), listOf(LibraryPin("game-1", "Undertale"))).single()

        assertNull(item.game)
        assertEquals("game-1", item.shortcutId)
        assertTrue(item.launchable)
    }

    private fun game(
        id: String,
        title: String,
        engine: Engine = Engine.GAMEMAKER_VM,
        linked: String? = null,
        runnable: Boolean = true,
        blocker: String? = null,
    ) = Game(
        id = id,
        title = title,
        folderPath = "content://doc/$id",
        engine = engine,
        wadVersion = if (engine == Engine.GAMEMAKER_VM) 16 else null,
        source = GameSource.SCANNED,
        linkedShortcutId = linked,
        treeUri = "content://tree",
        runnable = runnable,
        blocker = blocker,
    )
}
