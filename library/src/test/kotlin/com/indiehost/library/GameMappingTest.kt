package com.indiehost.library

import com.indiehost.detect.Engine
import org.junit.Assert.assertEquals
import org.junit.Test

class GameMappingTest {

    @Test
    fun `round trips every stored field`() {
        val game = Game(
            id = "scan-abc",
            title = "Undertale",
            folderPath = "content://tree/undertale",
            engine = Engine.GAMEMAKER_VM,
            wadVersion = 16,
            coverPath = "abc.webp",
            addedAt = 1_700_000_000_000L,
            source = GameSource.SCANNED,
            linkedShortcutId = "game-1234",
            treeUri = "content://tree",
            runnable = false,
            blocker = "No CODE chunk",
        )

        assertEquals(game, game.toEntity().toGame())
    }

    @Test
    fun `an unknown engine name maps to UNKNOWN`() {
        val converters = GameConverters()
        assertEquals(Engine.UNKNOWN, converters.stringToEngine("NOT_AN_ENGINE"))
    }

    @Test
    fun `an unknown source name maps to SCANNED`() {
        val converters = GameConverters()
        assertEquals(GameSource.SCANNED, converters.stringToSource("NOT_A_SOURCE"))
    }
}
