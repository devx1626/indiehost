# Testing

> **Status:** Living document · **Last updated:** 2026-11-25
> **Related:** [spec.md](spec.md) · [PREREQUISITES](PREREQUISITES.md) · [ARCHITECTURE](ARCHITECTURE.md) · [ROADMAP](ROADMAP.md)

IndieHost has two test layers: host-side JVM tests for the pure detector, and a manual
device protocol for everything that depends on Android's launcher, shortcut and usage
APIs. There are no instrumented tests yet; do not pretend the manual matrix is automated.

---

## 1. What is tested, and where

| Layer | Scope | Tool | Status |
| --- | --- | --- | --- |
| JVM unit tests | `GameMakerWad` parsing, `EngineDetector` fingerprints, blockers | JUnit 4 | **12 tests** (reported 12/12 in MILESTONE-0; re-run before release) |
| Instrumented tests | Activities, `LauncherApps`, pin flow, Room DAO (M1) | AndroidX Test | **None** |
| Manual device tests | Home role, pin, launch, recents, drawer, permissions | Human + adb | Required per release |
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

The fixtures are synthetic; no real game data is used.

---

## 2. Running the tests

```bash
# Host-side detector tests (no device, no Android SDK needed for this module alone
# once Gradle/JDK are available).
./gradlew :detect:test

# Everything that can run without a device.
./gradlew test

# Build the APK used for the manual protocol.
./gradlew :app:assembleDebug
```

Expected: `:detect:test` reports 12 passed, 0 failed. A failure here blocks any release.

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
IndieHost's library after returning; `adb logcat -s IndieHostPin` shows `Pinned game-…`.
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

Copy this table into the release notes or a future `COMPATIBILITY.md`. Never include game
files.

| Device | Android | RAM | Engine | Game | WAD | Walk fps | Battle fps | Audio | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Helio A22 (baseline) |  |  | GameMaker VM | Undertale 1.08 | 16 |  |  |  | M0 playable, unmeasured |
|  |  |  |  |  |  |  |  |  |  |

---

## 6. Release regression checklist

Before tagging or sideloading a build:

- [ ] `./gradlew :detect:test` — 12/12.
- [ ] `./gradlew :app:assembleDebug` — success.
- [ ] MT-01 through MT-06 pass on the baseline.
- [ ] MT-07 through MT-09 pass with usage access granted and revoked.
- [ ] MT-10 through MT-13 pass.
- [ ] MT-14 and MT-15 pass with a VM and a YYC file.
- [ ] Cold start and resume recorded (at least once).
- [ ] No game data in `git status` or the APK.
- [ ] README/spec version and claims match the build.

---

## 7. Bug report template

Open an issue with:

```
Build:            0.3.0-steam (versionCode 3)
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

- No instrumented tests; `LauncherApps`, pin requests and usage access are only exercised
  manually.
- No automated fps measurement; screen-recording frame counts are approximate.
- The emulator cannot validate Butterscotch performance or real shortcut ownership.
- Work profiles and multi-user devices are untested.
