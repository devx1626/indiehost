# Milestone 0 — Can a GameMaker game run natively on a Helio A22?

**Status: passed on the device.** Undertale runs in Butterscotch on the Helio A22 at a
usable frame rate — your words, not a measurement: I never got frame numbers, because I
have no adb connection to your phone.

This document keeps the Milestone 0 reasoning as it was written. One part of it is now
out of date: it said a launcher could never hand a game to Butterscotch. That was true of
the *Intent* route and false as a conclusion — see "The handoff" below.

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
:app       Compose. Pick data.win -> copy into cacheDir/import -> fingerprint ->
           show engine / WAD version / blocker -> "Open GameMaker runner".
           Since 0.2.0 it is a home screen: library, recents and app drawer.
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

### The handoff (this section replaces the old "blocker")

Butterscotch's `GameActivity` is `android:exported="false"`, so no Intent can hand it a
folder. When this milestone was written I read that as "a launcher can never start a
game", and it is not: it only rules out the direct-Intent route.

The route that works needs no fork and no AGPL obligation:

1. IndieHost declares itself a **home app** (`CATEGORY_HOME`).
2. That role grants it **shortcut host** permission, and it declares
   `android.content.pm.action.CONFIRM_PIN_SHORTCUT`, so it can accept pinned shortcuts.
3. In Butterscotch you open a game's settings and tap **Add Shortcut to Home Screen**.
   Butterscotch asks to pin a shortcut with id `game-<uuid>`; IndieHost is the launcher
   that answers, so the pin belongs to IndieHost.
4. IndieHost lists those shortcuts through `LauncherApps` and starts one with
   `startShortcut` — which runs Butterscotch's own `GameActivity` as its own process.
   Nothing is linked into our app, nothing is repackaged, and the game files never leave
   your phone.

A pin is recorded against the accepting launcher, so games pinned while another launcher
was active do not appear — they have to be re-pinned with IndieHost as home. That is the
one rough edge, not a wall.

The routes I did **not** take, and why: forking ButterscotchAndroid would make the
combined work AGPL-3.0 with no extra capability, and reading the shortcut Intents
Butterscotch fires is not possible — Android strips them for other apps.

---

## 3. Run it on your phone (about 5 minutes)

Game data stays on your phone. Nothing is uploaded, and `games/`, `*.win`, `*.droid`,
`*.love`, `*.rpy*`, `*.ogg`, `*.exe` are all in `.gitignore`.

```bash
# 1. Install Butterscotch (the runner)
adb install butterscotch.apk          # or install from Play Store, package
                                      # net.perfectdreams.butterscotch

# 2. Install our app
adb install app/build/outputs/apk/debug/app-debug.apk

# 3. Push your game file (your own copy; not committed)
adb push data.win /sdcard/Download/data.win
```

Then on the phone:

1. Open **IndieHost**, tap **CHECK DATA.WIN**, choose the file from Download. Read the
   line "WAD / bytecode version: N".
   - If it says **Blocked / no CODE chunk**, your copy is YYC and no bytecode runner can
     run it. Stop there and tell me — we'd need a VM copy of the game.
2. Open **Butterscotch** and import the same `data.win` through its own file picker.
3. In Butterscotch, open that game's settings and tap **Add Shortcut to Home Screen**.
4. Press Home: the game is in IndieHost's library. Tap it and it starts directly.
5. Play for a couple of minutes. Note: the opening walk cycle, the battle screen
   (bullet-hell sections are the worst case), and whether audio stutters.

Also please note your **Android version** and **RAM** (Settings → About phone).

### What I have *not* verified

I have no adb connection to your phone from this cloud session, so I have not installed
the app, launched the game, or measured a single frame. You confirmed the game is
playable; the exact frame rate, the stutter behaviour in battles, and the audio are still
unmeasured. If something is wrong, send me what the screen or `adb logcat` says.

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

1. ~~**Close the handoff gap.**~~ **Done, by the shortcut route.** IndieHost is the home
   app, holds shortcut-host permission, and starts Butterscotch's pinned games with
   `startShortcut`: tap a tile, the game starts, no second file picker. No fork, no AGPL
   obligation. See "The handoff" in section 2.
2. **Real library.** Room for games, per-game save directories, cover art, and a scan of a
   user-chosen games folder. Saves must live outside the runtime's private storage so a
   reinstall doesn't destroy them.
3. **Second engine, deliberately cheap.** RPG Maker MV in a WebView — one `RuntimeAdapter`
   — to prove the "one class per engine" claim with a runtime we know will work.
4. **Only then** the compatibility matrix: which WAD versions and which of the popular
   GameMaker titles actually hold 30 fps on the A22.
