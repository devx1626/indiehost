# Development

> **Status:** Living document · **Last updated:** 2026-11-25
> **Related:** [PREREQUISITES](PREREQUISITES.md) · [ARCHITECTURE](ARCHITECTURE.md) · [spec.md](spec.md) · [TESTING](TESTING.md) · [AGENTS.md](AGENTS.md)

How to build, change and ship IndieHost without breaking its constraints.

---

## 1. Read this first

1. [spec.md](spec.md) — what the product is and the hard constraints.
2. [ARCHITECTURE.md](ARCHITECTURE.md) — where the code lives and how the pieces talk.
3. [PREREQUISITES.md](PREREQUISITES.md) — toolchain and device setup.
4. [TESTING.md](TESTING.md) — what must pass before a change is done.

If a change contradicts the spec, update the spec in the same commit or don't make the
change.

---

## 2. Build and test

```bash
./gradlew :detect:test        # 12 host tests, no device
./gradlew :app:assembleDebug  # debug APK
./gradlew test                # all host-testable modules
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

The Gradle wrapper jar is not committed. If `./gradlew` fails with a missing
`GradleWrapperMain`, run `gradle wrapper --gradle-version 8.7` once or open the project in
Android Studio.

There is no lint baseline and no ktlint/detekt configuration yet. GitHub Actions is
configured to run the host tests and the debug build from
[.github/workflows/android.yml](.github/workflows/android.yml) on pushes to `main` and on
pull requests. The workflow has not run on GitHub yet; the first push verifies it.

---

## 3. Project conventions

### 3.1 Code

- Kotlin official style, 4-space indentation, trailing commas where the file already uses
  them.
- Package names follow the module: `com.indiehost.launcher`, `.library`, `.detect`,
  `.runtimes`.
- Comments explain **why**, not what. The existing code is a good reference: short
  paragraphs above non-obvious decisions.
- Prefer a small, explicit implementation over a framework. There is no DI container, no
  coroutine stack and no navigation library; don't add one for a single screen.
- Keep `:detect` free of Android imports. This is what makes host tests possible.
- Keep `:library` free of UI and Android framework dependencies until Room needs them.
- New dependencies need a reason in the commit message and, if user-visible, a spec note.

### 3.2 Hard rules

- **No game data** in the repo, APK, tests, screenshots or issues (CON-02).
- **No fork/link/bundle of Butterscotch** (CON-01).
- **No `INTERNET` permission or network calls** (CON-03).
- **No emulation** (CON-04).
- **GameMaker VM only**; never attempt a YYC/GMRT build (CON-05).
- **The launcher works with zero games** (CON-06).
- **No Steam branding in a public build**; `-steam` and `SteamTheme` are internal codenames
  (CON-07).

### 3.3 UI copy

- Short, direct, sentence case for body text; uppercase for labels.
- Say what happened and what to do next.
- Do not expose engine jargon in primary UI; put it in the checker report.
- User-visible launcher copy lives in `strings.xml`; add new strings there. Detector
  blockers and notes come from pure-Kotlin `:detect` and stay in English.

### 3.4 Agent skills

The repository carries a project-local skill set in `.opencode/skills/`, indexed by
[.opencode/SKILLS.md](.opencode/SKILLS.md). It is a development aid, not an app
dependency.

- Load a skill when the task matches its description; do not load the whole set.
- `AGENTS.md` and `spec.md` override any third-party advice.
- Do not apply the newer Android stack suggested by `android-ninja` (Hilt, Room 3, AGP 9,
  Kotlin 2.x, `targetSdk 37`) without an explicit decision.
- Keep the upstream licence file when updating a skill. The source pins and sync command
  are in `.opencode/SKILLS.md`.
- Do not add a root-level `*.md` file inside `.opencode/skills/`; OpenCode treats it as a
  skill.

---

## 4. Change workflow

1. **Pick a requirement.** Every non-trivial change should map to an `FR-`/`NFR-` in the
   spec or a backlog item in the roadmap.
2. **Write the test first when the logic is pure.** Detector changes need a fixture and a
   positive/negative test.
3. **Implement in the right module.** Detection in `:detect`, runtime in `:runtimes`,
   persistence in `:library`, UI/wiring in `:app`.
4. **Run `:detect:test` and build the APK.**
5. **Run the relevant manual cases** from [TESTING.md](TESTING.md) on a device. If you
   cannot, say so explicitly in the PR; do not claim a device test you did not run.
6. **Update docs** — spec, architecture, roadmap, test matrix — in the same change.
7. **Commit** with a scoped, imperative message.

### 4.1 Definition of done

- [ ] Behaviour matches the spec (or the spec is updated).
- [ ] `:detect:test` passes.
- [ ] `:app:assembleDebug` succeeds.
- [ ] Relevant manual tests pass, or the limitation is stated.
- [ ] No game data, secrets or local paths in the diff.
- [ ] Docs updated.
- [ ] New hard-coded strings are avoided or tracked.

---

## 5. Adding an engine adapter

The full contract is in [ARCHITECTURE.md §9](ARCHITECTURE.md#91-add-an-engine-adapter).
The short version:

1. Add the `Engine` enum value and display name.
2. Add `detectXxx(folder)` to `EngineDetector`, with a blocker for the unsupported case.
3. Add tests to `detect/src/test`.
4. Implement `XxxRuntimeAdapter : RuntimeAdapter` in `:runtimes`.
5. Register it in the adapter registry (create the registry when the second adapter lands).
6. Add the UI path and a manual test case.
7. Update spec §3 and the roadmap.

Return `LaunchOutcome.Blocked("sentence")` for expected failures; never throw across the
adapter boundary.

---

## 6. Commit style

Use a scope and the imperative mood:

```
detect: reject WAD 18 with a clear blocker
app: move the data.win check off the main thread
runtimes: add an adapter registry
docs: record the M1 save policy
```

Body: explain **why**, note the device test if one ran, and list the requirement ID. Keep
one logical change per commit. The existing history contains a "Restore …" series from a
repository reconstruction; new commits should not imitate it.

---

## 7. Branching and review

- `main` is the stable branch; `HEAD` should always build.
- Use a short-lived branch per change: `feat/m1-room`, `fix/pin-filter`,
  `docs/spec`.
- A pull request should include: what changed, why, requirement ID, test evidence (host and
  device), and any doc updates.
- Do not merge with failing `:detect:test`, game data in the diff, or an unstated device
  limitation.

---

## 8. Release process

There is no automated release pipeline. A release is a manual, verified build.

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Update the spec's "Applies to" line and the README status.
3. Run the full regression checklist in [TESTING.md §6](TESTING.md#6-release-regression-checklist).
4. Build: `./gradlew :app:assembleDebug`.
5. Copy the APK to `dist/` if you keep a local artifact; `dist/*.apk` is git-ignored.
6. Tag the commit: `git tag -a v0.3.0 -m "0.3.0-steam"`.
7. Record the device results (device, Android, fps, notes) in the release notes.
8. **Do not publish** until signing, the application-id rename and the `-steam` rename are
   resolved (see [ROADMAP](ROADMAP.md#backlog)).

---

## 9. Launcher icons

Adaptive icon (API 26+): `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and
`ic_launcher_round.xml`, using `drawable/ic_launcher_foreground.xml` and
`values/colors.xml`.

Legacy PNGs (API 24–25): regenerate with

```bash
python3 tools/make_icons.py
```

The script writes mdpi through xxxhdpi; only mdpi–xxhdpi are committed today. Add the
xxxhdpi files when touching icons (backlog).

---

## 10. Debugging

```bash
# Pin flow
adb logcat -s IndieHostPin

# Crashes
adb logcat -d -s AndroidRuntime

# Installed state
adb shell dumpsys package com.indiehost.launcher | head -40

# Shortcut state (varies by Android version)
adb shell dumpsys shortcut | head -80

# Home app / resolver
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME

# Usage access app-op
adb shell appops get com.indiehost.launcher GET_USAGE_STATS
```

Useful manual checks:

- The library is empty: is IndieHost the home app? `hasShortcutHostPermission()` is only
  true for the home app.
- A game does not launch: is Butterscotch installed? Is the shortcut stale? Re-pin.
- The drawer is empty: `LauncherApps.getActivityList` returned nothing; check the profile
  and the `<queries>` declaration.

---

## 11. Common tasks

| Task | Command / file |
| --- | --- |
| Run detector tests | `./gradlew :detect:test` |
| Build APK | `./gradlew :app:assembleDebug` |
| Install | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Regenerate icons | `python3 tools/make_icons.py` |
| Check a `data.win` | In-app `CHECK DATA.WIN`, or `GameMakerWad.read` |
| Add an engine | §5 above |
| Change the palette | `SteamTheme.kt` (and `themes.xml` for the window background) |
| Change the home layout | `HomeUi.kt` |
| Change app/recents sources | `InstalledApps.kt`, `Recents.kt` |

---

## 12. Documentation map

| File | Audience | Status |
| --- | --- | --- |
| [README](README.md) | Everyone | Front door |
| [spec.md](spec.md) | Everyone | Normative |
| [PREREQUISITES](PREREQUISITES.md) | New contributors | Setup |
| [ARCHITECTURE](ARCHITECTURE.md) | Contributors | Living |
| [ROADMAP](ROADMAP.md) | Contributors | Living |
| [TESTING](TESTING.md) | Contributors/release | Living |
| [DEVELOPMENT](DEVELOPMENT.md) | Contributors | Living |
| [LICENSING](LICENSING.md) | Maintainers | Policy |
| [SECURITY](SECURITY.md) | Maintainers | Policy |
| [AGENTS.md](AGENTS.md) | AI agents | Operating rules |
| [.opencode/SKILLS.md](.opencode/SKILLS.md) | AI agents/contributors | Project-local skill manifest |
| [MILESTONE-0](MILESTONE-0.md) | Everyone | Historical |
| [docs/adr](docs/adr/README.md) | Contributors | Decision record |
