# Milestone 0 — Can a GameMaker game run natively on a Helio A22?

**Status: code complete and building; device verification is the one step I could not do from here.**
Our APK installs, reads a `data.win` and reports its WAD version. The runner itself
(Butterscotch) has to be driven on your phone by you, because it does not accept a game
over an Intent — details in "The blocker" below.

---

## 1. Research findings

### Butterscotch — your preferred runtime

| Question | Answer |
| --- | --- |
| Licence | **AGPL-3.0** (also MIT for some support code). Forking or linking it makes the combined work AGPL-3.0. |
| What it is | A from-scratch **C reimplementation** of the GameMaker runner — it does not ship YoYo's proprietary runner library. |
| Bytecode (WAD) support | **WAD 8 through 17.** No YYC and no GMRT (the newest runtime) — it needs the `CODE` chunk that only VM builds have. |
| Android | **Yes, first-class.** Two distributions: a Play Store app (`net.perfectdreams.butterscotch`, current build 2026.06.21-1, `minSdk 24`, `arm64-v8a` + `armeabi-v7a`, OpenGL ES + modern GL backend, miniaudio) and nightly `butterscotch-android.zip` builds from the repo's CI. |
| Prebuilt APK faster than building? | **Yes, and that is what we used.** No NDK/SDL build needed to get a yes/no answer. Building it ourselves is only worth doing once we need to change how it receives a game. |

Undertale 1.08 is GMS 1.4.1804 = **WAD 16**, comfortably inside that range, and is
Butterscotch's reference target. So the version-match requirement is satisfied *if* your
`data.win` is a VM build — confirm that with UndertaleModTool (see step 2).

### Droidtale — reference implementation

Droidtale injects your `data.win` into a copy of `UndertaleWrapper.apk` as `game.droid`,
re-signs it with ZipSigner, and ships a gamepad-remap layer. It works by redistributing
**YoYo's proprietary runner without permission**, it is deprecated, its v2 builder is
"still only for Windows", and it targets an older Undertale (1.05a). Useful as a reference
for the packaging trick; not something to build a product on. Your instinct to avoid it
is correct.

### What this means for the product

The re-hosting premise is real: the game logic really is portable bytecode, and a
permissively-licensed runner exists that runs it on ARM. The open question is only
whether the **CPU cost of interpreting that bytecode on four A53s** leaves a playable
frame rate. That is exactly what this milestone measures.

---

## 2. What I built

Four Gradle modules, as directed:

```
:app       Compose, one screen. Pick data.win -> copy into cacheDir/import ->
           fingerprint -> show engine / WAD version / blocker ->
           "Open GameMaker runner (Butterscotch)".
:library   Game model + in-memory repository. Room deferred to M1.
:detect    Pure Kotlin, no Android deps. IFF/FORM "GEN8" reader, bytecode version at
           absolute offset 17, top-level chunk walk for the CODE chunk (its absence =
           YYC/GMRT), engine fingerprinting with explicit blockers.
:runtimes  RuntimeAdapter { canRun, prepare, launch, supportsSaves } and
           GameMakerRuntimeAdapter. Adding an engine later is one class.
```

Verification actually run:

- `:detect` unit tests: **12/12 pass** (JUnit 4, run with kotlinc directly). The tests
  caught two genuine bugs, both fixed: the chunk walker started at offset 12 and read a
  chunk *length* as a chunk id, and one test fixture declared an inconsistent GEN8 chunk
  length.
- `:app:assembleDebug`: **BUILD SUCCESSFUL**, produces `app-debug.apk` (9.4 MB,
  `minSdk 24`, `targetSdk 34`, package `com.indiehost.launcher`).

Copy of the APK: `dist/indiehost-milestone0.apk`
(sha256 `d0409c713771c343b1a09bb33aedb16ebe4bcbef0c27a78bf1a7560c2f7dfc33`). It is not
committed: `dist/*.apk` is ignored, so build it with `./gradlew :app:assembleDebug`.

### The blocker

Butterscotch's `GameActivity` is `android:exported="false"` and expects the game to be
imported through **its own UI**. No Intent can hand it a folder. So the launcher cannot
yet say "play this game" — it can only validate the game and open the runner. That is a
real architectural gap, not a bug I can fix in our app without either:

1. an upstream patch to Butterscotch that accepts a `content://` Uri (licence-clean, needs
   their agreement), or
2. our own fork of ButterscotchAndroid — works, but our combined app becomes **AGPL-3.0**
   and we must publish the source.

I deliberately did **not** link or bundle the runner, so nothing in this repo is currently
under AGPL obligations.

---

## 3. Run it on your phone (about 5 minutes)

Game data stays on your phone. Nothing is uploaded, and `games/`, `*.win`, `*.droid`,
`*.love`, `*.rpy*`, `*.ogg`, `*.exe` are all in `.gitignore`.

```bash
# 1. Install Butterscotch (the runner)
adb install butterscotch.apk          # or install from Play Store, package
                                      # net.perfectdreams.butterscotch

# 2. Install our Milestone 0 app
adb install app/build/outputs/apk/debug/app-debug.apk

# 3. Push your game file (your own copy; not committed)
adb push data.win /sdcard/Download/data.win
```

Then on the phone:

1. Open **IndieHost**, tap **Pick data.win**, choose the file from Download. Read the
   line "WAD / bytecode version: N".
   - If it says **Blocked / no CODE chunk**, your copy is YYC and no bytecode runner can
     run it. Stop there and tell me — we'd need a VM copy of the game.
2. Open **Butterscotch** and import the same `data.win` through its own file picker.
3. Play for a couple of minutes. Note: the opening walk cycle, the battle screen
   (bullet-hell sections are the worst case), and whether audio stutters.

Also please note your **Android version** and **RAM** (Settings → About phone).

### What I have *not* verified

I have no adb connection to your phone from this cloud session, so I have not installed
the app, launched the game, or measured a single frame. Everything above about
performance is reasoning, not measurement. If it fails, the failure is mine to fix — send
me what the screen or `adb logcat` says.

---

## 4. Recommendation: stay on GameMaker, but timebox the decision

Keep going with GameMaker, and make the phone test the gate — because it costs five
minutes and it is the only thing that can kill the premise.

The reasoning:

- **It is the product thesis.** If VM bytecode can't hold a frame rate on an A53, then
  "re-host indie engines at full speed" is really "wrap WebViews and a few 2D runtimes",
  and the launcher should be designed around that from day one. Learning it now is
  cheap; learning it after building the library UI is not.
- **The MV path is not a fallback we lose by waiting.** RPG Maker MV is an HTML5 game in
  a WebView: far safer on this GPU, far less interesting, and — crucially — it needs
  almost no research we haven't already done. If GameMaker fails, pivoting costs days,
  not weeks.
- **Butterscotch is unusually strong evidence in favour.** It is not a wrapper around
  Wine or a translation layer; it is a hand-written C runner whose author already targets
  Undertale specifically, including on Vita and Switch-class hardware. Undertale is also
  a 2D 30 fps game with modest draw load, which is the best case for a weak GPU.

Where I'd set the bar: **30 fps sustained in the Ruins walk and in a battle, with audio
not crackling.** If we get that, GameMaker is the flagship engine. If we get 15–20 fps
with heavy stutter, the honest answer is that the A22 cannot interpret this bytecode and
the launcher's headline feature should become "MV, Ren'Py, LOVE, ScummVM" — all of which
are smoother to ship anyway.

---

## 5. Milestone 1 (proposal)

Ordered by risk, not by visibility:

1. **Close the handoff gap.** Fork `ButterscotchAndroid` (accept AGPL-3.0 for the combined
   work, publish sources) *or* land the `content://` patch upstream. Success criterion:
   our app taps a game tile and the game starts, with no second file picker. This is the
   single thing that turns the demo into a launcher.
2. **Real library.** Room for games, per-game save directories, cover art, and a scan of a
   user-chosen games folder. Saves must live outside the runtime's private storage so a
   reinstall doesn't destroy them.
3. **Second engine, deliberately cheap.** RPG Maker MV in a WebView — one `RuntimeAdapter`
   — to prove the "one class per engine" claim with a runtime we know will work.
4. **Only then** the compatibility matrix: which WAD versions and which of the popular
   GameMaker titles actually hold 30 fps on the A22.
