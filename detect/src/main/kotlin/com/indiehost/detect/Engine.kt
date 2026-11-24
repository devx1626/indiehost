package com.indiehost.detect

/**
 * Engines this project knows how to re-host natively.
 *
 * Deliberately absent: Unity, Godot and Unreal. They ship per-platform native
 * binaries, so there is nothing portable to re-host.
 */
enum class Engine {
    /** GameMaker: Studio VM builds (data.win / game.droid + a runner). Never YYC or GMRT. */
    GAMEMAKER_VM,

    /** RPG Maker MV: HTML5 game, runs in a WebView. */
    RPGMAKER_MV,

    /** RPG Maker MZ: HTML5 game, runs in a WebView. */
    RPGMAKER_MZ,

    /** Ren'Py: official Android runtime (RAPT). */
    RENPY,

    /** LOVE: .love file + the official Android build. */
    LOVE,

    /** ScummVM: native port. */
    SCUMMVM,

    UNKNOWN;

    val displayName: String
        get() = when (this) {
            GAMEMAKER_VM -> "GameMaker (VM)"
            RPGMAKER_MV -> "RPG Maker MV"
            RPGMAKER_MZ -> "RPG Maker MZ"
            RENPY -> "Ren'Py"
            LOVE -> "LOVE"
            SCUMMVM -> "ScummVM"
            UNKNOWN -> "Unknown"
        }
}
