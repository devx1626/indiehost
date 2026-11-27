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
| Ren'Py | Detected only — RAPT builds a per-game APK ([ADR-0008](docs/adr/0008-renpy-detected-not-hosted.md)) |
| LÖVE | Detected only — generic player can open `.love`; handoff not wired ([ADR-0009](docs/adr/0009-love-handoff-not-implemented.md)) |
| ScummVM | Detected only — handoff needs a game configured in ScummVM ([ADR-0010](docs/adr/0010-scummvm-handoff-not-implemented.md)) |

Unity, Godot and Unreal are deliberately unsupported: they ship per-platform native
binaries, so there is nothing portable to re-host.

## Status

**Milestone 0 passed.** Undertale (GameMaker VM, WAD 16) plays at a usable frame rate on
the test phone — a MediaTek Helio A22 with four Cortex-A53 cores and a PowerVR GE8300.
Findings, the device test procedure and what is still unmeasured are in
[MILESTONE-0.md](MILESTONE-0.md).

**Current build: `0.4.0`.** IndieHost is a daily-usable home screen, styled like a
game client:

- a persistent **library** — pinned Butterscotch games plus folders you scan yourself,
  stored in Room, with a "play next" panel and one-tap launch;
- **folder scanning** with `ADD GAMES FOLDER`: SAF read grants survive restarts, re-scans
  upsert by stable id, and unsupported folders are reported with a reason;
- **game details**: engine, WAD version, location, explicit pin linking, user-picked cover
  art downsampled to 512 px, and a Remove that never touches the game folder;
- **offline RPG Maker MV/MZ** through [ADR-0007](docs/adr/0007-webview-offline-assets.md):
  `WebViewAssetLoader` serves the SAF folder under `appassets.androidplatform.net`, with no
  `INTERNET` permission, no localhost server and no copy of the game;
- **detection-only statuses** for Ren'Py, LÖVE and ScummVM, with the reason shown in the UI;
- a **recents** row, an **app drawer** with search, and the Milestone 0 `data.win` check.

The MV/MZ route and the M1 flows are **code-complete but device-unverified** in this build:
MT-21…MT-34 have not been run on hardware. Encrypted `.rpgmvp`/`.rpgmvo` assets are not
decrypted, save export is deferred, and no compatibility numbers are claimed — see
[COMPATIBILITY.md](COMPATIBILITY.md). Run `tools/device_checks.sh` from a workstation with
adb to verify them.

## Downloads

The signed `0.4.0` APK is
[`indiehost-0.4.0.apk`](https://github.com/devx1626/indiehost/releases/download/v0.4.0/indiehost-0.4.0.apk)
(SHA-256 `2793197bbd6bca752e78a269485b797d3950c6572cee4efdba64fc3bbfe23c78`).
Older signed builds are on the [Releases page](https://github.com/devx1626/indiehost/releases).
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
| [COMPATIBILITY.md](COMPATIBILITY.md) | Device results template; currently no measured data |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Build, style, commit and release workflow |
| [LICENSING.md](LICENSING.md) | AGPL boundary, game-data policy, third-party licences |
| [SECURITY.md](SECURITY.md) | Permissions, exported components, threat model |
| [AGENTS.md](AGENTS.md) | Operating rules for AI coding agents |
| [.opencode/SKILLS.md](.opencode/SKILLS.md) | Project-local agent skill set and provenance |
| [docs/adr](docs/adr/README.md) | Architecture decision records |
| [docs/ui-ux-research.md](docs/ui-ux-research.md) | Licence-safe UI/UX findings from DroidDeck, Cinnamon and COSMIC |
| [docs/research/repository-survey-2026-11-27.md](docs/research/repository-survey-2026-11-27.md) | Full ten-domain prior-art survey (UI, library, scan, security, testing, CI) |
| [docs/research/future-emulation-boundary.md](docs/research/future-emulation-boundary.md) | Why PC-game emulation stays research-only under current hard rules |
| [docs/roadmap/ui-ux-completion.md](docs/roadmap/ui-ux-completion.md) | Phased roadmap to finish UI/UX |

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

Milestone 1 adds a second source: scan a folder you own, then link the matching pin from
the game's detail panel. IndieHost stores only metadata and your chosen cover art; scanning
never copies or modifies game files. Milestone 2 adds a third route: a scanned RPG Maker
MV/MZ game starts directly in IndieHost's WebView, with no pin and no network permission.

## Modules

- `:app` — Compose home screen, drawer, SAF scan, covers and the offline WebView activity
- `:library` — `Game` model, repository seam and Room persistence
- `:detect` — pure Kotlin `GameFolder` seam, engine fingerprinting and WAD parsing
- `:runtimes` — adapter registry plus GameMaker and RPG Maker adapters

## Build

Requires JDK 17 and Android SDK 34. Full setup, including the device flow, is in
[PREREQUISITES.md](PREREQUISITES.md).

```bash
gradle wrapper --gradle-version 8.7   # once: regenerates gradle/wrapper/gradle-wrapper.jar
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :detect:test                # engine detection tests
python3 tools/make_icons.py           # re-render the legacy launcher PNGs
tools/device_checks.sh --help         # device tests/measurements from a workstation with adb
```

The Gradle wrapper's binary jar is not committed; Android Studio regenerates it when the
project is opened, otherwise the first command above does it.

## Game files

Game data is copyrighted and is **never** committed, packaged or uploaded. `games/`,
`*.win`, `*.droid`, `*.love`, `*.rpy*`, `*.ogg` and friends are all in `.gitignore`.
You point the app at your own copy.

## Licence

IndieHost is [MIT licensed](LICENSE). Butterscotch is AGPL-3.0; this project only launches
the installed app and does not link its native code, so no AGPL obligation attaches to this
repository. Forking or bundling Butterscotch would change that. Game data is never included;
see [LICENSING.md](LICENSING.md).
