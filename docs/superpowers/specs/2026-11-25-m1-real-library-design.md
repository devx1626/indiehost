# M1 — Real library design

**Date:** 2026-11-25
**Status:** Approved 2026-11-27
**Requirement:** ROADMAP Milestone 1; spec §4.5, NFR-05/NFR-07/NFR-08/NFR-11

## Intent and why

M0's library is borrowed: it is only the list of Butterscotch's pinned `game-` shortcuts.
If a game is not pinned, or a pin belongs to another launcher, IndieHost has nothing to
show. M1 gives IndieHost its own persistent, scannable collection while keeping the
pinned-shortcut path as a first-class source. The result must survive process death and a
launcher updates, and a full uninstall must be recoverable by re-scanning the same folder.
It must not copy game data into the repository, an APK or a backup.

## Goals

- Store games in Room; keep `GameRepository` as the seam and add a Room implementation.
- Scan one SAF folder tree with a persisted read grant, detect engines in place, and import
  the games it finds.
- Show repository games and pinned Butterscotch shortcuts in one library, with cover art
  when the user supplies it.
- Keep every existing hard constraint, especially no `INTERNET`, no Butterscotch
  fork/link/bundle, no game data in the repo, and `:detect` pure Kotlin/JVM.

## Non-goals

- No hosted-engine saves yet: M2+ decides save locations. Butterscotch-owned saves stay
  with Butterscotch and the limitation is documented.
- No automatic cover extraction from game files; the user picks an image.
- No Ren'Py/LOVE/ScummVM runtime: M3 decides whether they are hostable.
- No ViewModel/coroutine migration: this milestone keeps the existing plain
  `Thread` + `mutableStateOf` pattern.

## Anti-goals

- Turning "detected" into "launchable" without a real runtime. A scanned GameMaker game
  that is not pinned in Butterscotch must say so, not pretend to launch.
- Silently dropping a folder because a permission was revoked, a document vanished or a
  detector threw.

## Constraints

- `minSdk 24`, `targetSdk 34`, `compileSdk 34`; AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7.
- `:detect` is pure Kotlin/JVM: no Android imports, host-testable with JUnit 4.
- `:library` holds persistence only: no UI, no system-service calls.
- No new network permission or network call; no game data committed.
- New dependencies need a licence entry in `LICENSING.md`.

## Approaches considered

1. **Temporary copy of the signature file.** Copy `data.win` into the cache, run the
   current `EngineDetector(File)`, delete the copy, store the SAF URI. Smallest code change,
   but duplicates large files transiently and needs a separate filename-only path for
   MV/MZ/Ren'Py/LOVE/ScummVM — two detection paths to keep in sync.
2. **Folder abstraction in `:detect` (chosen).** Introduce a pure-Kotlin `GameFolder`
   interface with file/directory entries and an input stream for a named child. Implement
   `FileGameFolder` in `:detect` and a SAF `DocumentFile` adapter in `:app`.
   `EngineDetector.detect(GameFolder)` replaces `detect(File)` as the main path; the `File`
   overload stays as a thin wrapper for existing callers and tests. `GameMakerWad.read`
   gains a stream-based overload so the header/chunk walk works without random access.
   One detection path for every source, no copies, and `:detect` stays pure.
3. **Filename-only M1 detection.** Never read file contents; mark every folder "detected,
   not inspected". Fastest, but fails M1's exit criterion that a scan recognises GameMaker
   and MV/MZ folders with the same detector used by the checker.

Approach 2 was chosen by the user. It costs a refactor of `EngineDetector` and
`GameMakerWad` plus new host tests, but avoids duplicated game data and a second detector.

## Decisions and how it gets built

### Builder-review decisions

A fresh reviewer found five places where a wrong guess would change the plan. Two need the
user's approval because they change the spec (marked **pending** below); three are settled
here as implementation calls.

1. **"Survives a launcher reinstall" (settled 2026-11-27).** Room data and persisted SAF
   grants are deleted by a full uninstall; `allowBackup="false"` prevents cloud restore.
   FR-17 and the M1 exit criteria now read *process death and launcher update*, with a
   re-scan restoring the library after a full uninstall. Metadata stays app-private; the
   shared-storage alternative was rejected because it exposes game paths and complicates
   scoped storage.
2. **Scanned game → pinned shortcut (settled).** No automatic title matching: Butterscotch
   owns both the `game-<uuid>` id and the label, and the shortcut Intent is hidden from
   launchers, so a title match could launch the wrong game. Add `linkedShortcutId` to the
   game model; the detail panel lets the user choose a pinned shortcut explicitly. Duplicate
   titles are separate rows, not merged.
3. **FR-20 hosted-engine saves (settled 2026-11-27).** No engine is hosted until M2, so
   the requirement is vacuous in M1. FR-20 is marked *deferred to M2*, the Butterscotch
   limitation is documented now, and the real save-location design stays in M2. This is a
   recorded deferral, not a silent narrowing.
4. **Ids and repeated scans (settled).** A scanned id is derived from the SAF tree authority,
   tree document id and the document's relative id path. Multiple trees can be registered;
   a re-scan upserts rows, never auto-deletes them, which preserves covers and links.
   Explicit "Remove" is the only way to drop a row. Cover files are named from a stable
   hash of the id.
5. **Which engines are imported (settled).** Import every folder the detector recognises.
   MV/MZ show "Detected — WebView runtime arrives in M2"; Ren'Py, LOVE and ScummVM show
   "Detected — runtime undecided (M3)"; Launch is disabled with that reason. This is what
   the M1 exit criterion "imports at least GameMaker and MV/MZ folders" asks for, without
   pretending an engine is hostable.

### `:detect` — folder abstraction

```kotlin
data class GameEntry(val name: String, val isDirectory: Boolean)

interface GameFolder {
    val name: String
    val isDirectory: Boolean
    fun entries(): List<GameEntry>
    fun child(name: String): GameFolder?
    fun open(name: String): InputStream?
}
```

- `FileGameFolder(file: File) : GameFolder` lives in `:detect` and uses `File` directly.
- `EngineDetector.detect(folder: GameFolder)` contains the current rules, expressed with
  `entries()`, `child()` and `open()`. `detect(file: File)` delegates to
  `FileGameFolder` so the 12 existing tests and the in-app checker do not change shape.
- `GameMakerWad.read(input: InputStream, length: Long?)` reads the 20-byte header and
  walks top-level chunk headers sequentially (8 bytes at a time, skip by aligned length).
  The existing `read(File)` delegates to it with `FileInputStream`. No seek is needed, so
  a SAF stream works. Keep the existing `FORM`/`GEN8`, WAD range and `CODE` rules verbatim.
- Detection outcome is unchanged: same `Detection` fields, same English blocker strings.

### `:app` — SAF adapter and scan

- `OpenDocumentTree` picker; on result, `takePersistableUriPermission` with
  `FLAG_GRANT_READ_URI_PERMISSION`, store the tree URI in `SharedPreferences` alongside
  the existing recents prefs. No game data, only URIs and metadata.
- `SafGameFolder : GameFolder` wraps `DocumentFile.fromTreeUri` + `ContentResolver`;
  `open()` uses `contentResolver.openInputStream(documentUri)`.
- Scan walks at most two levels below the chosen root, skips folders over a fixed entry
  count, and calls `EngineDetector.detect` once per candidate. Results become `Game`
  objects with `source = SCANNED`. Permission errors, missing documents and detector
  exceptions become per-folder skip reasons shown in the UI; the scan continues.
- On resume, re-check persisted grants with `checkUriPermission`; a revoked grant produces
  a visible message and leaves stored metadata alone.

### `:library` — persistence

- `GameEntity(id, title, folderPath, engine, wadVersion, coverPath, addedAt)`. `folderPath`
  keeps its name for compatibility but holds a SAF tree/document URI string, never a
  filesystem path the app assumes exists. `engine` is stored as its enum name via a
  `TypeConverter`.
- Room 2.6.1 with KSP 1.9.24-1.0.20 (Apache-2.0; record both in `LICENSING.md`). No
  coroutines: DAO methods are blocking and are called only from background `Thread`s.
- `RoomGameRepository : GameRepository` maps entities to the existing `Game` model without
  exposing Room types to other modules. `InMemoryGameRepository` stays for tests.
- `Game` is extended with `coverPath`, `addedAt`, a `source` (`PINNED`/`SCANNED`) and a
  nullable `linkedShortcutId`; defaults keep existing call sites compiling.

### `:app` — library UI and covers

- `MainActivity` loads repository games on a background `Thread` and merges them with
  `ButterscotchShortcuts.list()` by id/title, pinned shortcuts first. The hero panel keeps
  showing the first playable entry.
- A scanned GameMaker entry launches only through the shortcut the user explicitly linked
  in its detail panel. Without a link, the row opens Butterscotch and shows "Pin this game
  in Butterscotch, then link the pin here". MV/MZ, Ren'Py, LOVE and ScummVM rows show a
  "detected, not launchable yet" reason instead of a Launch action.
- Cover picker: SAF `OpenDocument` for an image, downsample to 512 px max on a background
  thread, write `filesDir/covers/<id>.webp`, store the file name in `coverPath`. No image
  library. Missing cover keeps the existing letter tile.
- Add a small detail panel (title, engine, WAD, location, launch, remove, change cover).
  Removing a scanned game deletes only its metadata and cover, never the source folder.

### Saves

No save API in M1. Add a docs note: Butterscotch games save in Butterscotch's own storage;
IndieHost does not copy or relocate them.

## Testing

- Keep the existing 12 `:detect` tests green unchanged.
- Add host tests for `FileGameFolder` + `detect(GameFolder)` parity across GameMaker WAD 16,
  YYC (no CODE), MV, MZ and an unknown folder; add a stream-based `GameMakerWad` test.
- Pure mapping tests in `:library` (`Game` ↔ `GameEntity`, including engine conversion).
- Instrumented DAO tests in `app/src/androidTest` for insert/find/remove and a stored
  entity across process death. They cannot run on this aarch64 host (broken `adb`); report
  them as written-but-unrun, not as passing.
- Manual cases in `TESTING.md`: SAF grant/persist/revoke, scanned GameMaker + MV folders,
  cover pick/downsample, launch with and without a pinned shortcut, remove, process death,
  and a folder with an unreadable document.

## Risks

- KSP adds a build plugin and may need more memory than this 3.7 GiB container; use the
  existing `-Xmx1200m` or lower for local builds.
- SAF tree URIs differ across OEMs; all locations are treated as opaque URI strings.
- `DocumentFile.listFiles()` on a large tree is slow; the two-level and entry-count caps
  are the guardrail, and the scan runs off the main thread.
- Formatting and docs must stay in step: FR-02/FR-14, NFR-05/NFR-07/NFR-08/NFR-11,
  ARCHITECTURE §4/§7 and ROADMAP M1 exit criteria.

## Left to the builder

- Exact Compose layout of the detail panel, within the existing `SteamTheme` patterns.
- Preference key names and the scan depth/entry-count constants.
- Whether the DAO instrumented tests are enough or Robolectric is added later if a host
  test is required.
