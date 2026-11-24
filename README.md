# IndieHost

An Android launcher that runs indie PC games natively by re-hosting their engine — no Wine,
no x86 translation, no Windows emulation.

Many indie games compile their game logic to portable bytecode rather than a native
executable, so the right runtime can run them directly on ARM. This repository is the
launcher: it fingerprints a game folder and hands it to a native runtime per engine.

| Engine | Runtime |
| --- | --- |
| GameMaker (VM builds only, never YYC/GMRT) | [Butterscotch](https://github.com/ButterscotchRunner/Butterscotch) — AGPL-3.0, run as a separate app, not linked here |
| RPG Maker MV / MZ | WebView |
| Ren'Py | official Android runtime (RAPT) |
| LOVE | official Android build |
| ScummVM | native port |

Unity, Godot and Unreal are deliberately unsupported: they ship per-platform native
binaries, so there is nothing portable to re-host.

## Status

**Milestone 0** — prove one GameMaker game runs at a playable speed on a low-end phone
(MediaTek Helio A22, 4x Cortex-A53, PowerVR GE8300). See [MILESTONE-0.md](MILESTONE-0.md) for
findings, the device test procedure, and what is still unverified.

Current known gap: Butterscotch's `GameActivity` is not exported, so a launcher cannot hand
it a game over an Intent yet. `:runtimes` validates the data file and opens the runner; the
user imports the game inside the runner once. Closing that gap is Milestone 1.

## Modules

- `:app` — Compose UI (minimal)
- `:library` — data layer (Game model; Room in Milestone 1)
- `:detect` — pure Kotlin engine fingerprinting, unit-tested on the host
- `:runtimes` — one `RuntimeAdapter` per engine (`canRun`, `prepare`, `launch`, `supportsSaves`)

## Build

Requires JDK 17 and Android SDK 34.

```bash
gradle wrapper --gradle-version 8.7   # once: regenerates gradle/wrapper/gradle-wrapper.jar
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :detect:test                # engine detection tests
```

The Gradle wrapper's binary jar is not committed; Android Studio regenerates it when the
project is opened, otherwise the first command above does it.

## Game files

Game data is copyrighted and is **never** committed, packaged or uploaded. `games/`,
`*.win`, `*.droid`, `*.love`, `*.rpy*`, `*.ogg` and friends are all in `.gitignore`.
You point the app at your own copy.

## Licence

Launcher code is the author's. Butterscotch is AGPL-3.0; this project only launches the
installed app and does not link its native code, so no AGPL obligation attaches to this
repository. Forking or bundling Butterscotch would change that.
