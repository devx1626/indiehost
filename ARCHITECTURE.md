# Architecture

> **Status:** Living document · **Last updated:** 2026-11-25
> **Related:** [spec.md](spec.md) · [DEVELOPMENT](DEVELOPMENT.md) · [TESTING](TESTING.md) · [ADR index](docs/adr/README.md)

How IndieHost is put together today, why the seams are where they are, and what to touch
when adding an engine. The normative requirements live in [spec.md](spec.md); this
document explains the implementation.

---

## 1. Context

IndieHost is a home app that starts games owned by other apps and hosts a few engines
itself. It has no backend, no account and no network permission.

```
┌──────────────┐   HOME role, LauncherApps   ┌──────────────────────┐
│   Android    │◀───────────────────────────▶│      IndieHost       │
│   system     │   shortcuts, usage log      │  (this repository)   │
└──────┬───────┘                             └──────────┬───────────┘
       │                                                │
       │ startShortcut / startMainActivity              │ WebView (M2)
       ▼                                                ▼
┌──────────────┐                                 ┌──────────────┐
│ Butterscotch │                                 │ RPG Maker MV │
│ (separate,   │                                 │ / MZ game    │
│  AGPL-3.0)   │                                 │ (local files)│
└──────────────┘                                 └──────────────┘
```

The only contract with Butterscotch is Android's shortcut API. There is no shared code,
no AIDL, no ContentProvider and no Intent that carries a folder.

---

## 2. Modules

```
:app
 ├── :runtimes
 │    ├── :library
 │    │    └── :detect
 │    └── :detect
 ├── :library
 └── :detect
```

| Module | Type | Responsibility | Android APIs? |
| --- | --- | --- | --- |
| `:app` | Android application | Compose UI, activities, system data sources, state wiring | Yes |
| `:library` | Android library | `Game` model, `GameRepository` seam | Minimal (none today) |
| `:detect` | Pure Kotlin/JVM | Engine fingerprinting, GameMaker WAD parsing | **No** |
| `:runtimes` | Android library | `RuntimeAdapter` contract, GameMaker/Butterscotch implementation | Yes |

Dependency direction is one-way: `:app` → `:runtimes` → `:library` → `:detect`. `:detect`
must stay free of Android imports so it can be unit-tested on the host.

### 2.1 Package layout

```
app/src/main/kotlin/com/indiehost/launcher/
  MainActivity.kt        state owner, lifecycle, launch/settings actions
  HomeUi.kt              HomeScreen, AppDrawer, tiles, panels, checker UI
  SteamTheme.kt          palette, typography, MaterialTheme wrapper
  InstalledApps.kt       LauncherApps -> LauncherApp, startMainActivity
  Recents.kt             usage-access + SharedPreferences recents
  PinShortcutActivity.kt CONFIRM_PIN_SHORTCUT receiver

library/src/main/kotlin/com/indiehost/library/
  Game.kt                data class
  GameRepository.kt      interface + InMemoryGameRepository

detect/src/main/kotlin/com/indiehost/detect/
  Engine.kt              enum + display names
  EngineDetector.kt      folder fingerprinting
  GameMakerWad.kt        FORM/GEN8 reader, CODE-chunk walk

runtimes/src/main/kotlin/com/indiehost/runtimes/
  RuntimeAdapter.kt      contract, PreparedGame, LaunchOutcome
  GameMakerRuntimeAdapter.kt
  ButterscotchShortcuts.kt
```

---

## 3. Lifecycle and startup

`MainActivity` is a `singleTask` home activity. It owns all UI state; there is no
ViewModel, repository injection or DI framework.

```
onCreate
  ├─ refreshGames()          // synchronous: hasShortcutHostPermission + getShortcuts
  ├─ loadApps()              // background Thread: InstalledApps.load + Recents.pick
  └─ setContent { SteamTheme { HomeScreen | AppDrawer } }

onResume
  ├─ refreshGames()          // pins may have changed in Butterscotch
  └─ if appsLoaded refreshRecents() else loadApps()

onNewIntent
  └─ drawerOpen = false      // pressing Home while already here returns home
```

`appsLoaded` and `loading` are `@Volatile` guards so `onResume` does not start a second
loader thread. The app list is loaded once per process; recents are recomputed on resume.

---

## 4. Key flows

### 4.1 Pin a game

```
Butterscotch                Android                    IndieHost
     │                         │                          │
     │ requestPinShortcut      │                          │
     ├────────────────────────▶│                          │
     │                         │ CONFIRM_PIN_SHORTCUT     │
     │                         ├─────────────────────────▶│ PinShortcutActivity
     │                         │                          │ getPinItemRequest
     │                         │                          │ accept()
     │                         │◀─────────────────────────┤ finish()
     │                         │ record pin against       │
     │                         │ IndieHost (home app)     │
     │                         │                          │
     │                         │        onResume          │
     │                         │◀─────────────────────────┤ refreshGames()
     │                         │ getShortcuts(pinned)     │
     │                         ├─────────────────────────▶│ library tile
```

The shortcut id must start with `game-`; everything else Butterscotch may pin is ignored
by `ButterscotchShortcuts.toRunnerShortcut()`.

### 4.2 Launch a game

```
tap tile
  → MainActivity.launchGame(shortcut)
  → GameMakerRuntimeAdapter.launchShortcut(activity, shortcut)
  → ButterscotchShortcuts.start(shortcut)
  → LauncherApps.startShortcut(info, null, null)
  → Android starts Butterscotch's GameActivity in its own process
```

`LaunchOutcome.Launched` clears the status line; `Blocked` shows the reason. No game data
crosses the process boundary.

### 4.3 Drawer load

```
MainActivity.loadApps() [main]
  → Thread {
        InstalledApps.load(context)        // LauncherApps.getActivityList
        Recents.hasUsageAccess(context)    // AppOps
        Recents.pick(context, apps, access)
        update Compose state
    }
```

`InstalledApps.load` filters out IndieHost itself, keeps one activity per package, sorts by
lowercased label and converts icons to `ImageBitmap` (max 256 px, no support-library
helper).

### 4.4 Recents merge

```
system recency (usage access, 3-day window)
  → map package -> latest RESUMED timestamp
  → sort newest first
  → append locally remembered launches (newest first)
  → resolve against the drawer list (label + icon)
  → take 8
```

Without usage access the system part is empty and the row is just local launches. Local
launches are stored in `SharedPreferences("indiehost.recents")` as newline-separated
package names, capped at 12.

### 4.5 `data.win` check

```
OpenDocument picker
  → worker thread: copy content:// stream to cacheDir/import/data.win
  → worker thread: EngineDetector.detect(importDir)
  → report: engine, runnable, WAD version, blocker, notes
  → delete cacheDir/import
```

The picker callback starts a plain worker `Thread`; the UI shows `CHECKING...` and stays
interactive. The cache copy is deleted in a `finally`, so a failed check does not leave a
large game file behind. Results are written to Compose state from the worker; see §6.2.

---

## 5. Contracts

### 5.1 `RuntimeAdapter`

```kotlin
interface RuntimeAdapter {
    val engine: Engine
    fun canRun(folder: File): Boolean
    fun prepare(folder: File, game: Game): PreparedGame
    fun launch(activity: Activity, game: Game): LaunchOutcome
    fun supportsSaves(): Boolean
}
```

| Method | Intended semantics | Used today? |
| --- | --- | --- |
| `canRun` | Cheap pre-flight on a folder | No — `EngineDetector` is called directly by the checker |
| `prepare` | Validate import, return the data file and notes; never copy copyrighted data | No |
| `launch` | Start the runtime for a `Game` | Yes — the "BUTTERSCOTCH" button |
| `supportsSaves` | Whether the adapter manages saves | No |

`GameMakerRuntimeAdapter` adds two methods outside the interface:

- `canListShortcuts(context)` — home-role check.
- `shortcuts(context)` / `launchShortcut(activity, shortcut)` — the pinned-shortcut route.

**There is no adapter registry yet.** The comment in `RuntimeAdapter.kt` promises "one line
in the adapter registry"; that registry does not exist. M2 should introduce one
(`Map<Engine, RuntimeAdapter>`) before the second adapter lands.

### 5.2 Results

```kotlin
data class PreparedGame(val dataFile: File?, val wadVersion: Int?, val notes: List<String>)

sealed interface LaunchOutcome {
    data class Launched(val packageName: String) : LaunchOutcome
    data class RuntimeNotInstalled(val packageName: String, val installUrl: String) : LaunchOutcome
    data class Blocked(val reason: String) : LaunchOutcome
}
```

`Blocked` is for "we understood the request and refuse"; `RuntimeNotInstalled` is for "the
other app is missing". Do not use exceptions for expected launch failures.

### 5.3 `GameRepository`

```kotlin
interface GameRepository {
    fun all(): List<Game>
    fun find(id: String): Game?
    fun add(game: Game)
    fun remove(id: String)
}
```

`InMemoryGameRepository` is a placeholder. Nothing in `:app` uses it. M1 replaces it with a
Room-backed implementation and wires it into the home screen.

### 5.4 `EngineDetector.Detection`

```kotlin
data class Detection(
    val engine: Engine,
    val runnable: Boolean,
    val wadVersion: Int? = null,
    val blocker: String? = null,
    val notes: List<String> = emptyList(),
)
```

Detection order: GameMaker → RPG Maker → Ren'Py → LOVE → ScummVM. The first match wins;
`UNKNOWN` means no signature. `runnable = false` must always carry a human-readable
`blocker`.

### 5.5 `GameMakerWad`

- Requires `FORM` at 0 and `GEN8` at 8.
- Reads the bytecode version at absolute offset 17; accepts 1–30, then checks 8–17.
- Walks top-level chunks from offset 8, lengths include the 8-byte header, aligned to 4,
  capped at 512 chunks.
- `hasCodeChunk = false` means YYC/GMRT.

---

## 6. State and threading

### 6.1 State ownership

All state is `mutableStateOf` fields on `MainActivity`:

| Field | Type | Written from |
| --- | --- | --- |
| `games` | `List<RunnerShortcut>` | main (`refreshGames`) |
| `canListGames` | `Boolean` | main |
| `apps` | `List<LauncherApp>` | loader thread |
| `recents` | `List<LauncherApp>` | loader thread |
| `usageAccess` | `Boolean` | loader thread |
| `loadingApps` | `Boolean` | loader thread |
| `drawerOpen` | `Boolean` | main |
| `status` | `String` | main |

Compose snapshot state is safe to write from a background thread, but recomposition is
scheduled on the main thread. A future refactor should move the loader into a ViewModel and
post results to `Dispatchers.Main`; today the code relies on snapshot semantics.

Process death resets everything: the drawer closes, the status clears, the checker report
disappears. That is acceptable for a launcher, but it means `drawerOpen` is not persisted.

### 6.2 Threading rules

- **Main thread:** Compose, `refreshGames`, `startActivity`, `startShortcut`.
- **Background `Thread`:** `InstalledApps.load`, `Recents.pick`,
  `Recents.systemRecency`, the `data.win` copy/detect. The checker writes its
  result to Compose state from that thread; recomposition stays on the main thread.
- **No coroutines, no `ExecutorService`, no WorkManager.** Keep it simple until a task
  actually needs cancellation or lifecycle awareness.

### 6.3 Refresh triggers

- `onCreate` — first load.
- `onResume` — games always; recents/apps depending on `appsLoaded`.
- `PinShortcutActivity` finish — the home activity resumes and refreshes games.
- No broadcast receiver, no `LauncherApps.Callback`, no polling. If a pin is made while
  IndieHost is not visible, `onResume` catches it.

---

## 7. Persistence

| Data | Where | Lifetime |
| --- | --- | --- |
| Pinned games | Butterscotch + Android shortcut service | Owned by the system; read-only for us |
| Game files / saves | Butterscotch's private storage | Owned by Butterscotch |
| Recents (local) | `SharedPreferences("indiehost.recents")` | Survives process death; cleared on uninstall |
| Checker cache | `cacheDir/import/data.win` | Deleted before and after each check |
| Game library | None today | `InMemoryGameRepository` is unused |

`android:allowBackup="false"` keeps the prefs and cache out of cloud backup.

M1 introduces Room for `Game` and a SAF-persisted tree URI for the games folder. Saves for
IndieHost-hosted engines must live outside private storage; Butterscotch-owned saves cannot
be relocated by us and must be documented as such.

---

## 8. Security boundaries

| Boundary | Current state |
| --- | --- |
| `MainActivity` | Exported, HOME + LAUNCHER filters, `singleTask`, `excludeFromRecents` — intended |
| `PinShortcutActivity` | Exported, translucent, `noHistory`; accepts only Butterscotch `game-` pins |
| Permissions | `PACKAGE_USAGE_STATS` only (app-op); no `INTERNET`; no runtime permissions |
| Package visibility | `<queries>` for Butterscotch and `MAIN`/`LAUNCHER` |
| Game data | Never copied out of Butterscotch; checker cache is local and transient |
| Backup | Disabled |

See [SECURITY.md](SECURITY.md) for the threat model and hardening backlog.

---

## 9. Extension guide

### 9.1 Add an engine adapter

1. Add the engine to `Engine` if it is not there.
2. Add a detector branch in `EngineDetector` with a positive and a negative test in
   `detect/src/test`.
3. Create `XxxRuntimeAdapter : RuntimeAdapter` in `:runtimes`.
4. Implement `canRun`, `prepare`, `launch`, `supportsSaves`. Return `Blocked` with a
   sentence, not an exception, when the game cannot run.
5. Introduce/register it in the adapter registry (create one if this is the second
   adapter).
6. Wire the UI: the home screen needs a tile/row and a launch path.
7. Update [spec.md](spec.md) §3, [ROADMAP.md](ROADMAP.md), and add an ADR if the runtime
   model is non-obvious.
8. Add a manual test case to [TESTING.md](TESTING.md).

Keep Android APIs in `:runtimes`/`:app`; detection stays pure.

### 9.2 Replace the repository

1. Implement `GameRepository` with Room in `:library` (entities, DAO, database).
2. Add KSP/kapt and the Room dependencies to `library/build.gradle.kts`.
3. Add DAO tests under `library/src/test` or `androidTest` (Robolectric or instrumented).
4. Wire `MainActivity` to the repository; remove `InMemoryGameRepository` once unused.
5. Update the schema/migration policy in the spec.

### 9.3 Add a screen

- Add a composable in `HomeUi.kt` (or a new file) and a branch in `MainActivity`'s
  `setContent`.
- Handle Back explicitly; the home screen should not exit the app.
- Keep the screen built from system data or the repository; avoid hidden mutable state.

---

## 10. Known technical debt

| Item | Location | Impact | Tracked |
| --- | --- | --- | --- |
| No adapter registry | `runtimes/RuntimeAdapter.kt` comment | Extension friction; second adapter will force it | M2 |
| `GameRepository` / `canRun` / `prepare` / `supportsSaves` unused | `:library`, `:runtimes` | Misleading seam; dead code | M1 |
| Missing `mipmap-xxxhdpi` | `app/src/main/res` | Pre-26 icon upscaled on xxxhdpi | Backlog |
| Deprecated `PackageManager` calls | `InstalledApps.kt` | Future API removal | Backlog |
| No ViewModel / coroutines | `MainActivity.kt` | Harder to test; snapshot writes from threads | Backlog |
| No instrumented tests | repo-wide | System integrations untested | M1 |
| No `LICENSE` file | root | Default copyright; cannot be reused | Before release |
| `-steam` codename | `build.gradle.kts`, `SteamTheme.kt` | Trademark/branding risk (CON-07) | Before release |

---

## 11. Design decisions

| ADR | Decision |
| --- | --- |
| [0001](docs/adr/0001-shortcut-handoff.md) | Hand games to Butterscotch through pinned shortcuts |
| [0002](docs/adr/0002-no-butterscotch-fork.md) | Never fork or link Butterscotch |
| [0003](docs/adr/0003-module-boundaries.md) | Four modules with a pure-Kotlin detector |
| [0004](docs/adr/0004-launcherapps-and-usage-access.md) | `LauncherApps` for apps/shortcuts; usage access optional |
| [0005](docs/adr/0005-compose-material3.md) | Compose + Material3 with a custom palette |
| [0006](docs/adr/0006-room-in-milestone-1.md) | Room for the M1 library; prefs only for recents |

---

## 12. Build and versioning

- Single Gradle build, four modules, version catalog absent (versions are inline).
- `applicationId = "com.indiehost.launcher"` (placeholder), `versionCode = 3`,
  `versionName = "0.3.0-steam"`.
- Debug build only; no release signing.
- The Gradle wrapper jar is not committed; see [PREREQUISITES](PREREQUISITES.md).
- Launcher icons: adaptive XML for API 26+, generated PNGs for mdpi–xxhdpi. The generator
  also emits xxxhdpi, which is not committed yet.
