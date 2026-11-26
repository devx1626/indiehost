package com.indiehost.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Blocking DAO. It is called from background threads only, matching the
 * project's plain `Thread` pattern; no coroutines until the ViewModel refactor.
 *
 * Inserts are upserts: a re-scan refreshes rows and must not drop covers or
 * shortcut links. Nothing is ever auto-deleted by a scan.
 */
@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE ASC")
    fun all(): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    fun find(id: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(game: GameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(games: List<GameEntity>)

    @Query("DELETE FROM games WHERE id = :id")
    fun delete(id: String)
}
