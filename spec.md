# IndieHost Specification

> **Status:** Living document · **Version:** 1.0 · **Last updated:** 2026-11-25
> **Applies to:** `0.3.0-steam` (versionCode 3)
> **Related:** [README](README.md) · [MILESTONE-0](MILESTONE-0.md) · [ARCHITECTURE](ARCHITECTURE.md) · [ROADMAP](ROADMAP.md) · [TESTING](TESTING.md) · [LICENSING](LICENSING.md) · [SECURITY](SECURITY.md)

This document is normative. If the code and this document disagree, the code is wrong
unless both are updated in the same change. Requirements carry stable IDs (`FR-`, `NFR-`,
`CON-`) so the roadmap, tests and ADRs can refer to them.

---

## 1. Purpose

IndieHost is an Android home-screen launcher that runs indie PC games natively by
re-hosting their engine. It does not emulate Windows, translate x86, or bundle a
proprietary runner. It fingerprints a game folder, chooses a runtime per engine, and
starts the game.

The launcher is also the product surface: a fast, quiet home screen with a game library,
recents and an app drawer, styled like a game client rather than a stock launcher.

### 1.1 Product principles

1. **Native, not emulated.** Only engines with portable bytecode or an HTML5/script
   runtime are supported. No Wine, no x86 translation, no Windows container.
2. **Launcher first.** IndieHost must be a usable daily home screen even with zero games
   installed, no usage access, and no runtime app.
3. **The user supplies the games.** Game data is copyrighted, stays on the device, and is
   never committed, packaged, uploaded or backed up by IndieHost.
4. **Licence isolation.** AGPL runtimes such as Butterscotch run as separate apps. IndieHost
   never forks, links or repackages them.
5. **Weak hardware is the target.** The baseline is a MediaTek Helio A22 (4× Cortex-A53,
   PowerVR GE8300). A feature that only works on a flagship is not done.
6. **Measure, then claim.** Performance and compatibility statements must be backed by a
   device test recorded in the compatibility matrix. Milestone 0's frame rate is not yet
   measured.

---

## 2. Scope

### 2.1 Current release — `0.3.0-steam`

Shipped and expected to work:

- Home-app role with a Compose home screen.
- Library built from Butterscotch's pinned `game-` shortcuts.
- One-tap game launch through `LauncherApps.startShortcut`.
- Recents from usage access when granted, plus locally remembered launches.
- App drawer with search and long-press app info.
- The Milestone 0 `data.win` checker (engine, WAD version, blocker).
- Pin-request acceptance so Butterscotch games can be pinned to IndieHost.

### 2.2 Planned releases

| Release | Theme | Requirements |
| --- | --- | --- |
| M1 | Real library | FR-17, FR-18, FR-19, FR-20 |
| M2 | RPG Maker MV/MZ WebView runtime | FR-21, FR-22, FR-23 |
| M3 | Ren'Py, LOVE and ScummVM | FR-24 |
| M4 | Compatibility matrix and performance | FR-25 |

See [ROADMAP.md](ROADMAP.md) for exit criteria.

### 2.3 Non-goals

- Unity, Godot and Unreal. They ship per-platform native binaries; there is nothing
  portable to re-host.
- Wine, Box86/Box64, x86 translation, Windows emulation.
- YYC and GMRT GameMaker builds. They contain native code and no `CODE` chunk.
- Distributing, downloading or sourcing game data.
- A store, accounts, cloud saves, telemetry or any network service.
- A general-purpose Android launcher replacement for power users (widgets, icon packs,
  gesture configuration). The home screen is deliberately opinionated.

---

## 3. Supported engines

| Engine | Detection signature | Runtime | Hard constraint | Status |
| --- | --- | --- | --- | --- |
| GameMaker VM | `data.win`, `game.droid` or `game.unx` with `FORM`/`GEN8`, a `CODE` chunk, WAD 8–17 | Butterscotch, installed separately; launched through its pinned shortcut | VM builds only. No YYC/GMRT. WAD outside 8–17 is rejected. | **Implemented** |
| RPG Maker MV | `index.html` plus `js/rpg_core.js`, in the folder root or `www/` | IndieHost-hosted WebView | Offline only; no remote content | Planned (M2) |
| RPG Maker MZ | `index.html` plus `js/rmmz_core.js`, in the root or `www/` | IndieHost-hosted WebView | Offline only; no remote content | Planned (M2) |
| Ren'Py | `game/*.rpy` or `game/*.rpyc`, or a `renpy/` directory | Official Android runtime (RAPT) | Runtime model unresolved: RAPT produces a per-game APK, not a shared runtime | Detection only |
| LOVE | Any `*.love` package in the folder | Official Android build | Runtime model unresolved: the official build is per-game | Detection only |
| ScummVM | A `*.scummvm` marker within two directory levels | ScummVM Android app | Launch route to be researched | Detection only |

### 3.1 Why GameMaker first

GameMaker VM builds are the only case where a permissively licensed, from-scratch runner
(Butterscotch, AGPL-3.0) already exists, targets Android, and supports the exact bytecode
range of the reference game (Undertale 1.08, WAD 16). It is also the riskiest case for CPU
performance, so proving it first de-risks the whole thesis.

### 3.2 Runtime model caveat for Ren'Py and LOVE

RAPT and the official LOVE Android build package a game into an APK; they are build tools,
not a generic installed runtime that accepts a folder. M3 must decide, per engine, between:

- detecting an already-installed per-game APK and launching it, or
- orchestrating a build/install flow (likely out of scope on a phone), or
- marking the engine "detected but not hostable" with an explicit reason.

The decision must be recorded in an ADR before implementation. The same applies to
ScummVM, whose Android app may or may not expose a public launch intent.

---

## 4. Functional requirements

Status values: **Implemented**, **Partial**, **Planned**, **Not started**.

### 4.1 Home and library

| ID | Requirement | Status | Where |
| --- | --- | --- | --- |
| FR-01 | IndieHost declares `CATEGORY_HOME` and can be selected as the device home app. Pressing Home returns to it without reloading. | Implemented | `AndroidManifest.xml`, `MainActivity` |
| FR-02 | When IndieHost is the home app, it lists Butterscotch's pinned shortcuts whose id starts with `game-`, sorted by title, and draws their icons. | Implemented | `ButterscotchShortcuts`, `HomeScreen` |
| FR-03 | Tapping a library tile starts the pinned game through `LauncherApps.startShortcut`; the game runs in Butterscotch's own process. | Implemented | `GameMakerRuntimeAdapter.launchShortcut` |
| FR-04 | When IndieHost is not the home app, the library shows a setup panel and a button that opens the home-app settings. | Implemented | `HomeScreen`, `MainActivity.openHomeSettings` |
| FR-05 | With no pinned games, the library shows a short explanation of Butterscotch's "Add Shortcut to Home Screen" flow. | Implemented | `HomeScreen` |
| FR-06 | The home screen refreshes the game list on every resume, so pins made in Butterscotch appear without a restart. | Implemented | `MainActivity.onResume` |

### 4.2 Recents

| ID | Requirement | Status | Where |
| --- | --- | --- | --- |
| FR-07 | Without usage access, recents shows apps launched from IndieHost, most recent first, capped at 8. | Implemented | `Recents` |
| FR-08 | With usage access granted, recents merges the system's foreground events from the last 3 days with locally remembered launches. | Implemented | `Recents.pick` |
| FR-09 | The home screen explains the usage-access trade-off and links to the system setting. | Implemented | `HomeScreen` |

### 4.3 App drawer

| ID | Requirement | Status | Where |
| --- | --- | --- | --- |
| FR-10 | The drawer lists every launchable activity from `LauncherApps`, one tile per package, excluding IndieHost itself. | Implemented | `InstalledApps.load` |
| FR-11 | The drawer has a case-insensitive search over label and package name. | Implemented | `AppDrawer` |
| FR-12 | Tapping a tile starts the app through `LauncherApps.startMainActivity`; long-pressing opens its system app-info page. | Implemented | `InstalledApps.launch`, `MainActivity.openAppInfo` |
| FR-13 | Back closes the drawer; on the home screen, Back does nothing. | Implemented | `BackHandler` |

### 4.4 Tools

| ID | Requirement | Status | Where |
| --- | --- | --- | --- |
| FR-14 | "CHECK DATA.WIN" lets the user pick a file, copies it to the cache on a worker thread, and reports engine, runnable status, WAD version and blocker. | Implemented | `HomeScreen`, `EngineDetector` |
| FR-15 | "BUTTERSCOTCH" opens the runner app, or explains how to install it when missing. | Implemented | `GameMakerRuntimeAdapter.launch` |
| FR-16 | IndieHost accepts `CONFIRM_PIN_SHORTCUT` requests from Butterscotch for `game-` shortcuts so a pin made while it is the home app is recorded against it. Other pin requests are declined. | Implemented | `PinShortcutActivity` |

### 4.5 Milestone 1 — real library

| ID | Requirement | Status |
| --- | --- | --- |
| FR-17 | Games are persisted across process death and reinstall of the launcher using Room, not an in-memory map. | Planned (M1) |
| FR-18 | The user can point IndieHost at a games folder (SAF tree) and scan it for supported engines. | Planned (M1) |
| FR-19 | Each game has user-visible title, engine, WAD version (when applicable) and optional cover art. | Planned (M1) |
| FR-20 | For engines IndieHost hosts itself, saves live outside the app's private storage and survive a launcher reinstall. Butterscotch-owned saves remain Butterscotch's responsibility and are documented as such. | Planned (M1) |

### 4.6 Milestone 2 — RPG Maker WebView

| ID | Requirement | Status |
| --- | --- | --- |
| FR-21 | An MV/MZ folder detected by `EngineDetector` can be launched inside IndieHost in a WebView, fully offline. | Planned (M2) |
| FR-22 | The WebView blocks remote content by default; a game that requires a network request fails visibly rather than silently reaching the internet. | Planned (M2) |
| FR-23 | Back, pause/resume and audio focus behave like a game, not like a browser page. | Planned (M2) |

### 4.7 Milestone 3 — remaining engines

| ID | Requirement | Status |
| --- | --- | --- |
| FR-24 | Ren'Py, LOVE and ScummVM folders are either launched by a documented route or marked "detected, not hostable" with a reason shown in the UI. No engine may be silently ignored. | Planned (M3) |

### 4.8 Milestone 4 — compatibility

| ID | Requirement | Status |
| --- | --- | --- |
| FR-25 | A compatibility matrix records device, Android version, RAM, engine, game, WAD, measured fps, audio quality and notes. | Planned (M4) |

---

## 5. Non-functional requirements

| ID | Requirement | Status / target |
| --- | --- | --- |
| NFR-01 | **Cold start** to first frame on the Helio A22 baseline: ≤ 2.0 s. | Target, unmeasured |
| NFR-02 | **Warm resume** (Home key while already running): ≤ 300 ms to the home screen. | Target, unmeasured |
| NFR-03 | **Drawer readiness** for ~100 apps: ≤ 1.5 s, loaded off the main thread. | Target, unmeasured |
| NFR-04 | **Game performance** bar from Milestone 0: ≥ 30 fps sustained in Undertale's Ruins walk and a battle, with no audio crackle. | Acceptance bar, not yet measured |
| NFR-05 | **No ANR.** No file copy, fingerprinting, package query or usage-log scan on the main thread. | Implemented |
| NFR-06 | **Offline by construction.** No `INTERNET` permission and no network calls in the launcher. | Implemented |
| NFR-07 | **Privacy.** Usage data and game paths stay on the device. No analytics, accounts or telemetry. | Implemented |
| NFR-08 | **Permission resilience.** Revoking usage access, changing the home app, uninstalling Butterscotch or a stale shortcut must produce a clear message, never a crash. | Partial |
| NFR-09 | **API range.** `minSdk 24`, `targetSdk 34`, `compileSdk 34`. Shortcut features degrade cleanly below API 25/26. | Implemented |
| NFR-10 | **Accessibility.** Icons have content descriptions; text contrast is readable; touch targets should be ≥ 48 dp. | Partial — several buttons are 36–38 dp |
| NFR-11 | **Localisation.** User-visible strings live in resources. | Not started — most copy is hardcoded in Compose |
| NFR-12 | **Testability.** `:detect` is pure JVM and covered by host tests; Android integration is manually tested on device. | Partial — 12 JVM tests, no instrumented tests |

---

## 6. Hard constraints

These are not trade-offs. A change that violates one is rejected.

| ID | Constraint |
| --- | --- |
| CON-01 | **Never fork, link, bundle or repackage Butterscotch.** IndieHost may start its exported activities and its pinned shortcuts, nothing else. Doing more makes the combined work AGPL-3.0 and breaks the licence story. |
| CON-02 | **Game data never enters the repository, APK, backup or any upload.** This includes `data.win`, `game.droid`, `*.love`, `*.rpy*`, `*.ogg`, save files and cover art extracted from a game. `.gitignore` and `android:allowBackup="false"` enforce the mechanical part; review enforces the rest. |
| CON-03 | **No network permission, no telemetry, no remote config.** A future engine that needs the network requires an explicit spec change and a user-visible consent flow. |
| CON-04 | **No emulation.** No Wine, no x86 translation, no Windows container, no proprietary runner redistribution. |
| CON-05 | **GameMaker support is VM-only.** A build without a `CODE` chunk is reported as blocked, never attempted. |
| CON-06 | **The launcher works with zero games.** A missing runtime, empty library or denied usage access must leave a usable home screen, not a setup wall. |
| CON-07 | **No Steam artwork or trademarks in shipped branding.** The palette is inspired by a game-client look; the `-steam` suffix and `SteamTheme` class are internal codenames and must be renamed before a public release. |

---

## 7. UX specification

### 7.1 Screen map

```
Home ──┬── ALL APPS ──▶ App drawer ──▶ app
       ├── PLAY / tile ──▶ Butterscotch game
       ├── CHOOSE HOME APP ──▶ system home-app settings
       ├── ALLOW USAGE ACCESS ──▶ system usage-access settings
       ├── BUTTERSCOTCH ──▶ runner app (or install message)
       └── CHECK DATA.WIN ──▶ file picker ──▶ report panel
```

### 7.2 Home layout

Top to bottom, inside a `LazyColumn`:

1. **Client bar** — brand mark, `INDIEHOST / HOME`, `ALL APPS` button.
2. **Hero panel** — "PLAY NEXT" for the first pinned game, or the setup step that unlocks
   the library. 168 dp tall, gradient, oversized ghost initial.
3. **RECENT** — horizontal row of up to 8 app tiles, or a loading/empty/usage-access state.
4. **YOUR LIBRARY** — setup panel, empty panel, or one row per pinned game.
5. **Status panel** — transient success/error text, only when non-empty.
6. **TOOLS** — `BUTTERSCOTCH` and `CHECK DATA.WIN` buttons.
7. **Report panel** — checker output, only after a check.

### 7.3 Home states

| Condition | Hero | Library |
| --- | --- | --- |
| Not the home app (`canListGames = false`) | "Make it your home screen" + `CHOOSE HOME APP` | Setup panel with the same action |
| Home app, no pinned games | "Your library is empty" | Explanation of Butterscotch's pin flow |
| Home app, games present | First game, "PLAY NEXT", `PLAY` | One `GameRow` per game |

### 7.4 Recents states

| Condition | UI |
| --- | --- |
| Apps still loading | Spinner + "Reading your apps..." |
| Usage access denied, no local launches | Explanation + `ALLOW USAGE ACCESS` |
| Usage access granted, nothing recent | "Nothing opened recently." |
| Any entries | Horizontal tiles, newest first |

### 7.5 Drawer

- Header: brand mark, `ALL APPS`, `HOME` button.
- Search field: single line, filters on every keystroke, case-insensitive.
- Grid: adaptive columns, minimum 84 dp, icon + label.
- States: loading spinner, "No apps found.", "No app matches \"query\"."
- Long-press: system app-info page.
- Back: closes the drawer.

### 7.6 Visual language

- Dark-only. Background `#171A21`, panel `#1B2838`, card `#16202D`, edge `#2A3F5A`,
  accent `#66C0F4`, bright text `#F2F7FB`.
- Panels: 6 dp corners, 1 dp outline. Pills: 2 dp corners.
- Labels: bold, uppercase, 1.4 sp letter spacing.
- No wallpaper of our own, no widgets. The home screen stays quiet and fast.

### 7.7 Copy rules

- Say what happened and what to do next. No engine jargon in primary UI.
- "Butterscotch is not installed" is followed by the install URL.
- A blocked `data.win` explains YYC/GMRT in one sentence.
- Never blame the user for a system permission they have not granted.

### 7.8 Known UX gaps

- Most strings are hardcoded, so the app is English-only.
- Several buttons are below the 48 dp touch-target guideline.

These are tracked in [ROADMAP.md](ROADMAP.md#backlog).

---

## 8. Architecture summary

The full description is in [ARCHITECTURE.md](ARCHITECTURE.md). In short:

| Module | Responsibility |
| --- | --- |
| `:app` | Compose home screen and drawer, activity wiring, system data sources |
| `:library` | `Game` model and the `GameRepository` storage seam (Room in M1) |
| `:detect` | Pure Kotlin engine fingerprinting and GameMaker WAD parsing |
| `:runtimes` | `RuntimeAdapter` contract and the GameMaker/Butterscotch implementation |

Dependency direction: `:app` → `:runtimes` → `:library` → `:detect`. `:detect` has no
Android dependencies and is tested on the host.

### 8.1 Key contracts

```kotlin
interface RuntimeAdapter {
    val engine: Engine
    fun canRun(folder: File): Boolean
    fun prepare(folder: File, game: Game): PreparedGame
    fun launch(activity: Activity, game: Game): LaunchOutcome
    fun supportsSaves(): Boolean
}

sealed interface LaunchOutcome {
    data class Launched(val packageName: String) : LaunchOutcome
    data class RuntimeNotInstalled(val packageName: String, val installUrl: String) : LaunchOutcome
    data class Blocked(val reason: String) : LaunchOutcome
}
```

`GameRepository` is the persistence seam; `EngineDetector.Detection` is the detector's
result. `ButterscotchShortcuts` is the only code that talks to the runner's shortcuts.

---

## 9. Data model

| Type | Fields | Persistence |
| --- | --- | --- |
| `Game` | `id`, `title`, `folderPath`, `engine`, `wadVersion?` | In memory today; Room in M1 |
| `RunnerShortcut` | `id`, `title`, `info` (internal `ShortcutInfo`), `packageName` | Owned by the system/Butterscotch |
| `LauncherApp` | `packageName`, `activityName`, `label`, `icon` | Rebuilt from `LauncherApps` |
| `Detection` | `engine`, `runnable`, `wadVersion?`, `blocker?`, `notes` | Computed, not stored |
| `PreparedGame` | `dataFile?`, `wadVersion?`, `notes` | Computed, not stored |
| `LaunchOutcome` | `Launched`, `RuntimeNotInstalled`, `Blocked` | Transient |

Locally persisted data is limited to the recents list in
`SharedPreferences("indiehost.recents")` under `launches`, capped at 12 package names.
The `data.win` checker copies the chosen file to `cacheDir/import/data.win` and deletes
the directory before each check.

---

## 10. Permissions and manifest surface

| Item | Why | Notes |
| --- | --- | --- |
| `CATEGORY_HOME` | Be a home app; grants shortcut-host permission | Core product requirement |
| `CATEGORY_LAUNCHER` | Open IndieHost from another drawer | Convenience |
| `CONFIRM_PIN_SHORTCUT` activity | Accept pins from Butterscotch | Exported, translucent, no history |
| `PACKAGE_USAGE_STATS` | Read the system recents log | App-op granted in Settings, optional |
| `<queries>` Butterscotch package | Resolve its launch intent on Android 11+ | Package visibility |
| `<queries>` `MAIN`/`LAUNCHER` intent | Let the drawer see installed apps | Standard launcher visibility |
| `allowBackup="false"` | Keep game paths and recents off cloud backup | Hard constraint |

There is no `INTERNET` permission and no runtime permission request.

---

## 11. Performance budgets

All numbers are **targets on the Helio A22 baseline**, not measurements, except the
Milestone 0 game bar. Measure with the protocol in [TESTING.md](TESTING.md).

| Budget | Target |
| --- | --- |
| Cold start to first frame | ≤ 2.0 s |
| Warm resume | ≤ 300 ms |
| Drawer list ready (~100 apps) | ≤ 1.5 s |
| Recents refresh | ≤ 500 ms |
| Idle PSS | ≤ 120 MB |
| Main-thread block | < 16 ms per frame; no operation > 200 ms |
| Game | ≥ 30 fps in Ruins walk and battle, audio clean |

---

## 12. Error handling and edge cases

| Condition | Required behaviour | Current |
| --- | --- | --- |
| Butterscotch not installed | Explain and offer the Play Store URL | Implemented |
| IndieHost not the home app | Setup panel + settings button; no crash | Implemented |
| Usage access denied or revoked | Fall back to local launches; explain the setting | Implemented |
| Shortcut launch rejected or stale | Status message telling the user to re-pin | Implemented |
| Pin made while another launcher is active | Explain that pins belong to the accepting launcher and must be re-pinned | Partially documented; no in-app hint |
| Game folder unreadable / file truncated | Detector returns a blocker, never throws | Implemented |
| YYC/GMRT build | Blocked with a one-sentence explanation | Implemented |
| Very large `data.win` | Must not freeze the UI | Implemented — copied and checked on a worker thread |
| App uninstalled between load and tap | Launch returns false and shows a message | Implemented |
| Profile locked / shutting down | `LauncherApps` calls return empty, no crash | Implemented |
| Pin request from a non-Butterscotch app | Reject or ignore, do not record | Implemented — declines shortcuts outside Butterscotch's `game-` namespace |

---

## 13. Acceptance criteria

### 13.1 `0.3.0-steam`

- [ ] Installs and launches on API 24 through 34.
- [ ] Can be selected as the home app; Home returns to it with `singleTask` behaviour.
- [ ] Lists pinned `game-` shortcuts only while it is the home app.
- [ ] Tapping a game starts Butterscotch's own activity; no game data is copied.
- [ ] Drawer lists launchable apps, search filters, long-press opens app info.
- [ ] Recents works with and without usage access.
- [ ] `data.win` check reports engine, WAD version, runnable status and blocker.
- [ ] No game data in the APK or repository; `allowBackup="false"`.
- [ ] `./gradlew :detect:test` passes 12/12; `./gradlew :app:assembleDebug` succeeds.

### 13.2 Milestone 1

- [ ] Room persists games across process death and launcher reinstall.
- [ ] A SAF-selected folder can be scanned and imported.
- [ ] Cover art can be attached and survives restart.
- [ ] Hosted-engine saves survive a launcher reinstall; the Butterscotch limitation is
      documented in the UI or help text.
- [ ] DAO and detector tests pass; manual matrix updated.

### 13.3 Milestone 2

- [ ] An MV and an MZ game launch offline in a WebView.
- [ ] Back/pause/audio behave; no remote request succeeds.
- [ ] The adapter is selected through the same `RuntimeAdapter` seam as GameMaker.

### 13.4 Milestone 3

- [ ] Each remaining engine is either launchable or explicitly "detected, not hostable"
      with a reason.
- [ ] The decision is recorded in an ADR.

### 13.5 Milestone 4

- [ ] At least five GameMaker titles are measured on the baseline device with fps, audio
      and notes.
- [ ] The matrix is committed (device data only; no game files).

---

## 14. Open questions

1. **Ren'Py/LOVE runtime model.** Per-game APK vs shared runtime vs "detect and hand off".
   Needs an ADR before M3.
2. **ScummVM launch route.** Does the Android app expose a public intent for a game path?
3. **MV WebView loading.** `WebViewAssetLoader` over a SAF tree vs copying into app
   storage vs a localhost server. The first avoids duplication and the `INTERNET`
   permission; the second is simpler but doubles disk use.
4. **Save location.** Which shared directory is both scoped-storage-safe and survives
   uninstall? `Documents/IndieHost/<game>` vs app-specific external storage.
5. **Room schema and migration policy.** Version 1 schema, destructive migration allowed
   during M1 only.
6. **Application id.** `com.indiehost.launcher` is explicitly a placeholder. Renaming
   changes shortcut ownership and installed-app identity; do it before a public release.
7. **Launcher licence.** There is no `LICENSE` file. Choose one before publishing.
8. **`-steam` codename.** Rename the version suffix, `SteamTheme`, and `Steam` palette
   object before distribution (CON-07).
9. **Localisation.** Move Compose copy into `strings.xml`; decide whether M1 or backlog.
10. **Work profiles.** `LauncherApps` is queried for `Process.myUserHandle()` only. Support
    for managed profiles is out of scope until requested.

---

## 15. Glossary

| Term | Meaning |
| --- | --- |
| **WAD** | GameMaker's data-file format; the version number is the runtime/bytecode version. Butterscotch supports 8–17. |
| **YYC** | GameMaker's native-compiled output. No `CODE` chunk; cannot run on a bytecode runner. |
| **GMRT** | GameMaker's newest runtime. Native, not supported by Butterscotch. |
| **CODE chunk** | The top-level IFF chunk containing VM bytecode. Its presence distinguishes a VM build. |
| **RAPT** | Ren'Py Android Packaging Tool; builds a per-game Android APK. |
| **Shortcut host** | The current home app's permission to read and start other apps' pinned shortcuts. |
| **Pin** | A shortcut accepted by a launcher via `CONFIRM_PIN_SHORTCUT`. A pin belongs to the accepting launcher. |
| **SAF** | Storage Access Framework; the modern way to let the user choose a folder. |
