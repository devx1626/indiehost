# AGENTS.md

> **Status:** Operating rules · **Last updated:** 2026-11-25
> **Audience:** AI coding agents working in this repository.

Read this before changing anything. It is the short version of the constraints that are
easy to violate and expensive to undo.

---

## 1. Read order

1. [spec.md](spec.md) — normative requirements and hard constraints.
2. [ARCHITECTURE.md](ARCHITECTURE.md) — module map, flows, contracts, tech debt.
3. [ROADMAP.md](ROADMAP.md) — what is in scope now and what is not.
4. [TESTING.md](TESTING.md) — what "done" means.
5. [PREREQUISITES.md](PREREQUISITES.md) — toolchain and device setup.

If a request conflicts with `spec.md`, say so and propose a spec change; do not silently
implement it.

Project-local agent skills live in `.opencode/skills/` and are indexed by
[.opencode/SKILLS.md](.opencode/SKILLS.md). Load one when its description matches the
task; this file and `spec.md` override any third-party advice.

---

## 2. Hard rules

- **Never commit game data.** No `data.win`, `game.droid`, `*.love`, `*.rpy*`, `*.ogg`,
  save files, extracted cover art or screenshots of copyrighted art. `.gitignore` is not a
  substitute for checking `git status`.
- **Never fork, link, bundle or repackage Butterscotch.** It is AGPL-3.0. Starting its
  exported activities/shortcuts is the only allowed interaction. See
  [LICENSING.md](LICENSING.md).
- **Do not add the `INTERNET` permission or any network call.**
- **Do not add Wine, x86 translation or emulation.**
- **Do not attempt YYC/GMRT GameMaker builds.** A missing `CODE` chunk is a blocker, not a
  challenge.
- **Keep `:detect` pure Kotlin/JVM.** No Android imports, no `android.*` dependencies.
- **Do not change `applicationId`, `versionCode` or signing** without explicit instruction.
- **Do not rename or delete user work** without checking whether it is committed or
  intentional.
- **Do not claim a build or device test that did not run.** See §6.

---

## 3. Commands

```bash
./gradlew :detect:test        # 12 host tests; the minimum gate
./gradlew :app:assembleDebug  # debug APK
./gradlew test                # all host-testable modules
python3 tools/make_icons.py   # regenerate legacy icons (needs Pillow)
```

The Gradle wrapper jar is not committed. If `./gradlew` fails with
`GradleWrapperMain`, run `gradle wrapper --gradle-version 8.7` once, or open the project in
Android Studio.

---

## 4. Where things live

| Task | File |
| --- | --- |
| Home screen / drawer UI | `app/src/main/kotlin/com/indiehost/launcher/HomeUi.kt` |
| Activity, state, launch/settings actions | `app/src/main/kotlin/com/indiehost/launcher/MainActivity.kt` |
| Palette and typography | `app/src/main/kotlin/com/indiehost/launcher/IndieTheme.kt` |
| Installed-app list | `app/src/main/kotlin/com/indiehost/launcher/InstalledApps.kt` |
| Recents | `app/src/main/kotlin/com/indiehost/launcher/Recents.kt` |
| Pin requests | `app/src/main/kotlin/com/indiehost/launcher/PinShortcutActivity.kt` |
| Engine detection | `detect/src/main/kotlin/com/indiehost/detect/EngineDetector.kt` |
| GameMaker WAD parsing | `detect/src/main/kotlin/com/indiehost/detect/GameMakerWad.kt` |
| Runtime contract | `runtimes/src/main/kotlin/com/indiehost/runtimes/RuntimeAdapter.kt` |
| Butterscotch shortcuts | `runtimes/src/main/kotlin/com/indiehost/runtimes/ButterscotchShortcuts.kt` |
| Game model / repository | `library/src/main/kotlin/com/indiehost/library/` |
| Manifest | `app/src/main/AndroidManifest.xml` |
| Versions | `app/build.gradle.kts`, `build.gradle.kts` |
| Agent skills | `.opencode/skills/`, indexed by `.opencode/SKILLS.md` |

---

## 5. Current state (do not assume more)

Implemented:

- Home role, pinned-shortcut library, one-tap launch, drawer + search, recents, `data.win`
  checker, pin acceptance.
- 12 host tests in `:detect`.
- Debug build only; no release signing.

Not implemented (see roadmap):

- Room/persistence; `GameRepository` is unused.
- SAF folder scan, cover art, save management.
- MV/MZ WebView, Ren'Py/LOVE/ScummVM adapters.
- Adapter registry (the comment promises one; it does not exist).
- Instrumented tests, ViewModel/coroutines, localisation.
- Pin filtering, background `data.win` check, shortcut icons.

Known bugs/gaps are listed in [ARCHITECTURE §10](ARCHITECTURE.md#10-known-technical-debt)
and [ROADMAP backlog](ROADMAP.md#backlog). Do not "fix" them by weakening a hard rule.

---

## 6. Verification rules

- Run `./gradlew :detect:test` after any detector change.
- Run `./gradlew :app:assembleDebug` after any app/runtime change, if a JDK and SDK are
  available.
- Device tests need a phone, Butterscotch and a legal game copy. If the environment has no
  JDK, SDK or working `adb` (some sandboxes do not), say exactly what could not be run.
- A green compile is not a device test. A synthetic WAD is not a real game.
- Update [TESTING.md](TESTING.md) when adding a manual case, and the spec/roadmap when
  changing behaviour.

---

## 7. Change discipline

- One logical change per commit; imperative, scoped message:
  `detect: reject WAD 18 with a clear blocker`.
- Update docs in the same change as the behaviour.
- Add a test for pure logic; add a manual case for system integration.
- New dependency? Justify it in the commit and record the licence in
  [LICENSING.md](LICENSING.md).
- Prefer the existing patterns (plain `Thread`, `mutableStateOf`, Compose, no DI) over
  introducing a framework for one screen.
- Do not reformat unrelated files or rewrite the "Restore …" history.

---

## 8. Definition of done

- [ ] Behaviour matches the spec, or the spec is updated.
- [ ] `:detect:test` passes (12/12).
- [ ] APK builds, if the environment allows it.
- [ ] Relevant manual tests pass, or the limitation is stated.
- [ ] No game data, secrets or local paths in the diff.
- [ ] Docs updated (spec/architecture/roadmap/testing).
- [ ] Commit message explains why and names the requirement ID.

---

## 9. Escalate instead of guessing

Ask the user before:

- Changing the application id, signing or version scheme.
- Adding a network permission or a remote service.
- Forking/bundling Butterscotch or any AGPL component.
- Adding a new engine whose runtime model is unresolved (Ren'Py/LOVE/ScummVM).
- Deleting or rewriting large parts of the repository.
- Publishing a release or pushing to a remote.
