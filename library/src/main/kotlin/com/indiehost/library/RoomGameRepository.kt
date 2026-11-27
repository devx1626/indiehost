package com.indiehost.library

import android.content.Context

/** Room-backed storage. Blocking by design; callers use a background thread. */
class RoomGameRepository(private val dao: GameDao) : GameRepository {

    constructor(context: Context) : this(IndieHostDatabase.get(context).games())

    override fun all(): List<Game> = dao.all().map { it.toGame() }

    override fun find(id: String): Game? = dao.find(id)?.toGame()

    override fun add(game: Game) {
        dao.upsert(game.toEntity())
    }

    override fun remove(id: String) {
        dao.delete(id)
    }

    override fun setAvailable(id: String, available: Boolean) {
        dao.setAvailable(id, available)
    }

    override fun recordScan(run: ScanRun) {
        dao.recordScan(run.toEntity())
    }

    override fun latestScan(): ScanRun? = dao.latestScan()?.toScanRun()
}
