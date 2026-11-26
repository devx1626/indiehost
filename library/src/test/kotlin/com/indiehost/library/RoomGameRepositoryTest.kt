package com.indiehost.library

import com.indiehost.detect.Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomGameRepositoryTest {

    @Test
    fun `a re-scan keeps the cover, the link and the add time`() {
        val dao = FakeDao()
        val repository = RoomGameRepository(dao)

        repository.add(
            Game(
                id = "scan:tree:UNDERTALE",
                title = "UNDERTALE",
                folderPath = "content://old",
                engine = Engine.GAMEMAKER_VM,
                wadVersion = 16,
                coverPath = "covers/undertale.webp",
                addedAt = 123L,
                source = GameSource.SCANNED,
                linkedShortcutId = "game-1",
            ),
        )

        repository.upsertAll(
            listOf(
                Game(
                    id = "scan:tree:UNDERTALE",
                    title = "UNDERTALE",
                    folderPath = "content://new",
                    engine = Engine.GAMEMAKER_VM,
                    wadVersion = 16,
                    addedAt = 999L,
                    source = GameSource.SCANNED,
                ),
            ),
        )

        val stored = repository.find("scan:tree:UNDERTALE")!!
        assertEquals("content://new", stored.folderPath)
        assertEquals("covers/undertale.webp", stored.coverPath)
        assertEquals("game-1", stored.linkedShortcutId)
        assertEquals(123L, stored.addedAt)
    }

    @Test
    fun `a scan inserts new rows without deleting old ones`() {
        val dao = FakeDao()
        val repository = RoomGameRepository(dao)
        repository.add(Game("pinned", "Pinned", "", Engine.GAMEMAKER_VM, source = GameSource.PINNED))

        repository.upsertAll(listOf(Game("scanned", "Scanned", "u", Engine.RPGMAKER_MV, source = GameSource.SCANNED)))

        assertEquals(listOf("Pinned", "Scanned"), repository.all().map { it.title })
        assertNull(repository.find("missing"))
    }

    @Test
    fun `remove deletes one row`() {
        val dao = FakeDao()
        val repository = RoomGameRepository(dao)
        repository.add(Game("a", "A", "", Engine.GAMEMAKER_VM))
        repository.add(Game("b", "B", "", Engine.GAMEMAKER_VM))

        repository.remove("a")

        assertEquals(listOf("B"), repository.all().map { it.title })
    }

    /** Minimal in-memory DAO so the repository rules are testable on the host. */
    private class FakeDao : GameDao {
        private val rows = linkedMapOf<String, GameEntity>()

        override fun all(): List<GameEntity> = rows.values.sortedBy { it.title.lowercase() }

        override fun find(id: String): GameEntity? = rows[id]

        override fun upsert(game: GameEntity) {
            rows[game.id] = game
        }

        override fun upsertAll(games: List<GameEntity>) {
            games.forEach { rows[it.id] = it }
        }

        override fun delete(id: String) {
            rows.remove(id)
        }
    }
}
