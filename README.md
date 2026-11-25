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

**Milestone 0 passed.** Undertale (GameMaker VM, WAD 16) plays at a usable frame rate on
the test phone — a MediaTek Helio A22 with four Cortex-A53 cores and a PowerVR GE8300.
Findings, the device test procedure and what is still unmeasured are in
[MILESTONE-0.md](MILESTONE-0.md).

**Current build: `0.3.0`.** IndieHost is a daily-usable home screen, styled like a
game client:

- a pinned-game **library** with a "play next" panel, one tap to start;
- a **recents** row, from usage access when granted and from apps opened here otherwise;
- an **app drawer** with search, built from `LauncherApps`;
- the Milestone 0 `data.win` check, kept as a tool.

## Downloads

Signed release APKs are on the [Releases page](https://github.com/devx1626/indiehost/releases).
Unsigned debug APKs from CI are attached to [Actions runs](https://github.com/devx1626/indiehost/actions/workflows/android.yml)
as the `app-debug` artifact.

## Documentation

| Document | What it covers |
| --- | --- |
| [spec.md](spec.md) | Normative product and technical specification |
| [PREREQUISITES.md](PREREQUISITES.md) | Toolchain, device setup, game-data rules |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, flows, contracts, technical debt |
| [ROADMAP.md](ROADMAP.md) | Milestones and exit criteria |
| [TESTING.md](TESTING.md) | Automated and manual test protocol |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Build, style, commit and release workflow |
| [LICENSING.md](LICENSING.md) | AGPL boundary, game-data policy, third-party licences |
| [SECURITY.md](SECURITY.md) | Permissions, exported components, threat model |
| [AGENTS.md](AGENTS.md) | Operating rules for AI coding agents |
| [.opencode/SKILLS.md](.opencode/SKILLS.md) | Project-local agent skill set and provenance |
| [docs/adr](docs/adr/README.md) | Architecture decision records |

## How a game is started

Butterscotch's `GameActivity` is not exported, so a launcher cannot pass it a folder over
an Intent. That is not a dead end:

1. IndieHost declares itself a home app, which grants it shortcut-host permission.
2. It also answers `CONFIRM_PIN_SHORTCUT` for Butterscotch's `game-` shortcuts; other pin
   requests are declined.
3. In Butterscotch, open a game's settings and tap **Add Shortcut to Home Screen**.
4. The game appears in IndieHost's library; tapping it calls `startShortcut`, which runs
   Butterscotch's own activity in its own process.

Nothing of Butterscotch is linked, forked or repackaged, and game files never leave the
phone. A pin belongs to the launcher that accepted it, so games pinned while another
launcher was active must be re-pinned with IndieHost as home.

## Modules

- `:app` — Compose home screen and drawer
- `:library` — data layer (Game model; Room in Milestone 1)
- `:detect` — pure Kotlin engine fingerprinting, unit-tested on the host
- `:runtimes` — one `RuntimeAdapter` per engine (`canRun`, `prepare`, `launch`, `supportsSaves`)

## Build

Requires JDK 17 and Android SDK 34. Full setup, including the device flow, is in
[PREREQUISITES.md](PREREQUISITES.md).

```bash
gradle wrapper --gradle-version 8.7   # once: regenerates gradle/wrapper/gradle-wrapper.jar
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :detect:test                # engine detection tests
python3 tools/make_icons.py           # re-render the legacy launcher PNGs
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
