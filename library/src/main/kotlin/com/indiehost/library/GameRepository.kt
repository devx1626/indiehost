package com.indiehost.library

/**
 * Storage seam. [RoomGameRepository] is the production implementation;
 * [InMemoryGameRepository] stays for tests and previews.
 *
 * All methods block. Call them from a background thread, never the main thread.
 */
interface GameRepository {
    fun all(): List<Game>
    fun find(id: String): Game?
    fun add(game: Game)
    fun remove(id: String)

    /** Default no-ops keep test doubles simple; Room overrides all three. */
    fun setAvailable(id: String, available: Boolean) {}
    fun recordScan(run: ScanRun) {}
    fun latestScan(): ScanRun? = null
}

class InMemoryGameRepository : GameRepository {
    private val games = linkedMapOf<String, Game>()

    override fun all(): List<Game> = games.values.toList()

    override fun find(id: String): Game? = games[id]

    override fun add(game: Game) {
        games[game.id] = game
    }

    override fun remove(id: String) {
        games.remove(id)
    }
}
