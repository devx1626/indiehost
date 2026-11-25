# Roadmap

> **Status:** Living document · **Last updated:** 2026-11-25
> **Related:** [spec.md](spec.md) · [MILESTONE-0](MILESTONE-0.md) · [ARCHITECTURE](ARCHITECTURE.md) · [TESTING](TESTING.md)

Ordered by risk, not visibility. Each milestone has a definition of done; a milestone is
not complete because the code compiles, but because its exit criteria were observed on the
baseline device.

Legend: **Done** · **In progress** · **Planned** · **Blocked** · **Backlog**

---

## Milestone 0 — Can GameMaker run natively on an A22? — **Done**

**Goal:** prove the re-hosting premise on the weakest realistic phone.

**Outcome:** Undertale (GameMaker VM, WAD 16) runs in Butterscotch on the Helio A22 at a
usable frame rate, confirmed by the device owner. The exact frame rate, battle stutter and
audio quality are **not measured**; see [MILESTONE-0.md](MILESTONE-0.md).

**What exists:** four modules, engine detection with 12 host tests, a debug APK, and the
shortcut handoff that lets a launcher start a pinned Butterscotch game without a fork.

---

## Engineering baseline — **In progress**

**Goal:** make every change verifiable before M1 adds persistence.

- [x] JDK 17, Gradle 8.7 and SDK 34 build the debug APK; the aarch64 path is recorded in
  [PREREQUISITES §1.3](PREREQUISITES.md#13-linux-on-arm64).
- [x] `:detect:test` and `:app:assembleDebug` are wired into
  [.github/workflows/android.yml](.github/workflows/android.yml).
- [x] The workflow is green on `main` (run 45705981692).
- [x] Hardening: off-main-thread `data.win` check, Butterscotch-only pins, shortcut icons,
  `strings.xml`, ≥ 48 dp touch targets.

**Exit criteria:** the workflow is green on `main`, the remaining hardening items are
closed, and `:detect:test` plus the APK build stay green.

---

## Milestone 1 — Real library — **Planned**

**Goal:** IndieHost stops borrowing Butterscotch's pins as its only library and owns a
persistent, scannable game collection.

### Work

1. **Room**
   - `GameEntity` (`id`, `title`, `folderPath`, `engine`, `wadVersion`, `coverPath`,
     `addedAt`).
   - DAO + database + migration policy (version 1; destructive migration allowed only
     during M1).
   - `RoomGameRepository : GameRepository`; wire `MainActivity`/a ViewModel to it.
   - DAO tests (Robolectric or instrumented).

2. **Folder scan**
   - SAF `OpenDocumentTree` with persisted read permission.
   - Walk one or two levels, run `EngineDetector` on candidate folders.
   - Import supported games; show skipped folders with reasons.
   - Handle revoked permission, missing folder and symlinks safely.

3. **Library UI**
   - Grid or rows driven by the repository, not only pinned shortcuts.
   - Game detail: title, engine, WAD, path, launch, remove.
   - Empty state that explains both sources: pinned Butterscotch games and scanned
     folders.
   - Keep the pinned-shortcut path as a first-class source; do not regress it.

4. **Cover art**
   - Pick an image per game; copy into app storage with a stable name.
   - Downsample before storing; never store a full-resolution screenshot.
   - Do not extract or redistribute copyrighted art.

5. **Saves**
   - For engines IndieHost hosts (M2+), save to a shared, documented directory that
     survives a launcher reinstall.
   - For Butterscotch-hosted games, saves belong to Butterscotch; document the limitation
     and do not pretend to manage them.

### Exit criteria

- [ ] Games survive process death and a launcher reinstall.
- [ ] A SAF folder scan imports at least GameMaker and MV/MZ folders.
- [ ] Cover art survives restart and is downsampled.
- [ ] Revoking the SAF permission produces a clear message, not a crash.
- [ ] DAO tests and the existing 12 detector tests pass.
- [ ] Manual test matrix updated; no regression in the pin flow.

### Risks

- SAF tree persistence across reboots/OEMs.
- Scoped-storage rules for saves.
- Room adds KSP/kapt to the build; keep `:detect` untouched.
- Library UI can become slow if icons/covers are decoded on the main thread.

---

## Milestone 2 — RPG Maker MV/MZ in a WebView — **Planned**

**Goal:** prove the "one `RuntimeAdapter` per engine" claim with a runtime that is certain
to work, and give the launcher a second real engine.

### Work

1. **Adapter**
   - `RpgMakerRuntimeAdapter : RuntimeAdapter` for `RPGMAKER_MV` and `RPGMAKER_MZ`.
   - Introduce the adapter registry (`Map<Engine, RuntimeAdapter>`), since this is the
     second adapter.
   - `prepare` validates `index.html` and the core JS; `launch` starts a new
     `GameWebViewActivity`.

2. **Loading strategy** (decide in an ADR)
   - **Preferred:** `WebViewAssetLoader` over the SAF tree / copied folder, so there is no
     `INTERNET` permission and no localhost server.
   - **Fallback:** copy the game into app storage (simple, but duplicates data).
   - **Rejected unless necessary:** a localhost HTTP server (adds a socket and a permission
     story).

3. **WebView configuration**
   - JavaScript on, DOM storage on.
   - File access off, universal access from file URLs off, mixed content never allowed.
   - No JavaScript bridge unless a concrete feature requires it; if added, expose one
     narrow method and validate input.
   - Block remote requests; a game that needs the network fails visibly.

4. **Game lifecycle**
   - Fullscreen/immersive, keep screen on, audio focus.
   - Back = pause/exit confirmation, not browser history.
   - Save data in WebView storage; document where it lives and how to export it.
   - Handle rotation without restarting the game if feasible.

### Exit criteria

- [ ] One MV game and one MZ game launch offline from a scanned folder.
- [ ] No remote request succeeds; no `INTERNET` permission added.
- [ ] Back/pause/audio behave like a game.
- [ ] The adapter is selected through the same seam as GameMaker.
- [ ] Manual tests cover a missing `index.html`, a broken JS file and a revoked SAF grant.

### Risks

- MV/MZ plugins may assume a browser/server environment.
- WebView performance on the A22, especially canvas-heavy games.
- Save-data durability and export.
- Security of untrusted HTML/JS; see [SECURITY.md](SECURITY.md).

---

## Milestone 3 — Ren'Py, LOVE, ScummVM — **Planned**

**Goal:** either host the remaining engines or say clearly why not. No silent detection.

### Work

1. **Decide the runtime model per engine (ADR required).**
   - Ren'Py: RAPT produces a per-game APK, not a shared runtime. Options: detect an
     installed per-game APK and launch it; orchestrate a build (likely out of scope); or
     mark "detected, not hostable".
   - LOVE: same shape — the official Android build is per-game.
   - ScummVM: the Android app is generic; research whether it exposes a public intent for a
     game path. If not, open ScummVM and guide the user.

2. **Implement what the decision allows**
   - Adapter or explicit "not hostable" result with a reason shown in the library.
   - Detection stays as-is unless a signature proves unreliable.

3. **Update the product claim**
   - The README table and spec §3 must match reality. If an engine is detection-only, say
     so in the same sentence as the runtime.

### Exit criteria

- [ ] Every engine in `Engine` has a documented route: launch, hand off, or not hostable.
- [ ] The library shows the reason for a detected-but-unsupported game.
- [ ] No engine is silently dropped.
- [ ] ADRs record the decisions and their consequences.

### Risks

- The "one adapter per engine" model may not fit build-tool runtimes; the product thesis
  may need to narrow to "launcher + handoff" for those engines.
- Per-game APK detection needs a stable package/id convention that Ren'Py/LOVE do not
  guarantee.

---

## Milestone 4 — Compatibility and performance — **Planned**

**Goal:** replace "it runs" with measured claims.

### Work

1. **Compatibility matrix**
   - At least five GameMaker titles across WAD versions 8–17.
   - Columns: device, Android, RAM, engine, game, WAD, fps (walk/battle), audio, notes.
   - Commit device data only; never game files.

2. **Performance protocol**
   - Cold start, resume, drawer load, recents refresh, idle PSS.
   - Game fps measured externally (screen recording frame count or a benchmarking tool);
     IndieHost has no in-game overlay.
   - Battery/thermal notes for a 15-minute session.

3. **Publish findings**
   - Update README's status and spec §11 with measured numbers.
   - Remove or qualify any claim that is not backed by a test.

### Exit criteria

- [ ] Matrix committed with at least five titles.
- [ ] Cold start, resume and drawer numbers recorded on the baseline.
- [ ] README/spec claims match the measurements.
- [ ] Known-bad configurations documented.

---

## Backlog

Small, high-value work that does not need a milestone. Pull items forward when touching the
area.

| Priority | Item | Why | Where |
| --- | --- | --- | --- |
| P2 | Add `mipmap-xxxhdpi` legacy icons | Pre-26 xxxhdpi devices upscale | `res/mipmap-xxxhdpi` |
| P2 | Add predictive-back opt-in for target 34 | Future-proof back handling | `AndroidManifest.xml` |
| P2 | Replace deprecated `PackageManager` icon/label calls | Future API removal | `InstalledApps.kt` |
| P2 | Introduce a ViewModel + coroutines for loading | Testability, lifecycle safety | `MainActivity.kt` |
| P3 | Instrumented tests for `LauncherApps`/pin flows | System integration is untested | `app/src/androidTest` |
| P3 | Release signing + application-id rename | Required before any public build | Gradle + manifest |
| P3 | Add a `LICENSE` file | Default copyright blocks reuse | root |
| P3 | Rename `-steam` codename and `SteamTheme` | Trademark/branding (CON-07) | build + theme |
| P3 | Work-profile support | Out of scope until requested | `InstalledApps`/`Recents` |

---

## Prioritisation rule

1. Anything that violates a hard constraint (CON-01…07) first.
2. Anything that causes data loss or an ANR second.
3. Anything that unblocks a milestone third.
4. Polish last.

When a milestone's scope changes, update this file and [spec.md](spec.md) in the same
change. A roadmap item without an exit criterion is a wish, not a task.
