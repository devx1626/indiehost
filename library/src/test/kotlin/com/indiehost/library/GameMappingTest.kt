package com.indiehost.library

import com.indiehost.detect.Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameMappingTest {

    @Test
    fun `a full game round-trips through the entity`() {
        val game = Game(
            id = "tree/doc/UNDERTALE",
            title = "UNDERTALE",
            folderPath = "content://tree/doc/UNDERTALE",
            engine = Engine.GAMEMAKER_VM,
            wadVersion = 16,
            coverPath = "covers/abc.webp",
            addedAt = 1_763_000_000_000L,
            source = GameSource.SCANNED,
            linkedShortcutId = "game-1234",
        )

        assertEquals(game, game.toEntity().toGame())
    }

    @Test
    fun `optional fields stay null`() {
        val game = Game(
            id = "pinned-1",
            title = "Pinned game",
            folderPath = "",
            engine = Engine.GAMEMAKER_VM,
        )

        val entity = game.toEntity()
        assertNull(entity.wadVersion)
        assertNull(entity.coverPath)
        assertNull(entity.linkedShortcutId)
        assertEquals(GameSource.PINNED, entity.source)
        assertEquals(game, entity.toGame())
    }

    @Test
    fun `every engine survives the mapping`() {
        for (engine in Engine.values()) {
            val game = Game(id = engine.name, title = engine.name, folderPath = "", engine = engine)
            assertEquals(engine, game.toEntity().toGame().engine)
        }
    }

    @Test
    fun `converters store enums by name`() {
        val converters = GameTypeConverters()
        assertEquals("RPGMAKER_MZ", converters.engineToString(Engine.RPGMAKER_MZ))
        assertEquals(Engine.RPGMAKER_MZ, converters.stringToEngine("RPGMAKER_MZ"))
        assertEquals("SCANNED", converters.sourceToString(GameSource.SCANNED))
        assertEquals(GameSource.SCANNED, converters.stringToSource("SCANNED"))
    }

    @Test
    fun `an unknown stored engine degrades instead of throwing`() {
        val converters = GameTypeConverters()
        assertEquals(Engine.UNKNOWN, converters.stringToEngine("SOME_FUTURE_ENGINE"))
        assertEquals(GameSource.PINNED, converters.stringToSource("SOME_FUTURE_SOURCE"))
    }
}
