# M1 — Real Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give IndieHost its own persistent, scannable game library while keeping Butterscotch pins as a first-class source.

**Architecture:** A pure-Kotlin `GameFolder` seam lets one detector read both `File` folders and SAF documents. `:library` stores games in Room behind the existing `GameRepository`. `:app` scans a persisted SAF tree, merges scanned games with pinned shortcuts, and adds a detail panel plus user-picked cover art.

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.09.02, Room 2.6.1, KSP 1.9.24-1.0.20, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-25-m1-real-library-design.md`

**Status:** implementation complete. Gate run: `gradle test :app:assembleDebug` — detector
19/19, library mapping 3/3, app scanner/merge/cover 16/16, debug APK built. Manual cases
MT-21…MT-28 and the instrumented `GameDaoTest` remain unrun because `adb` is broken in this
container; M1 is not done until they pass on the baseline device.

## Global Constraints

- `minSdk 24`, `targetSdk 34`, `compileSdk 34`; `applicationId` and `versionCode` unchanged.
- `:detect` stays pure Kotlin/JVM: no `android.*` imports.
- No `INTERNET` permission, no network call, no Wine/emulation, no Butterscotch fork or bundle.
- No game data, saves, cover art from games, or secrets in the repository.
- Room DAO calls are blocking and run only on background `Thread`s; no coroutines.
- One logical change per commit; docs travel with behaviour.
- Do not claim a device or instrumented test that did not run.

## Review Focus

- A revoked SAF grant must produce a visible message and leave stored metadata untouched, never a crash.
- Re-scanning the same tree must upsert by stable id and preserve covers and pin links.
- A GameMaker scan without an explicitly linked pin must not launch and must explain why.
- A large cover image must be downsampled before allocation, or the A22 will OOM.
- Every DAO read/write must be off the main thread.

---

### Task 1: Settle the M1 semantics in the spec

**Files:**
- Modify: `spec.md` (FR-17, FR-20, §13.2)
- Modify: `ROADMAP.md` (M1 exit criteria, saves work)
- Modify: `docs/superpowers/specs/2026-11-25-m1-real-library-design.md` (status)

**Interfaces:**
- Produces: FR-17 means process death and app update; a full uninstall is restored by re-scan.
- Produces: FR-20 is not applicable until M2 hosts an engine; Butterscotch saves are documented as Butterscotch's.

- [x] **Step 1: Reword FR-17**

Replace "across process death and reinstall of the launcher" with "across process death
and launcher updates; a full uninstall is restored by re-scanning the same folder".

- [x] **Step 2: Reword FR-20 and the M1 exit criterion**

Mark hosted-engine saves "Not applicable until M2", keep the Butterscotch limitation, and
change §13.2's save line to say no hosted engine exists in M1.

- [x] **Step 3: Mark the design approved**

Change the design status to "Approved 2026-11-27" and record that the user approved the
recommended wording.

- [x] **Step 4: Commit**

```bash
git add spec.md ROADMAP.md docs/superpowers/specs/2026-11-25-m1-real-library-design.md
git commit -m "docs: settle M1 reinstall and save semantics"
```

---

### Task 2: Add the `GameFolder` seam to `:detect`

**Files:**
- Create: `detect/src/main/kotlin/com/indiehost/detect/GameFolder.kt`
- Modify: `detect/src/main/kotlin/com/indiehost/detect/EngineDetector.kt`
- Test: `detect/src/test/kotlin/com/indiehost/detect/GameFolderTest.kt`

**Interfaces:**
- Produces: `data class GameEntry(val name: String, val isDirectory: Boolean)`
- Produces: `interface GameFolder { val name: String; val isDirectory: Boolean; val location: String; fun entries(): List<GameEntry>; fun child(name: String): GameFolder?; fun open(name: String): InputStream? }`
- Produces: `class FileGameFolder(val file: File) : GameFolder`
- Produces: `fun EngineDetector.detect(folder: GameFolder): Detection`; `detect(File)` delegates.

- [x] **Step 1: Write the failing parity test**

Create a `FileGameFolderTest` that writes a WAD-16 `data.win`, wraps the folder, and asserts
`EngineDetector().detect(FileGameFolder(folder))` has `GAMEMAKER_VM`, `runnable == true`,
`wadVersion == 16`. Add an MV folder case and an unknown-folder case.

- [x] **Step 2: Run it and confirm it fails**

Run: `gradle :detect:test --tests '*GameFolderTest*' --console=plain`
Expected: FAIL because `GameFolder` does not exist.

- [x] **Step 3: Implement `GameFolder` and refactor the detector**

Implement the interface and `FileGameFolder`. Change every private detector helper to take
`GameFolder`; use `entries()`, `child()` and `open()`. Keep `detect(File)` as a one-line
delegate. Preserve all existing blocker strings.

- [x] **Step 4: Run the detector suite**

Run: `gradle :detect:test --console=plain`
Expected: the original 12 tests plus the new parity tests pass.

- [x] **Step 5: Commit**

```bash
git add detect/src
git commit -m "detect: read folders through a GameFolder seam"
```

---

### Task 3: Read WAD headers from a stream

**Files:**
- Modify: `detect/src/main/kotlin/com/indiehost/detect/GameMakerWad.kt`
- Test: `detect/src/test/kotlin/com/indiehost/detect/GameMakerWadTest.kt`

**Interfaces:**
- Produces: `fun read(input: InputStream, length: Long? = null): Header?`
- Produces: `read(File)` delegates to `FileInputStream` and the stream overload.

- [x] **Step 1: Write the failing stream test**

Add a test that builds a WAD-16 byte array with CODE, calls
`GameMakerWad.read(ByteArrayInputStream(bytes), bytes.size.toLong())`, and asserts version
16 and `hasCodeChunk`. Add a truncated stream case that returns null.

- [x] **Step 2: Run it and confirm it fails**

Run: `gradle :detect:test --tests '*GameMakerWadTest*' --console=plain`
Expected: FAIL because the overload does not exist.

- [x] **Step 3: Implement the sequential reader**

Read 20 bytes, validate `FORM`/`GEN8`, read the version, skip the rest of the GEN8 chunk,
then read 8-byte chunk headers and skip `align4(length) - 8` bytes until `CODE`, EOF, or
`MAX_CHUNKS`. Use a `readFully` helper and a `skipFully` loop. Never seek.

- [x] **Step 4: Run the suite**

Run: `gradle :detect:test --console=plain`
Expected: all tests pass.

- [x] **Step 5: Commit**

```bash
git add detect/src
git commit -m "detect: parse WAD headers from a stream"
```

---

### Task 4: Persist games in Room

**Files:**
- Modify: `library/build.gradle.kts`
- Create: `library/src/main/kotlin/com/indiehost/library/GameEntity.kt`
- Create: `library/src/main/kotlin/com/indiehost/library/GameDao.kt`
- Create: `library/src/main/kotlin/com/indiehost/library/IndieHostDatabase.kt`
- Create: `library/src/main/kotlin/com/indiehost/library/RoomGameRepository.kt`
- Modify: `library/src/main/kotlin/com/indiehost/library/Game.kt`
- Modify: `library/src/main/kotlin/com/indiehost/library/GameRepository.kt`
- Test: `library/src/test/kotlin/com/indiehost/library/GameMappingTest.kt`
- Test: `library/src/androidTest/kotlin/com/indiehost/library/GameDaoTest.kt`

**Interfaces:**
- Produces: `enum class GameSource { PINNED, SCANNED }`
- Produces: `data class Game(..., coverPath: String? = null, addedAt: Long = 0L, source: GameSource = GameSource.PINNED, linkedShortcutId: String? = null, treeUri: String? = null)`
- Produces: `class RoomGameRepository(context: Context) : GameRepository`
- Produces: `@Entity(tableName = "games") data class GameEntity(...)`

- [x] **Step 1: Add Room and KSP**

Add the KSP plugin to the root build and `:library`, then `room-runtime`, `room-ktx` and
`ksp("androidx.room:room-compiler:2.6.1")`. Add `testInstrumentationRunner`.

- [x] **Step 2: Write the failing mapping test**

Assert `Game.toEntity().toGame()` round-trips engine, WAD, source, cover and link.

- [x] **Step 3: Implement the model, entity, DAO and database**

Use `@PrimaryKey` id, `@Insert(onConflict = REPLACE)`, `@Query("SELECT * FROM games ORDER BY title COLLATE NOCASE")`,
`@Query("SELECT * FROM games WHERE id = :id")`, `@Query("DELETE FROM games WHERE id = :id")`.
Convert `Engine` and `GameSource` with `@TypeConverters`. Version 1, `fallbackToDestructiveMigration()`.

- [x] **Step 4: Implement `RoomGameRepository`**

Map entities in both directions. Keep `InMemoryGameRepository` for tests.

- [x] **Step 5: Run the mapping test and compile the app**

Run: `gradle :library:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: PASS and BUILD SUCCESSFUL.

- [x] **Step 6: Commit**

```bash
git add library build.gradle.kts
git commit -m "library: persist games in Room"
```

---

### Task 5: Scan a persisted SAF tree

**Files:**
- Create: `app/src/main/kotlin/com/indiehost/launcher/SafGameFolder.kt`
- Create: `app/src/main/kotlin/com/indiehost/launcher/GameScanner.kt`
- Create: `app/src/main/kotlin/com/indiehost/launcher/GameFolderStore.kt`
- Modify: `app/src/main/kotlin/com/indiehost/launcher/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (no new permission)
- Test: `app/src/test/kotlin/com/indiehost/launcher/GameScannerTest.kt`

**Interfaces:**
- Produces: `class SafGameFolder(context: Context, treeUri: Uri, document: DocumentFile) : GameFolder`
- Produces: `data class ScanResult(val games: List<Game>, val skipped: List<String>, val error: String?)`
- Produces: `class GameScanner(context: Context, detector: EngineDetector = EngineDetector()) { fun scan(treeUri: Uri): ScanResult }`
- Produces: `class GameFolderStore(context: Context) { fun add(uri: Uri); fun all(): List<Uri>; fun remove(uri: Uri) }`

- [x] **Step 1: Write the failing scanner test**

Use a fake `GameFolder` tree (root → `Undertale/data.win`, root → `MV/www/index.html` +
`www/js/rpg_core.js`) and assert two games, stable ids across two scans, and an unknown
folder skipped with a reason. Keep the fake in the test; `GameScanner` should accept a
`GameFolder` root for testability.

- [x] **Step 2: Implement `SafGameFolder`**

Wrap `DocumentFile`; `entries()` maps `listFiles()` names and directory flags; `child()`
looks up by name; `open()` uses `contentResolver.openInputStream(document.uri)`.

- [x] **Step 3: Implement `GameScanner`**

Walk at most two levels, cap entries per folder at 500, detect each directory, stop
descending when a game is recognised. Derive ids as `"scan-" + sha256("$treeUri|$documentUri")`.
Carry `treeUri`, `folderPath = document.uri.toString()`, `addedAt = existing?.addedAt ?: now`,
and preserve `coverPath`/`linkedShortcutId` from the repository on upsert. Catch
`SecurityException` and `IOException` per folder into `skipped`.

- [x] **Step 4: Implement `GameFolderStore`**

Store a string set of tree URIs in `indiehost.folders`; call
`takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` on add.

- [x] **Step 5: Wire the scan into `MainActivity`**

On startup, load repository games on a `Thread`; expose `onAddFolder(uri)` that persists
the grant and scans all trees. Show `scanning`, the imported count and skipped reasons.
On resume, verify each grant with `checkUriPermission`; a revoked grant sets a visible
message without deleting rows.

- [x] **Step 6: Run tests and build**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: PASS and BUILD SUCCESSFUL.

- [x] **Step 7: Commit**

```bash
git add app/src
git commit -m "app: scan a SAF games folder into the library"
```

---

### Task 6: Merge scanned games and pins in the library UI

**Files:**
- Modify: `app/src/main/kotlin/com/indiehost/launcher/HomeUi.kt`
- Modify: `app/src/main/kotlin/com/indiehost/launcher/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/kotlin/com/indiehost/launcher/LibraryItem.kt`
- Test: `app/src/test/kotlin/com/indiehost/launcher/LibraryMergeTest.kt`

**Interfaces:**
- Produces: `data class LibraryItem(val game: Game?, val shortcut: RunnerShortcut?, val title: String, val subtitle: String, val launchable: Boolean, val reason: String?)`
- Produces: `fun mergeLibrary(games: List<Game>, pins: List<RunnerShortcut>): List<LibraryItem>`

- [x] **Step 1: Write the failing merge test**

Pins first; a pin linked by a scanned game is hidden; an unlinked GameMaker scan has
`launchable == false` and a reason; MV/MZ are launchable after M2, not now; Ren'Py is not.

- [x] **Step 2: Implement the merge**

Linked pins are matched by `linkedShortcutId`. A scanned GameMaker game is launchable only
when its linked pin is present. MV/MZ are not launchable until Task 10.

- [x] **Step 3: Add the detail panel**

Tapping a scanned row opens a `Dialog` with title, engine, WAD, location, cover, Launch
(when launchable), Link pin (list of pins), Change cover, Remove, Close. Removing deletes
metadata and cover only, never the source folder. Keep pin rows one-tap launch.

- [x] **Step 4: Update copy and empty state**

Add strings for "Add games folder", "Scanning...", "Detected, no runtime yet", "Pin this
game in Butterscotch, then link the pin here", "Remove", "Change cover", "Link pin".

- [x] **Step 5: Build**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: PASS and BUILD SUCCESSFUL.

- [x] **Step 6: Commit**

```bash
git add app/src
git commit -m "app: show scanned games and pins in one library"
```

---

### Task 7: Add user-picked cover art

**Files:**
- Create: `app/src/main/kotlin/com/indiehost/launcher/CoverStore.kt`
- Modify: `app/src/main/kotlin/com/indiehost/launcher/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/indiehost/launcher/HomeUi.kt`
- Test: `app/src/test/kotlin/com/indiehost/launcher/CoverStoreTest.kt`

**Interfaces:**
- Produces: `class CoverStore(context: Context) { fun save(gameId: String, uri: Uri): String?; fun delete(fileName: String); fun file(fileName: String): File }`

- [x] **Step 1: Write the failing downsample test**

A 2000×2000 bitmap saved as PNG is passed through the pure `sampleSizeFor(width, height,
max = 512)` helper; assert the chosen sample size keeps the result at or below 512 and
above 256. Keep the helper `internal` and pure for the host test.

- [x] **Step 2: Implement `CoverStore`**

Decode bounds with `inJustDecodeBounds`, compute `inSampleSize`, decode, scale to 512 max,
compress WEBP 85 to a temp file, rename to `<sha256(id)>.webp`. Delete on replace/remove.

- [x] **Step 3: Wire the picker**

`OpenDocument` with `image/*`; save on a background `Thread`; update the repository and the
in-memory library; render with `BitmapFactory.decodeFile` on the same background thread and
hold `ImageBitmap` in state. No image library.

- [x] **Step 4: Build**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: PASS and BUILD SUCCESSFUL.

- [x] **Step 5: Commit**

```bash
git add app/src
git commit -m "app: attach downsampled cover art"
```

---

### Task 8: Documentation and manual matrix

**Files:**
- Modify: `ARCHITECTURE.md` (GameFolder, Room, scan flow)
- Modify: `TESTING.md` (SAF, covers, detail, reinstall/re-scan)
- Modify: `ROADMAP.md` (M1 status and evidence)
- Modify: `LICENSING.md` (Room/KSP Apache-2.0)
- Modify: `README.md` (library description)

- [x] **Step 1: Update the docs**

Record the new seams, the Room schema, the scan limits, the cover path, the reinstall
semantics and the exact test commands. Mark M1 items done only when their evidence exists.

- [x] **Step 2: Run the full gate**

Run: `gradle :detect:test :app:assembleDebug --console=plain`
Expected: 12+ tests pass and BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add ARCHITECTURE.md TESTING.md ROADMAP.md LICENSING.md README.md
git commit -m "docs: record the M1 library"
```
