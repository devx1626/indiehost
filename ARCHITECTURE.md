# Architecture

> **Status:** Living document · **Last updated:** 2026-11-27
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
| `:app` | Android application | Compose UI, SAF folder scan, covers, offline WebView game activity, state wiring | Yes |
| `:library` | Android library | `Game` model, `GameRepository` seam, Room entity/DAO/database | Yes (Room) |
| `:detect` | Pure Kotlin/JVM | Engine fingerprinting, GameMaker WAD parsing, `GameFolder` seam | **No** |
| `:runtimes` | Android library | `RuntimeAdapter` contract, adapter registry, GameMaker and RPG Maker adapters | Yes |

Dependency direction is one-way: `:app` → `:runtimes` → `:library` → `:detect`. `:detect`
must stay free of Android imports so it can be unit-tested on the host.

### 2.1 Package layout

```
app/src/main/kotlin/com/indiehost/launcher/
  MainActivity.kt        state owner, lifecycle, launch/settings actions
  HomeUi.kt              HomeScreen, AppDrawer, library rows/detail, checker UI
  IndieTheme.kt          palette, typography, MaterialTheme wrapper
  InstalledApps.kt       LauncherApps -> LauncherApp, startMainActivity
  Recents.kt             usage-access + SharedPreferences recents
  PinShortcutActivity.kt CONFIRM_PIN_SHORTCUT receiver
  GameFolderStore.kt     persisted SAF tree URIs and read grants
  SafGameFolder.kt       DocumentFile tree through the GameFolder seam
  GameScanner.kt         pure walk, stable ids, upsert-never-delete import
  LibraryItem.kt         merge of scanned games and pinned shortcuts
  CoverStore.kt          downsample + store user-picked art in filesDir/covers
  AppSearch.kt           pure case/accent-folding drawer search rules
  CapabilityPolicy.kt / FeedbackPolicy.kt  pure gating and status urgency rules
  Contrast.kt / Motion.kt  pure WCAG ratio and reduced-motion rules
  (debug) LauncherPreviews.kt  device-less state previews, not shipped
  WebPaths.kt            pure URL/path/MIME rules for the offline WebView
  SafTreeResolver.kt     resolve a relative path inside one SAF game folder
  SafAssetPathHandler.kt WebViewAssetLoader path handler backed by SAF streams
  GameWebViewActivity.kt offline MV/MZ host; no INTERNET, no copy

library/src/main/kotlin/com/indiehost/library/
  Game.kt                data class
  GameRepository.kt      interface + InMemoryGameRepository
  GameEntity.kt          Room entity, converters, mapping
  GameDao.kt             all/find/upsert/remove
  IndieHostDatabase.kt   version 1, destructive migration in M1
  RoomGameRepository.kt  production repository

detect/src/main/kotlin/com/indiehost/detect/
  Engine.kt              enum + display names
  GameFolder.kt          pure folder seam + FileGameFolder
  EngineDetector.kt      folder fingerprinting
  GameMakerWad.kt        streamed FORM/GEN8 reader, CODE-chunk walk

runtimes/src/main/kotlin/com/indiehost/runtimes/
  RuntimeAdapter.kt      contract, PreparedGame, LaunchOutcome
  RuntimeRegistry.kt     Engine -> RuntimeAdapter, no silent fallback
  GameMakerRuntimeAdapter.kt
  RpgMakerRuntimeAdapter.kt
  ButterscotchShortcuts.kt
```

---

## 3. Lifecycle and startup

`MainActivity` is a `singleTask` home activity. It owns all UI state; there is no
ViewModel, repository injection or DI framework.

```
onCreate
  ├─ repository / folderStore / coverStore initialised
  ├─ loadLibrary()           // background Thread: Room read + cover decode
  ├─ refreshGames()          // synchronous: hasShortcutHostPermission + getShortcuts
  ├─ loadApps()              // background Thread: InstalledApps.load + Recents.pick
  └─ setContent { IndieTheme { spinner until uiReady, then HomeScreen | AppDrawer } }

onResume
  ├─ refreshGames()          // pins may have changed in Butterscotch
  ├─ loadLibrary()           // scanned rows may have changed
  ├─ checkFolderPermissions() // revoked SAF grants become a visible message
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

### 4.6 Folder scan

```
ADD GAMES FOLDER → OpenDocumentTree
  → GameFolderStore.add(uri)        // takePersistableUriPermission, remember only the URI
  → Thread "folder-scan" with a run id, progress callback and cancellation check
        for each remembered tree
          ├─ hasPermission? else: visible message, keep stored rows
          ├─ DocumentFile.fromTreeUri → SafGameFolder
          └─ GameScanner.scan(root, treeUri, existing, scanId, progress, cancelled)
               depth 0..2, max 500 entries per folder, one SAF query per folder
               detector hit → stable id "scan-" + SHA-256(treeUri|documentUri)
               unreadable folder → skipped reason, continue
        → repository.add for each import (upsert only; never delete)
        → reloadLibrary()          // Room read + cover decode on the same thread
```

A scan is a snapshot, not a synchronisation. A folder that is no longer present or no
longer recognised is left in the library; only the detail panel's **REMOVE** deletes a row.
Re-scanning the same tree keeps `addedAt`, `coverPath` and `linkedShortcutId`. A recognised
GameMaker folder is a leaf, so `www/` and `game/` internals are not imported as separate
games. Each scan has a `scanId`; touched rows get `lastSeenScanId` and `available=true`, and
rows from that tree not seen by the scan are set `available=false` (shown as MISSING) rather
than deleted. A `scan_runs` row records timestamps, counts and the failure reason.

### 4.7 Library merge and covers

```
Room games ─┐
            ├─ mergeLibrary() → pins first, then scans
pins ───────┘   linked pin is hidden behind its scanned row

tap a scanned row → detail panel
  ├─ LINK PIN      → explicit game.linkedShortcutId; no title matching
  ├─ CHOOSE COVER  → OpenDocument(image/*) → CoverStore.save → repository.add
  └─ REMOVE        → repository.remove + delete stored cover file

GameMaker without a live linked pin is not launchable; MV/MZ say "M2", and the other
detected engines say "M3". A detector blocker always wins over a linked pin.
```

`CoverStore` reads bounds first, subsamples, then scales to at most 512 px and writes WebP
to `filesDir/covers/<sha256(gameId)>.webp`. Decoding and file writes run on worker threads;
the UI receives decoded `ImageBitmap`s through Compose state.

### 4.8 Offline RPG Maker WebView

```
launchItem → RuntimeRegistry.forEngine(engine) → RpgMakerRuntimeAdapter.launch
  → startActivity(action com.indiehost.launcher.GAME_WEBVIEW, extra game id)

GameWebViewActivity.onCreate
  → Thread "webgame-load": RoomGameRepository.find(id), require MV/MZ + treeUri
  → SafTreeResolver probes www/index.html + www/js/rpg_core.js, then root layout
  → main thread: WebView settings, WebViewAssetLoader("/game/") + SafAssetPathHandler
  → loadUrl("https://appassets.androidplatform.net:<portFor(gameId)>/game/<indexPath>")

asset request under /game/ → PathHandler → DocumentsContract child lookup → InputStream
port per game id isolates DOM storage; SVG is served as octet-stream until sanitised
off-origin request/navigation → blocked response + visible message, never a network call
Back → "Leave the game?" confirmation (never goBack)
onPause/onResume → WebView.onPause/onResume + audio focus abandon/request
```

The Activity is `exported=false`, uses `configChanges` so rotation does not restart the
game, and declares no new permission. A revoked SAF grant, missing core JS or wrong engine
finishes with an `AlertDialog` explaining why. Encrypted `.rpgmvp`/`.rpgmvo` assets are
served as octet-stream and are **not** decrypted in M2.

### 4.9 Engines with no runtime

`mergeLibrary` gives Ren'Py, LÖVE and ScummVM their own `LibraryReason`, so each row says
why it cannot launch rather than sharing a generic M3 label:

| Engine | Status shown | Source |
| --- | --- | --- |
| Ren'Py | Per-game APK; install it to play | ADR-0008 |
| LÖVE | Generic player can open `.love`; handoff not wired | ADR-0009 |
| ScummVM | Add the folder inside ScummVM first | ADR-0010 |

No adapter is registered for these engines; `RuntimeRegistry.forEngine` returns null and
the UI keeps Launch disabled. Adding an adapter requires a resolved runtime model and a new
ADR, not just a detector branch.

---

## 5. Contracts

### 5.1 `RuntimeAdapter`

```kotlin
interface RuntimeAdapter {
    val engine: Engine
    fun canRun(folder: File): Boolean
    fun prepare(folder: File, game: Game): PreparedGame
    fun launch(activity: Activity, game: Game, shortcut: RunnerShortcut? = null): LaunchOutcome
    fun supportsSaves(): Boolean
}
```

| Method | Intended semantics | Used today? |
| --- | --- | --- |
| `canRun` | Cheap pre-flight on a folder | No — `EngineDetector` is called directly by the checker |
| `prepare` | Validate import, return the data file and notes; never copy copyrighted data | No |
| `launch` | Start the runtime for a `Game` | Yes — the "BUTTERSCOTCH" button |
| `supportsSaves` | Whether the adapter manages saves | No |

`RuntimeAdapter.launch(activity, game, shortcut)` now takes the linked Butterscotch pin as
an optional third argument. GameMaker uses it for the shortcut handoff; RPG Maker ignores
it. `RuntimeRegistry(adapters).forEngine(engine)` is the only routing decision in the app,
and returns null for an engine with no adapter.

`GameMakerRuntimeAdapter` adds two methods outside the interface:

- `canListShortcuts(context)` — home-role check.
- `shortcuts(context)` / `launchShortcut(activity, shortcut)` — the pinned-shortcut route.

The registry is `RuntimeRegistry`, a small wrapper over a fixed `List<RuntimeAdapter>`.
It is not a mutable map: the set of engines is a build-time decision, and a missing adapter
must be visible rather than silently replaced. `RpgMakerRuntimeAdapter` starts
`GameWebViewActivity` by a manifest action string so `:runtimes` never depends on `:app`.

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

`RoomGameRepository` is the production implementation; `InMemoryGameRepository` remains as
a test double. `MainActivity` owns the only instance. Every call is blocking and is made
from a named background `Thread` (`library-load`, `folder-scan`, `library-link`,
`cover-save`, `cover-remove`, `library-remove`), never from the main thread.

Room schema version 1 uses `fallbackToDestructiveMigration()`; that is acceptable only while
M1 is unreleased. A released schema needs explicit migrations. Enum names are stored as
text, and an unknown name maps to `Engine.UNKNOWN`. `Game` also stores `runnable`/`blocker`
from the detector so the UI does not re-read a folder just to show why Launch is disabled.

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
| `library` | `List<Game>` | library/scanner/cover worker threads |
| `covers` | `Map<String, ImageBitmap>` | library/cover worker threads |
| `scanning` | `Boolean` | main, then scan thread |
| `scanMessage` | `String` | main, then scan thread |
| `apps` | `List<LauncherApp>` | loader thread |
| `recents` | `List<LauncherApp>` | loader thread |
| `usageAccess` | `Boolean` | loader thread |
| `loadingApps` | `Boolean` | loader thread |
| `drawerOpen` | `Boolean` | main |
| `status` | `String` | main and worker threads |

Compose snapshot state is safe to write from a background thread, but recomposition is
scheduled on the main thread. A future refactor should move the loader into a ViewModel and
post results to `Dispatchers.Main`; today the code relies on snapshot semantics.

Process death resets everything: the drawer closes, the status clears, the checker report
disappears. That is acceptable for a launcher, but it means `drawerOpen` is not persisted.

### 6.2 Threading rules

- **Main thread:** Compose, `refreshGames`, `startActivity`, `startShortcut`.
- **Background `Thread`:** `InstalledApps.load`, `Recents.pick`,
  `Recents.systemRecency`, the `data.win` copy/detect, all Room calls, the SAF folder
  walk, cover decode/encode, the WebView game-row lookup and
  `WebViewAssetLoader.shouldInterceptRequest` (which WebView invokes on a worker).
  Checker, scanner and cover results are written to Compose state from those threads;
  recomposition stays on the main thread.
- **No coroutines, no `ExecutorService`, no WorkManager.** Keep it simple until a task
  actually needs cancellation or lifecycle awareness.
- **No filesystem paths in SharedPreferences.** `GameFolderStore` persists only SAF tree
  URIs; the document URIs and display paths live in Room rows.

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
| Game library | Room, `databases/indiehost.db` (version 2) | Survives process death and app updates; cleared on uninstall |
| Scan runs | Room `scan_runs` | Records id, timestamps, imported/skipped counts and error |
| Games folders | `SharedPreferences("indiehost.folders")`, SAF tree URIs + persisted read grants | Survives restart until the user revokes the grant |
| Cover art | `filesDir/covers/<sha256(gameId)>.webp` | App-private, replaced by the next pick, deleted with the row |
| MV/MZ saves | WebView DOM/localStorage in app-private `app_webview` | Survives restart; no export yet |

`android:allowBackup="false"` (API ≤30) and `res/xml/data_extraction_rules.xml` (API 31+
cloud and device transfer) keep all of this out of backup. A full uninstall
therefore loses metadata and must be restored by re-scanning the same folders; the game
files themselves are untouched. Butterscotch-owned saves cannot be relocated by us and
remain Butterscotch's responsibility. FR-20 (saves outside private storage) is **deferred
beyond M2** until the WebView runtime is device-proven; the scope change is recorded in the
M2 design and in `spec.md`.

---

## 8. Security boundaries

| Boundary | Current state |
| --- | --- |
| `MainActivity` | Exported, HOME + LAUNCHER filters, `singleTask`, `excludeFromRecents` — intended |
| `PinShortcutActivity` | Exported, translucent, `noHistory`; accepts only Butterscotch `game-` pins |
| Permissions | `PACKAGE_USAGE_STATS` only (app-op); no `INTERNET`; no runtime permissions |
| Package visibility | `<queries>` for Butterscotch and `MAIN`/`LAUNCHER` |
| Game data | Never copied out of Butterscotch; checker cache is local and transient |
| WebView | JavaScript on, no bridge, file/content access off, `blockNetworkLoads=true`, mixed content never, off-origin requests/navigation blocked, path segments/length and 64 MB asset caps, cache cleared on destroy; no `INTERNET` |
| Pin requests | `PinPolicy` requires a valid, shortcut-type `game-` request from Butterscotch; listed shortcuts must also be enabled and pinned |
| Backup | `allowBackup="false"` plus `dataExtractionRules` excluding prefs, Room and covers from cloud backup and device transfer |

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
5. Add it to the `RuntimeRegistry` list in `MainActivity` and add a test in `:runtimes`.
6. Wire the UI: the home screen needs a tile/row and a launch path.
7. Update [spec.md](spec.md) §3, [ROADMAP.md](ROADMAP.md), and add an ADR if the runtime
   model is non-obvious.
8. Add a manual test case to [TESTING.md](TESTING.md).

Keep Android APIs in `:runtimes`/`:app`; detection stays pure.

### 9.2 Change the repository or schema

Room is already implemented in `:library` (see §5.3). To change the schema:

1. Bump the version and write an explicit migration; do not extend the M1 destructive
   migration waiver.
2. Update `GameEntity`, the converters and the `Game` mapping test together.
3. Re-run the instrumented DAO test on a device when one is available.
4. Update the schema/migration policy in the spec and this document.

`InMemoryGameRepository` is retained as a test double; it is not used by `:app`.

### 9.3 Add a screen

- Add a composable in `HomeUi.kt` (or a new file) and a branch in `MainActivity`'s
  `setContent`.
- Handle Back explicitly; the home screen should not exit the app.
- Keep the screen built from system data or the repository; avoid hidden mutable state.

---

## 10. Known technical debt

| Item | Location | Impact | Tracked |
| --- | --- | --- | --- |
| `canRun` / `prepare` unused for SAF games | `:runtimes` | File-based seam cannot pre-flight a tree URI; validation happens in the Activity | M3 |
| WebView route is device-unverified | `GameWebViewActivity.kt` | Offline HTTPS without `INTERNET` may be refused before interception | Blocked on device |
| Encrypted MV/MZ assets not decrypted | `SafAssetPathHandler.kt` | Games using `.rpgmvp`/`.rpgmvo` show broken art | M3 |
| Room uses destructive migration | `IndieHostDatabase.kt` | Would drop library rows on a future schema change | Before release |
| Instrumented `GameDaoTest` never ran | `library/src/androidTest` | DAO behaviour is verified only by host mapping tests here; run `tools/device_checks.sh --dao` on a workstation | Blocked on device |
| M1/M2 manual cases never ran | `TESTING.md` §3 | SAF, covers, WebView load and Back are compile/test verified, not device verified; script + synthetic fixture provided | Blocked on device |
| Kotlin incremental cache flake | local Gradle builds | Test compilation can fail to see production top-level functions; use `-Pkotlin.incremental=false` | Local toolchain |
| Missing `mipmap-xxxhdpi` | `app/src/main/res` | Pre-26 icon upscaled on xxxhdpi | Backlog |
| No ViewModel / coroutines | `MainActivity.kt` | Harder to test; snapshot writes from threads | Backlog |

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
| [0007](docs/adr/0007-webview-offline-assets.md) | Load RPG Maker games from SAF through WebViewAssetLoader |
| [0008](docs/adr/0008-renpy-detected-not-hosted.md) | Ren'Py is detected but not hosted |
| [0009](docs/adr/0009-love-handoff-not-implemented.md) | LÖVE handoff is a candidate, not built |
| [0010](docs/adr/0010-scummvm-handoff-not-implemented.md) | ScummVM handoff needs a configured target |

---

## 12. Build and versioning

- Single Gradle build, four modules, version catalog absent (versions are inline).
- Launcher licence: MIT ([LICENSE](LICENSE)); third-party licences are in
  [LICENSING.md](LICENSING.md).
- `applicationId = "com.indiehost.launcher"` (unchanged), `versionCode = 5`,
  `versionName = "0.4.0"`.
- Debug is built by CI; release signing is opt-in through four `INDIEHOST_*` environment
  variables, and the keystore stays outside the repository.
- The Gradle wrapper jar is not committed; see [PREREQUISITES](PREREQUISITES.md).
- Launcher icons: adaptive XML for API 26+, generated PNGs for mdpi–xxhdpi. The generator
  also emits xxxhdpi, which is not committed yet.
