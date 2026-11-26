package com.indiehost.library

/**
 * Room-backed [GameRepository]. DAO calls are blocking, so every method here is
 * expected to be called from a background thread, like every other storage path
 * in the launcher.
 */
class RoomGameRepository(private val dao: GameDao) : GameRepository {

    override fun all(): List<Game> = dao.all().map { it.toGame() }

    override fun find(id: String): Game? = dao.find(id)?.toGame()

    override fun add(game: Game) = dao.upsert(game.toEntity())

    override fun remove(id: String) = dao.delete(id)

    /**
     * Re-scan support: insert new rows and refresh existing ones, never delete.
     *
     * A scan only knows what it just detected, so it must not overwrite what the
     * user attached: the cover, the linked shortcut and the original add time
     * are kept from the stored row.
     */
    fun upsertAll(games: List<Game>) {
        val merged = games.map { incoming ->
            val existing = dao.find(incoming.id)?.toGame()
            if (existing == null) {
                incoming
            } else {
                incoming.copy(
                    coverPath = existing.coverPath ?: incoming.coverPath,
                    addedAt = existing.addedAt,
                    linkedShortcutId = existing.linkedShortcutId ?: incoming.linkedShortcutId,
                )
            }
        }
        dao.upsertAll(merged.map { it.toEntity() })
    }
}
