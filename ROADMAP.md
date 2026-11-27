# Roadmap

> **Status:** Living document · **Last updated:** 2026-11-27
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

## Milestone 1 — Real library — **In progress (code complete, device verification pending)**

**Goal:** IndieHost stops borrowing Butterscotch's pins as its only library and owns a
persistent, scannable game collection.

### Work

1. **Room** — done
   - [x] `GameEntity` (`id`, `title`, `folderPath`, `engine`, `wadVersion`, `coverPath`,
     `addedAt`, `source`, `linkedShortcutId`, `treeUri`, `runnable`, `blocker`).
   - [x] DAO + database + migration policy (version 1; destructive migration allowed only
     during M1).
   - [x] `RoomGameRepository : GameRepository`; wired into `MainActivity` (plain threads,
     no ViewModel).
   - [x] Host mapping test; instrumented DAO test written but **not run** (broken `adb`).

2. **Folder scan** — done
   - [x] SAF `OpenDocumentTree` with persisted read permission (`GameFolderStore`).
   - [x] Walk two levels through the `GameFolder` seam, run `EngineDetector` on candidates.
   - [x] Stable `scan-` ids; upsert only; skipped folders carry reasons; revoked grants
     produce a visible message and leave rows untouched.
   - [x] Missing/disappeared children are skipped, not followed as symlinks.

3. **Library UI** — done
   - [x] Rows driven by `mergeLibrary(games, pins)`, not only pinned shortcuts.
   - [x] Detail panel: title, engine, WAD, path, launch, explicit pin link, remove.
   - [x] Empty state explains both sources; GameMaker without a live pin is not launchable.
   - [x] Pinned-shortcut path kept as a first-class source.

4. **Cover art** — done
   - [x] `OpenDocument(image/*)` → `CoverStore` → app-private WebP with a hashed name.
   - [x] Bounds decode + subsample + scale to ≤ 512 px, on worker threads.
   - [x] Only user-supplied art; no extraction or redistribution of game assets.

5. **Saves — deferred to M2**
   - M1 hosts no engine, so there is nothing for IndieHost to save. For engines hosted
     from M2, save to a shared, documented directory.
   - For Butterscotch-hosted games, saves belong to Butterscotch; document the limitation
     and do not pretend to manage them.

### Exit criteria

Implementation is complete; criteria stay open until observed on the baseline device.
Host evidence: `:detect:test` 19/19, `:library:testDebugUnitTest` 3/3,
`:app:testDebugUnitTest` 16/16, `:app:assembleDebug` successful.

- [ ] Games survive process death and launcher updates; a full uninstall is restored by
      re-scanning the same folder (metadata is app-private, not backed up). — MT-27
- [ ] A SAF folder scan imports at least GameMaker and MV/MZ folders. — MT-21, MT-22
- [ ] Cover art survives restart and is downsampled. — MT-25
- [ ] Revoking the SAF permission produces a clear message, not a crash. — MT-26
- [ ] DAO tests and the detector tests pass. — DAO test written but unrun; detector 19/19
- [ ] Manual test matrix updated (MT-21…MT-28); no regression in the pin flow. — MT-23

**Blocker:** this container has no working `adb`, so instrumented and manual cases could
not be executed. Do not mark M1 done on a compile alone.

### Risks

- SAF tree persistence across reboots/OEMs.
- Scoped-storage rules for saves.
- Room adds KSP/kapt to the build; keep `:detect` untouched.
- Library UI can become slow if icons/covers are decoded on the main thread.

---

## Milestone 2 — RPG Maker MV/MZ in a WebView — **In progress (code complete, device verification pending)**

**Goal:** prove the "one `RuntimeAdapter` per engine" claim with a runtime that is certain
to work, and give the launcher a second real engine.

### Work

1. **Adapter** — done
   - [x] `RpgMakerRuntimeAdapter` for MV and MZ, registered twice in `RuntimeRegistry`.
   - [x] `RuntimeAdapter.launch(activity, game, shortcut?)` keeps GameMaker's pin handoff;
     GameMaker and RPG Maker share the registry seam.
   - [x] Validation of `index.html`/core JS moved into `GameWebViewActivity`, because a SAF
     tree is not a `File`; `prepare` documents that instead of pretending to pre-flight.

2. **Loading strategy** — done in [ADR-0007](docs/adr/0007-webview-offline-assets.md)
   - [x] `WebViewAssetLoader` + SAF `PathHandler`; no `INTERNET` permission, no copy, no
     localhost server.
   - [ ] Device fallback (copy into app storage) is named but **not** implemented; needed
     only if WebView refuses intercepted HTTPS without `INTERNET`.
   - [x] A localhost server remains rejected.

3. **WebView configuration** — done
   - [x] JavaScript and DOM storage on; file/content access and universal file access off.
   - [x] No JavaScript bridge; popups and multiple windows disabled.
   - [x] Off-origin subresources get a blocked response; off-origin navigation shows a
     visible message and never reaches the network.

4. **Game lifecycle** — done
   - [x] Fullscreen, keep screen on, audio focus request/abandon on resume/pause.
   - [x] Back = leave confirmation, never browser history.
   - [x] `configChanges` avoids a rotation restart.
   - [ ] Save export (FR-20) is **deferred beyond M2**; WebView DOM storage is app-private
     and the runtime is not yet device-proven. Scope change recorded in the M2 design.

### Exit criteria

Host evidence: `:runtimes:testDebugUnitTest` 3/3, `:app:testDebugUnitTest` 21/21,
`:detect:test` 19/19, `:library:testDebugUnitTest` 3/3, debug APK built.

- [ ] One MV game and one MZ game launch offline from a scanned folder. — MT-29, MT-30
- [ ] No remote request succeeds; no `INTERNET` permission added. — MT-31; manifest checked
- [ ] Back/pause/audio behave like a game. — MT-32
- [ ] The adapter is selected through the same seam as GameMaker. — registry tests
- [ ] Manual tests cover a missing `index.html`, a broken JS file and a revoked SAF grant. —
      MT-33, unrun

**Blocker:** no working `adb`, no legal MV/MZ game copy in this environment. If WebView
refuses the intercepted HTTPS URL without `INTERNET`, implement ADR-0007's copy fallback.

### Risks

- MV/MZ plugins may assume a browser/server environment.
- WebView performance on the A22, especially canvas-heavy games.
- Save-data durability and export.
- Security of untrusted HTML/JS; see [SECURITY.md](SECURITY.md).

---

## Milestone 3 — Ren'Py, LÖVE, ScummVM — **Done (detection-only, no runtime added)**

**Goal:** either host the remaining engines or say clearly why not. No silent detection.

### Work

1. **Runtime model decided per engine** — done
   - [x] [ADR-0008](docs/adr/0008-renpy-detected-not-hosted.md): RAPT builds a per-game APK;
     no shared runtime exists, so IndieHost does not host it.
   - [x] [ADR-0009](docs/adr/0009-love-handoff-not-implemented.md): a generic player can open
     `.love` files, but the handoff intent is unverified — no adapter shipped.
   - [x] [ADR-0010](docs/adr/0010-scummvm-handoff-not-implemented.md): a `scummvm:<target>`
     intent exists, but the target must first be configured inside ScummVM.

2. **What the decisions allow** — done
   - [x] Each engine gets its own library reason and `NOT HOSTED` badge; Launch disabled.
   - [x] Detection unchanged; no unresolved runtime was added.

3. **Product claim** — done
   - [x] README table and spec FR-24 now say "detected only" for these engines.

### Exit criteria

- [x] Every engine in `Engine` has a documented route: launch, hand off, or not hostable.
- [x] The library shows the reason for a detected-but-unsupported game. — MT-34
- [x] No engine is silently dropped. — `LibraryMergeTest` asserts all three reasons
- [x] ADRs record the decisions and their consequences.

### Risks

- The "one adapter per engine" model may not fit build-tool runtimes; the product thesis
  may need to narrow to "launcher + handoff" for those engines.
- Per-game APK detection needs a stable package/id convention that Ren'Py/LOVE do not
  guarantee.

---

## Milestone 4 — Compatibility and performance — **In progress (template only, measurements blocked)**

**Goal:** replace "it runs" with measured claims.

### Work

1. **Compatibility matrix** — template ready, data blocked
   - [x] [COMPATIBILITY.md](COMPATIBILITY.md) created with the required columns and a
     "no measured data" banner; detection-only engines are listed as such.
   - [ ] At least five GameMaker titles across WAD versions 8–17, plus an MV and MZ title.
   - [x] Commit device data only; never game files.

2. **Performance protocol**
   - Cold start, resume, drawer load, recents refresh, idle PSS.
   - Game fps measured externally (screen recording frame count or a benchmarking tool);
     IndieHost has no in-game overlay.
   - Battery/thermal notes for a 15-minute session.

3. **Publish findings**
   - Update README's status and spec §11 with measured numbers.
   - Remove or qualify any claim that is not backed by a test.

### Exit criteria

- [ ] Matrix filled with at least five titles — **blocked: no device or legal game copy**.
- [ ] Cold start, resume and drawer numbers recorded on the baseline — **blocked**.
- [ ] README/spec claims match the measurements — currently qualified in COMPATIBILITY.md.
- [ ] Known-bad configurations documented — the section is deliberately empty until tested.

**Blocker:** the container has no working `adb` and no game data. No performance number may
be claimed until the maintainer fills the template on the baseline phone.

---

## Backlog

Small, high-value work that does not need a milestone. Pull items forward when touching the
area.

| Priority | Item | Why | Where |
| --- | --- | --- | --- |
| P0 | Run MT-21…MT-33 and `GameDaoTest` on a device | M1/M2 exit criteria are unobserved | `TESTING.md`, `library/src/androidTest` |
| P0 | Confirm WebView loads `appassets` without `INTERNET` | Decides whether ADR-0007's copy fallback is needed | `GameWebViewActivity.kt` |
| P1 | Replace destructive Room migration with explicit migrations | Would drop library rows on a schema change | `IndieHostDatabase.kt` |
| P1 | Decrypt MV/MZ encrypted assets | Games with `.rpgmvp`/`.rpgmvo` show broken art | `SafAssetPathHandler.kt` |
| P2 | Export MV/MZ saves outside private storage | FR-20 deferred beyond M2 | `GameWebViewActivity.kt` |
| P2 | Add `mipmap-xxxhdpi` legacy icons | Pre-26 xxxhdpi devices upscale | `res/mipmap-xxxhdpi` |
| P2 | Add predictive-back opt-in for target 34 | Future-proof back handling | `AndroidManifest.xml` |
| ~~P2~~ | ~~Replace deprecated `PackageManager` icon/label calls~~ | Done: drawer reads `LauncherActivityInfo` label/icon directly | `InstalledApps.kt` |
| P2 | Introduce a ViewModel + coroutines for loading | Testability, lifecycle safety | `MainActivity.kt` |
| P3 | Instrumented tests for `LauncherApps`/pin flows | System integration is untested | `app/src/androidTest` |
| P3 | Application-id rename | Placeholder id must change before broad distribution | manifest + repin |
| ~~P3~~ | ~~Add a `LICENSE` file~~ | Done: MIT, 2026-11-27 | [LICENSE](LICENSE) |
| ~~P3~~ | ~~Rename internal palette identifiers~~ | Done: `IndieTheme`/`Indie`, 2026-11-27 | app theme |
| P3 | Work-profile support | Out of scope until requested | `InstalledApps`/`Recents` |

---

## Prioritisation rule

1. Anything that violates a hard constraint (CON-01…07) first.
2. Anything that causes data loss or an ANR second.
3. Anything that unblocks a milestone third.
4. Polish last.

When a milestone's scope changes, update this file and [spec.md](spec.md) in the same
change. A roadmap item without an exit criterion is a wish, not a task.
