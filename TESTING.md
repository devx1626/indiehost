# Testing

> **Status:** Living document · **Last updated:** 2026-11-27
> **Related:** [spec.md](spec.md) · [PREREQUISITES](PREREQUISITES.md) · [ARCHITECTURE](ARCHITECTURE.md) · [ROADMAP](ROADMAP.md)

IndieHost has host-side JVM tests for the detector, the Room mapping, the SAF scanner,
library merging and cover sizing, plus a manual device protocol for everything that
depends on Android's launcher, shortcut and SAF APIs. One instrumented DAO test exists but
has never run in this environment (`adb` is broken here); do not report it as passing.

---

## 1. What is tested, and where

| Layer | Scope | Tool | Status |
| --- | --- | --- | --- |
| JVM unit tests (`:detect`) | `GameMakerWad` parsing, `EngineDetector` fingerprints, blockers | JUnit 4 | **19 tests**, passing |
| JVM unit tests (`:library`) | `Game` ↔ `GameEntity` mapping, enum fallback | JUnit 4 | **3 tests**, passing |
| JVM unit tests (`:app`) | SAF scan/progress, merge/duplicates, covers, WebView paths/origins, search, capabilities, feedback, contrast, motion, previews, compatibility | JUnit 4 | **43 tests**, passing |
| JVM unit tests (`:runtimes`) | Adapter registry, WebView action constants, pin policy | JUnit 4 | **7 tests**, passing |
| Instrumented tests | Room DAO round trip | AndroidX Test | 1 test written (`GameDaoTest`), **not run here**; run via `tools/device_checks.sh --dao` |
| Manual device tests | Home role, pin, scan, covers, launch, recents, drawer, permissions | Human + adb | Required per release; script and synthetic fixture provided; cases not yet run |
| Performance | Startup, resume, drawer, fps, memory | adb + screen recording | Protocol below; not yet measured |

### 1.1 Existing JVM coverage

`detect/src/test/kotlin/com/indiehost/detect/GameMakerWadTest.kt`:

1. Reads WAD 16 from a Studio 1.4-shaped file.
2. Reads every version 8–17.
3. Flags version 18 as unsupported.
4. Detects a missing `CODE` chunk.
5. Rejects non-GameMaker and truncated files.

`EngineDetectorTest.kt`:

6. Undertale-shaped folder is a runnable WAD 16 game.
7. `game.droid` is accepted.
8. A YYC build is rejected with a reason.
9. A version outside 8–17 is rejected.
10. Distinguishes MV from MZ.
11. Detects LOVE and Ren'Py.
12. An empty folder is `UNKNOWN`.
13. `FileGameFolder` lists children and opens files only.
14. MV is detected through the `GameFolder` seam.
15. An unknown folder through the seam is not runnable.
16. `FileGameFolder` and detector agree on a WAD 16 game.
17. A WAD header is read from a `InputStream` without copying the whole file.
18. A stream without `CODE` reports the YYC blocker.
19. A truncated stream is not a GameMaker data file.

`.library` (`GameMappingTest.kt`): round-trips every `Game` field through `GameEntity`,
maps unknown engine names to `UNKNOWN`, and keeps optional fields null.

`.app` host tests:

- `GameScannerTest` (5): imports GameMaker and MV, ignores unknown folders, keeps stable
  ids/covers/pin links across re-scans, stops at two levels, and reports an unreadable
  folder with a reason.
- `LibraryMergeTest` (7): pins first, linked pin hidden, missing pin, MV/MZ and M3
  statuses, blocker precedence, pin-only rows.
- `CoverStoreTest` (4): subsampling bounds, already-small images, tall images, stable
  hashed file names.
- `WebPathsTest` (4): both RPG Maker entry layouts, local-origin allowlist, traversal and
  percent-escape rejection, MIME map.
- `AppSearchTest` (4): case-insensitive label/package matching, accent folding, empty query
  and non-matching queries.
- `ContrastTest` (3): every text token against all four surfaces, plus accent/button ink.
- `MotionTest` (2): zero/negative animator scale disables decorative motion.
- `LauncherPreviewsTest` (1, debug): the four key state previews still exist.
- `CompatibilityTest` (1): every `Engine` value has a documented route.
- `CapabilityPolicyTest` (3): launch gating for GameMaker and RPG Maker engines.
- `FeedbackPolicyTest` (3): urgency classification, priority and TTL expiry.

`.runtimes` host tests: `RuntimeRegistryTest.kt` (3) verifies all three registered engines,
null for an unregistered engine, and shared WebView action/extra constants;
`PinPolicyTest.kt` (4) verifies valid/invalid, non-shortcut, foreign and disabled/unpinned
pin decisions.

The fixtures are synthetic; no real game data is used.

---

## 2. Running the tests

```bash
# Host-side detector tests (no device, no Android SDK needed for this module alone
# once Gradle/JDK are available).
./gradlew :detect:test

# Everything that can run without a device (three host-testable modules).
./gradlew test

# App scanner/merge/cover tests.
./gradlew :app:testDebugUnitTest

# Build the APK used for the manual protocol.
./gradlew :app:assembleDebug
```

Expected: `:detect:test` reports 19 passed, `:library:testDebugUnitTest` 3,
`:app:testDebugUnitTest` 43 and `:runtimes:testDebugUnitTest` 7, all with 0 failures. A
detector failure blocks any release. `gradle test` does **not** run
`library/src/androidTest`; that needs a device.

On the aarch64 container used here, add `-Pkotlin.incremental=false` and the aapt2
override; a stale incremental cache can otherwise make the test compiler miss production
top-level functions.

### 2.2 Device-less UI previews

`app/src/debug/kotlin/com/indiehost/launcher/LauncherPreviews.kt` has Compose previews for
the empty library, a scanning state with a blocked game, an app drawer with no search
matches, and the drawer loading state. Open them in Android Studio's preview pane to inspect
the shared state cards, reserved badge slots and search affordances without a device. They
are debug-only and are not part of the shipped APK. `LauncherPreviewsTest` (host) checks that
all four state previews still exist. Robolectric PNG rendering is deferred until the offline
`android-all-instrumented` jar story is settled; it is not a substitute for MT-37.

### 2.1 Running the device checks from a workstation

The build container cannot run these tests: its Termux `adb` needs a libc++ symbol that is
not installed (`_ZNSt6__ndk113__hash_memoryEPKvm`), the SDK's `adb` is x86-64, and its
`pm`/`am` run as app uid `u0a188`, not the Android shell uid, so they cannot install a test
APK. The container is root-on-device but `adbd` is stopped and `ro.adb.secure=1` with no
authorised keys. No instrumented or manual case has run here.

The script follows an explicit device-check contract: `PASS`/`FAIL`/`SKIP` counters,
`0` = all checks passed, `1` = a check failed, `2` = usage/device problem; `SKIP` means "not
applicable on this device", never a silent pass. It prints device identity and kernel/page
size, clears the crash buffer before launch and checks it afterwards, and removes only its
own synthetic fixture on `--clean`.

On your own machine, with the phone connected and USB debugging authorised:

```bash
gradle :app:assembleDebug :library:assembleDebugAndroidTest
tools/device_checks.sh --all      # install, run GameDaoTest, push fixture, launch app
tools/device_checks.sh --perf     # raw cold-start / resume / memory output
tools/device_checks.sh --clean    # remove only /sdcard/IndieHostDeviceCheck
```

`--fixture` writes a tiny **synthetic** MV layout (`index.html` plus a two-line
`rpg_core.js`) generated by the script itself. It is test data, not a game; it is never
committed or packaged. After `--all`, pick `IndieHostDeviceCheck` with `ADD GAMES FOLDER`,
open `MVGame`, tap `PLAY`, and check MT-29. MT-30 needs a legal MZ game and cannot use the
fixture. If the installed app uses a different signing key, use `--replace` — it uninstalls
the app and drops its pins, so only do that on a test device.

Record the results in [COMPATIBILITY.md](COMPATIBILITY.md); leave a cell `unmeasured` when
you cannot measure it.

---

## 3. Manual device protocol

Run on the baseline Helio A22 unless a case says otherwise. Reset state between destructive
cases with `adb uninstall com.indiehost.launcher` (this also drops IndieHost's pins).

### MT-01 — Fresh install, not the home app

**Precondition:** IndieHost installed, stock launcher still active.
**Steps:** Open IndieHost from the app drawer.
**Expected:** Hero says "Make it your home screen"; library shows the setup panel with
`CHOOSE HOME APP`; no crash; recents either explains usage access or shows local launches.

### MT-02 — Become the home app

**Steps:** Tap `CHOOSE HOME APP` → select IndieHost → press Home.
**Expected:** IndieHost is the home screen; the setup panel is replaced by the empty-library
panel (or pinned games); pressing Home again returns without a reload.

### MT-03 — Empty library

**Precondition:** IndieHost is home; no games pinned.
**Expected:** Hero says "Your library is empty"; the library explains Butterscotch's
"Add Shortcut to Home Screen" flow; no error.

### MT-04 — Pin a game

**Precondition:** Butterscotch installed; a VM game imported; IndieHost is home.
**Steps:** In Butterscotch, open the game's settings → Add Shortcut to Home Screen → accept.
**Expected:** The translucent pin activity flashes and finishes; the game appears in
IndieHost's library after returning with its shortcut icon (or a letter tile if the system
supplies none); `adb logcat -s IndieHostPin` shows `Pinned game-…`.
**Failure hints:** If the pin belongs to another launcher, re-pin with IndieHost active.

### MT-05 — Launch a game

**Steps:** Tap the game tile or `PLAY`.
**Expected:** Butterscotch's game activity opens; IndieHost is not in the foreground; Back
from the game returns to IndieHost; no game data is copied into IndieHost's storage.

### MT-06 — Missing runtime

**Precondition:** Butterscotch uninstalled.
**Steps:** Tap `BUTTERSCOTCH`, or tap a stale game tile.
**Expected:** A status panel explains that Butterscotch is not installed and shows the Play
Store URL; no crash.

### MT-07 — Usage access denied

**Precondition:** IndieHost is home; usage access not granted.
**Steps:** Launch one or two apps from IndieHost; return Home.
**Expected:** The recents row shows those apps, newest first; an explanation and
`ALLOW USAGE ACCESS` button are visible when the row is empty.

### MT-08 — Usage access granted

**Steps:** Grant usage access in Settings; open an app outside IndieHost; return Home.
**Expected:** The externally opened app appears in recents, ordered by recency; locally
launched apps still appear.

### MT-09 — Revoke usage access

**Steps:** Revoke usage access while IndieHost is backgrounded; return Home.
**Expected:** No crash; the row falls back to local launches; the explanation returns.

### MT-10 — Drawer and search

**Steps:** Tap `ALL APPS`; type part of an app name; clear; long-press a tile.
**Expected:** The grid lists launchable apps (one per package, no IndieHost); search is
case-insensitive and matches label or package; long-press opens the system app-info page;
`HOME` and Back close the drawer.

### MT-11 — Back behaviour

**Steps:** On the home screen, press Back; open the drawer, press Back.
**Expected:** On home, nothing happens (the launcher does not exit); in the drawer, it
closes.

### MT-12 — Home key while inside IndieHost

**Steps:** Open the drawer; press Home.
**Expected:** The drawer closes and the home screen is shown (`onNewIntent` path).

### MT-13 — Process death / rotation

**Steps:** Rotate the device; kill the app with `adb shell am force-stop
com.indiehost.launcher`; reopen.
**Expected:** No crash; state reloads from system data; the drawer starts closed; pins are
still listed.

### MT-14 — Check a valid VM `data.win`

**Steps:** `CHECK DATA.WIN` → pick a VM build (Undertale 1.08 / WAD 16).
**Expected:** The button reads `CHECKING...`, the UI stays responsive, then the report says
GameMaker (VM), runnable yes, WAD 16, no blocker.

### MT-15 — Check a YYC/GMRT build

**Steps:** Pick a build with no `CODE` chunk.
**Expected:** Runnable no; blocker mentions the missing CODE chunk; no crash.

### MT-16 — Large file

**Steps:** Pick a very large `data.win` (hundreds of MB).
**Expected:** The button reads `CHECKING...` and the screen still scrolls while the file is
copied and checked; the report appears when done. No ANR, and `cacheDir/import` is empty
afterwards. Record the file size and elapsed time.

### MT-17 — Another launcher takes home

**Steps:** Set the stock launcher as home; open IndieHost from the drawer.
**Expected:** Library shows the setup panel again; no stale games; no crash. Set IndieHost
back and confirm games return (re-pin if the system dropped them).

### MT-18 — Stale shortcut / uninstalled game

**Steps:** Uninstall Butterscotch while a game tile exists; tap the tile.
**Expected:** A clear status message; no crash. Reinstall and re-pin to recover.

### MT-19 — Pin request from an unrelated app

**Steps:** Use a test app that pins a shortcut while IndieHost is home, and separately pin a
non-`game-` shortcut from Butterscotch.
**Expected:** The pin activity finishes without accepting; nothing is added to the library;
`adb logcat -s IndieHostPin` shows `Ignored pin request from <package>/<id>`.

### MT-20 — Dark theme and system bars

**Steps:** Cold start on a device with a light system theme.
**Expected:** No white flash; status/navigation bars are `#171A21`; text remains readable.

### MT-21 — Scan a games folder

**Precondition:** A legally owned games folder on the device; IndieHost is home.
**Steps:** `ADD GAMES FOLDER` → pick the parent folder; wait for the scan message.
**Expected:** The message reports imported games and skipped folders; GameMaker and MV/MZ
folders appear as rows; unknown folders are ignored; `www/` and `game/` internals do not
appear as separate games. No game file is copied into IndieHost storage.

### MT-22 — Re-scan is stable

**Steps:** Re-open `ADD GAMES FOLDER` and pick the same tree; compare rows and covers.
**Expected:** No duplicates; `addedAt`, covers and pin links survive; a folder removed from
the tree is still listed (the scan upserts and never auto-deletes).

### MT-23 — Link a pin and launch

**Precondition:** A GameMaker VM scan without a pin, and the same game pinned in
Butterscotch.
**Steps:** Open the scanned row → link its Butterscotch pin → tap `PLAY`.
**Expected:** The row becomes launchable, the duplicate pin tile disappears, and `PLAY`
starts Butterscotch's own activity. Unlink returns it to `PIN NEEDED`.

### MT-24 — Detected but not launchable

**Steps:** Scan a folder containing an MV/MZ game and a YYC/YYC-shaped game; open each row.
**Expected:** MV/MZ shows the M2 message, other engines the M3 message, and a YYC/no-`CODE`
game shows its detector blocker with `PLAY` disabled. No crash or silent launch.

### MT-25 — Cover art

**Steps:** Open a scanned row → `CHOOSE COVER` → pick a large image (≥ 2000 px) → close and
reopen IndieHost → `REPLACE COVER` → `REMOVE COVER`.
**Expected:** The row shows the cover after restart; the stored WebP is at most 512 px on
its longest edge; replace and remove take effect; no full-resolution image is kept.

### MT-26 — Revoked SAF grant

**Steps:** In Settings, revoke IndieHost's access to the games folder; return Home and scan
again; then re-pick the folder.
**Expected:** A clear permission message, no crash, and stored rows untouched. Re-picking
the same folder restores access and imports without duplicating rows.

### MT-27 — Process death, launcher update and uninstall

**Steps:** Force-stop IndieHost and reopen; install an updated APK over it; then uninstall
and re-scan the same folder.
**Expected:** Rows and covers survive the first two; after uninstall the library is empty
until the same folder is re-scanned, at which point rows return without touching game files.

### MT-28 — Remove is metadata-only

**Steps:** Open a scanned row → `REMOVE` → verify the folder on disk.
**Expected:** The row and its stored cover disappear from IndieHost; the game folder and all
game files remain untouched.

### MT-29 — Launch an MV game offline

**Precondition:** A legally owned RPG Maker MV game in a scanned folder; airplane mode on.
**Steps:** Tap its row → `PLAY`; repeat with a root-layout game (`index.html` beside `js/`).
**Expected:** The game loads fullscreen with no browser chrome; art and sound work; both
folder layouts launch. No copy of the game appears in app storage. **This is the check that
closes ADR-0007's open question about HTTPS interception without `INTERNET`.**

### MT-30 — An MZ game launches through the same seam

**Steps:** Scan an MZ folder and launch it.
**Expected:** It opens without a GameMaker pin and without a separate setup screen; the
row is launchable immediately after the scan.

### MT-31 — Remote requests are blocked visibly

**Steps:** In a game with a link or plugin that requests a remote URL, trigger that request.
**Expected:** A `Network requests are blocked` message appears; nothing loads from the
network; device traffic logs show no outbound request from IndieHost.

### MT-32 — Back, rotation and audio

**Steps:** During play press Back → `RESUME`, then Back → `LEAVE`; rotate the device;
background the app and return.
**Expected:** Back asks before leaving and never walks browser history; rotation does not
restart the game; audio pauses in the background and resumes on return.

### MT-33 — Broken WebView inputs

**Steps:** Scan folders with (a) no `index.html`, (b) a missing `rpg_core.js`, (c) a revoked
SAF grant; open each row.
**Expected:** Each case shows a specific error dialog and finishes without a crash; stored
library rows remain intact.

### MT-34 — Detected but not hosted engines

**Steps:** Scan folders containing a Ren'Py, a LÖVE and a ScummVM game; open each row.
**Expected:** All three appear with a `NOT HOSTED` badge and an engine-specific reason;
`PLAY` is disabled or shows the explanation; no attempt is made to start an unverified
runtime.

### MT-35 — Invalid or foreign pin requests

**Steps:** Ask a non-Butterscotch app to pin a shortcut through IndieHost; cancel a
Butterscotch pin request before it resolves; pin a `game-` shortcut normally.
**Expected:** The first two finish without accepting anything and appear in
`adb logcat -s IndieHostPin` as ignored/could-not-pin; only the valid request is accepted and
listed. A shortcut disabled or unpinned later disappears from the library.

### MT-36 — WebView hardening did not break offline games

**Steps:** Launch a scanned MV game with `blockNetworkLoads=true` and mixed content never
allowed, with the device offline.
**Expected:** The game still loads from `appassets.androidplatform.net` through the SAF
handler; no network request succeeds; the loading overlay clears after first page finish.
If local loads break, revisit the hardening flags before any release.

### MT-37 — TalkBack and reduced motion

**Steps:** With TalkBack on, traverse home → recents → library row → detail dialog → drawer;
open detail and confirm the Play/link/Remove actions are announced. Enable "Remove animations"
in system accessibility settings and reopen the app.
**Expected:** Every interactive item has a spoken name; decorative marks and letter
placeholders are not announced; focus reaches state-card actions; no animation runs when the
system motion scale is 0; all touch targets are at least 48 dp.

### MT-38 — Per-game WebView origin and SVG policy

**Steps:** Launch two scanned MV/MZ games in turn and use each game's save/options screen;
include a game folder containing an `.svg` asset.
**Expected:** Saves/options do not leak between games because each game has its own origin
port. SVG assets are not executed as scripts; they may fail to render, which is the accepted
trade-off until a sanitising policy exists. Both games still load offline.

### MT-39 — Missing scan rows are marked, not deleted

**Steps:** Scan a folder, remove one game folder from disk, re-scan the same tree, then put
the folder back and re-scan again.
**Expected:** The missing row shows `MISSING` with a re-pick hint; its cover, pin link and
added time are intact. After the folder returns, the same row becomes launchable again with
no duplicate.

### MT-41 — Disabled launches explain why

**Steps:** With Butterscotch uninstalled, tap a pinned GameMaker row; with no games folder
granted, tap a scanned MV row.
**Expected:** IndieHost does not try to start a missing runtime; a short message explains the
missing capability (runner or folder) and the row stays non-launchable.

### MT-40 — Scan progress and cancellation

**Steps:** Scan a large games folder and watch the message; press Home or leave the app
mid-scan and return.
**Expected:** The message shows folders visited and games found; leaving stops the walk
promptly; returning starts a fresh scan with a new run id and no duplicate rows.

---

## 4. Performance protocol

Measure on the baseline device, charger disconnected, screen brightness fixed, no other
apps in the foreground. Record every number in the compatibility matrix; do not round a
guess into a claim.

### 4.1 Cold start

```bash
adb shell am force-stop com.indiehost.launcher
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
```

Record `TotalTime` and `WaitTime`. Repeat three times; report the median. Target: ≤ 2.0 s
(NFR-01).

### 4.2 Warm resume

```bash
adb shell input keyevent KEYCODE_HOME
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
```

Record `TotalTime`. Target: ≤ 300 ms (NFR-02). The `singleTask` activity should take the
`onNewIntent` path, not recreate.

### 4.3 Drawer readiness

No instrumentation exists yet. Two acceptable methods:

- Record the screen while tapping `ALL APPS` and count frames until the grid is stable.
- Add a temporary `Log.d("IndieHostPerf", ...)` around `InstalledApps.load` and read
  `adb logcat -s IndieHostPerf`; remove it before committing.

Target: ≤ 1.5 s for ~100 apps (NFR-03).

### 4.4 Memory

```bash
adb shell dumpsys meminfo com.indiehost.launcher
```

Record PSS after a cold start, after opening the drawer, and after returning Home. Target:
≤ 120 MB idle (spec §11).

### 4.5 Game frame rate

IndieHost has no in-game overlay. Use one of:

- `adb shell screenrecord --time-limit 30 /sdcard/game.mp4`, pull the file, and count
  frames with `ffmpeg`/`ffprobe`.
- A benchmarking app that reports fps for the foreground game.
- The runner's own overlay, if it has one.

Record fps for the Ruins walk and a battle, plus audio quality (clean / occasional crackle /
constant). The Milestone 0 bar is ≥ 30 fps with clean audio.

### 4.6 Battery and thermal

For a 15-minute session:

```bash
adb shell dumpsys batterystats com.indiehost.launcher > battery.txt
adb shell dumpsys thermalservice > thermal.txt
```

Note whether the device throttles. This is informational; there is no hard budget yet.

---

## 5. Compatibility matrix template

The tracked template is [COMPATIBILITY.md](COMPATIBILITY.md). Copy its table into the
release notes as well. Never include game files, and leave a cell `unmeasured` rather than
guessing.

| Device | Android | RAM | Engine | Game | WAD | Walk fps | Battle fps | Audio | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Helio A22 (baseline) |  |  | GameMaker VM | Undertale 1.08 | 16 |  |  |  | M0 playable, unmeasured |
|  |  |  |  |  |  |  |  |  |  |

---

## 6. Release regression checklist

Before tagging or sideloading a build:

- [ ] `./gradlew :detect:test` — 19/19.
- [ ] `./gradlew :app:testDebugUnitTest` (43), `:library:testDebugUnitTest` (3) and
      `:runtimes:testDebugUnitTest` (7) — green.
- [ ] `./gradlew :app:assembleDebug` — success.
- [ ] MT-01 through MT-06 pass on the baseline.
- [ ] MT-07 through MT-09 pass with usage access granted and revoked.
- [ ] MT-10 through MT-13 pass.
- [ ] MT-14 and MT-15 pass with a VM and a YYC file.
- [ ] MT-21 through MT-28 pass on the baseline (SAF, merge, covers, revoke, reinstall).
- [ ] MT-29 through MT-33 pass offline with an MV and an MZ game.
- [ ] MT-34 passes for all three detection-only engines.
- [ ] MT-35 and MT-36 pass for pin validity and WebView hardening.
- [ ] MT-37 passes for TalkBack focus order and reduced motion.
- [ ] MT-38 passes for per-game origin isolation and SVG policy.
- [ ] MT-39 passes for missing-row marking and recovery.
- [ ] MT-40 passes for scan progress and cancellation.
- [ ] MT-41 passes for capability explanations.
- [ ] `GameDaoTest` runs on a device when `adb` is available.
- [ ] Cold start and resume recorded (at least once).
- [ ] No game data in `git status` or the APK.
- [ ] README/spec version and claims match the build.

---

## 7. Bug report template

Open an issue with:

```
Build:            0.4.0 (versionCode 5)
Device:           make/model
Android:          version / API level
RAM:
Home app:         IndieHost / other
Usage access:     granted / denied
Runtime:          Butterscotch version, installed from Play Store / APK
Game:             title and WAD version (do NOT attach the data file)

Steps:
1.
2.

Expected:

Actual:

Logcat (filtered):
  adb logcat -d -s IndieHostPin AndroidRuntime

Screenshots / screen recording:
```

**Never attach `data.win`, `game.droid`, `*.love`, `*.rpy*`, save files or any other
copyrighted game data.** A synthetic fixture or a redacted log is enough.

---

## 8. Known limitations of this test plan

- `GameDaoTest` is written but unrun; `LauncherApps`, pin requests, SAF grants and usage
  access are only exercised manually.
- No automated fps measurement; screen-recording frame counts are approximate.
- M1 scan/cover cases MT-21…MT-28 and M2 WebView cases MT-29…MT-33 were not executed in
  the container used to write them.
- No legal MV/MZ game copy is present in the repository, and game data must never be added
  to it. Device verification needs the maintainer's own copies.
- The emulator cannot validate Butterscotch performance or real shortcut ownership.
- Work profiles and multi-user devices are untested.
