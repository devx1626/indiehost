package com.indiehost.library

/** Keeps Room types on the storage side of the [GameRepository] seam. */
fun GameEntity.toGame(): Game = Game(
    id = id,
    title = title,
    folderPath = folderPath,
    engine = engine,
    wadVersion = wadVersion,
    coverPath = coverPath,
    addedAt = addedAt,
    source = source,
    linkedShortcutId = linkedShortcutId,
)

fun Game.toEntity(): GameEntity = GameEntity(
    id = id,
    title = title,
    folderPath = folderPath,
    engine = engine,
    wadVersion = wadVersion,
    coverPath = coverPath,
    addedAt = addedAt,
    source = source,
    linkedShortcutId = linkedShortcutId,
)
