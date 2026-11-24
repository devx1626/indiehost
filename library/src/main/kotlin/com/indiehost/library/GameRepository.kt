package com.indiehost.library

/**
 * Storage seam. A Room-backed implementation replaces [InMemoryGameRepository]
 * in Milestone 1 without touching the runtimes or the detector.
 */
interface GameRepository {
    fun all(): List<Game>
    fun find(id: String): Game?
    fun add(game: Game)
    fun remove(id: String)
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
