package com.indiehost.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Blocking DAO. Every caller runs on a background thread. */
@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE")
    fun all(): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    fun find(id: String): GameEntity?

    /** A re-scan upserts; it never deletes rows the scan did not see. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    fun delete(id: String)
}
