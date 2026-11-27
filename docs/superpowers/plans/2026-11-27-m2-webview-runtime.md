# M2 — RPG Maker WebView Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Launch scanned RPG Maker MV/MZ games offline in a WebView through the same adapter seam as GameMaker.

**Architecture:** `:runtimes` gains a `RuntimeRegistry` and an `RpgMakerRuntimeAdapter` that starts an Activity by a manifest action string (no `:app` dependency). `:app` hosts `GameWebViewActivity`, which serves SAF documents through a `WebViewAssetLoader` path handler under `appassets.androidplatform.net`. No `INTERNET` permission, no copy, no socket.

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, AndroidX WebKit 1.12.0, DocumentFile 1.0.1, JUnit 4, Compose Material3.

**Spec:** `docs/superpowers/specs/2026-11-27-m2-webview-runtime-design.md` and `docs/adr/0007-webview-offline-assets.md`.

## Global Constraints

- `minSdk 24`, `targetSdk 34`, `compileSdk 34`; `applicationId` and `versionCode` unchanged.
- `:detect` stays pure Kotlin/JVM: no `android.*` imports.
- No `INTERNET` permission, no network call, no localhost server, no copy of game files.
- No Wine, x86 translation, emulation, Butterscotch fork/link/bundle.
- No game data, saves or copyrighted art in the repository.
- Room DAO calls are blocking and run only on background `Thread`s; no coroutines.
- One logical change per commit; docs travel with behaviour.
- No device is available: compile and host tests only; device claims stay unverified.

## Review Focus

- A revoked SAF tree must show an error, not crash — `GameWebViewActivity` resolves documents inside a `runCatching` block and finishes with a visible message (MT-30).
- An off-origin URL must never reach the network — `WebViewClient.shouldOverrideUrlLoading` and `shouldInterceptRequest` both reject every non-`appassets.androidplatform.net` host (unit test `WebPathsTest` + MT-31).
- A crafted path such as `../../` or an absolute path must not escape the game folder — `WebPaths.segments()` rejects traversal and returns null (unit test).
- Back must leave a game only after confirmation — the Activity shows a dialog, never `goBack()` (MT-32).
- A missing `index.html` or core JS must fail visibly before load — the Activity checks both files on a worker thread (MT-33).

---

### Task 1: Adapter registry and RPG Maker adapters

**Files:**
- Modify: `runtimes/src/main/kotlin/com/indiehost/runtimes/RuntimeAdapter.kt`
- Modify: `runtimes/src/main/kotlin/com/indiehost/runtimes/GameMakerRuntimeAdapter.kt`
- Create: `runtimes/src/main/kotlin/com/indiehost/runtimes/RuntimeRegistry.kt`
- Create: `runtimes/src/main/kotlin/com/indiehost/runtimes/RpgMakerRuntimeAdapter.kt`
- Modify: `runtimes/build.gradle.kts`
- Test: `runtimes/src/test/kotlin/com/indiehost/runtimes/RuntimeRegistryTest.kt`

**Interfaces:**
- Consumes: `Engine`, `Game`, `RunnerShortcut`, `LaunchOutcome`, `GameMakerRuntimeAdapter.launchShortcut`.
- Produces: `RuntimeAdapter.launch(activity: Activity, game: Game, shortcut: RunnerShortcut? = null): LaunchOutcome`; `RuntimeRegistry(adapters: List<RuntimeAdapter>)` with `fun forEngine(engine: Engine): RuntimeAdapter?`; `RpgMakerRuntimeAdapter(engine: Engine)`, constants `GAME_WEBVIEW_ACTION = "com.indiehost.launcher.GAME_WEBVIEW"` and `EXTRA_GAME_ID = "com.indiehost.launcher.game_id"`.

- [ ] **Step 1: Add JUnit to `:runtimes` and write the failing registry test**

`RuntimeRegistryTest` asserts: a registry of a GameMaker adapter and two RPG Maker adapters returns the adapter whose `engine` matches `GAMEMAKER_VM`, `RPGMAKER_MV` and `RPGMAKER_MZ`; returns null for `RENPY`; and that both RPG Maker adapters share the action constant. Do not construct an Activity.

- [ ] **Step 2: Run it and confirm it fails**

Run: `gradle :runtimes:testDebugUnitTest --no-daemon --console=plain`
Expected: compilation failure because the classes do not exist.

- [ ] **Step 3: Change the seam and add the adapters**

Add the optional `shortcut` parameter to `RuntimeAdapter.launch`; GameMaker overrides it to call `launchShortcut` when a shortcut is supplied and returns `Blocked` with the pin-needed sentence otherwise. `RpgMakerRuntimeAdapter.launch` starts `Intent(GAME_WEBVIEW_ACTION).setPackage(activity.packageName).putExtra(EXTRA_GAME_ID, game.id)` and returns `LaunchOutcome.Launched(activity.packageName)`. Its `canRun`/`prepare` remain conservative: `canRun` is false for a `File` path (SAF games are validated in the Activity), `prepare` returns a `PreparedGame` with a note that validation happens at launch. `supportsSaves()` returns true.

- [ ] **Step 4: Run the test and compile**

Run: `gradle :runtimes:testDebugUnitTest :app:compileDebugKotlin --no-daemon --console=plain`
Expected: tests pass; `:app` compiles despite the new interface parameter.

- [ ] **Step 5: Commit**

```bash
git add runtimes
git commit -m "runtimes: select engines through one adapter registry"
```

### Task 2: Offline WebView activity and SAF path handler

**Files:**
- Create: `app/src/main/kotlin/com/indiehost/launcher/WebPaths.kt`
- Create: `app/src/main/kotlin/com/indiehost/launcher/SafAssetPathHandler.kt`
- Create: `app/src/main/kotlin/com/indiehost/launcher/GameWebViewActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/kotlin/com/indiehost/launcher/WebPathsTest.kt`

**Interfaces:**
- Consumes: `Game`, `RoomGameRepository`, SAF tree/document URIs, `RpgMakerRuntimeAdapter.EXTRA_GAME_ID`.
- Produces: `object WebPaths` with `const val ORIGIN = "appassets.androidplatform.net"`, `const val PREFIX = "/game/"`, `fun urlFor(indexPath: String = "www/index.html"): String`, `fun isLocal(url: String): Boolean`, `fun segments(relative: String): List<String>?`, `fun mimeFor(name: String): String`; `class SafAssetPathHandler(context: Context, treeUri: Uri, folderUri: Uri) : WebViewAssetLoader.PathHandler`.

- [ ] **Step 1: Write the failing pure path test**

`WebPathsTest` asserts: `urlFor()` is `https://appassets.androidplatform.net/game/www/index.html`; `isLocal(urlFor())` is true and `isLocal("https://example.com/game/www/index.html")`, `isLocal("http://appassets.androidplatform.net/game/www/index.html")` and `isLocal("https://appassets.androidplatform.net/other")` are false; `segments("www/js/rpg_core.js")` returns the three segments; `segments("../secret")`, `segments("/etc/passwd")`, `segments("www/../../x")` and `segments("www//js")` return null; `mimeFor("a.js")` is `text/javascript`, `mimeFor("a.png")` is `image/png`, `mimeFor("a.rpgmvp")` is `application/octet-stream`.

- [ ] **Step 2: Run it and confirm it fails**

Run: `gradle :app:testDebugUnitTest --tests '*WebPathsTest' --no-daemon --console=plain`
Expected: compilation failure because `WebPaths` does not exist.

- [ ] **Step 3: Implement `WebPaths` and add the WebKit dependency**

Add `implementation("androidx.webkit:webkit:1.12.0")` to `:app`. Implement `WebPaths` with strict segment validation (no empty segment, `.`, `..`, backslash or leading slash) and a small MIME map with `application/octet-stream` fallback.

- [ ] **Step 4: Implement `SafAssetPathHandler`**

It resolves `treeUri` with `DocumentFile.fromTreeUri`, walks the segments with `findFile`, rejects a non-file, opens an `InputStream`, and returns `WebResourceResponse(WebPaths.mimeFor(lastSegment), null, stream)`. Any exception or null returns null. It never copies bytes to disk.

- [ ] **Step 5: Implement `GameWebViewActivity` and declare it**

The Activity reads `EXTRA_GAME_ID`, loads the row from `RoomGameRepository` on a worker `Thread`, requires an MV/MZ engine and a non-blank `treeUri`, resolves `www/index.html` and `www/js/rpg_core.js` under the tree, then on the main thread configures a fullscreen WebView (JavaScript and DOM storage on; file/content access off; no JS bridge; `setKeepScreenOn(true)`), installs a `WebViewClient` that calls the loader and blocks off-origin requests/navigation, and loads `WebPaths.urlFor()`. Failures show an `AlertDialog` with a reason and finish. Use `OnBackPressedDispatcher` for a leave confirmation; never call `goBack()`. Pause WebView and abandon audio focus in `onPause`, restore in `onResume`. Manifest: exported=false, `configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"`, action `com.indiehost.launcher.GAME_WEBVIEW`, no new permission.

- [ ] **Step 6: Run the test and build**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`
Expected: path tests pass and the APK builds.

- [ ] **Step 7: Commit**

```bash
git add app
git commit -m "app: launch RPG Maker games from a SAF tree in a WebView"
```

### Task 3: Wire MV/MZ into the library

**Files:**
- Modify: `app/src/main/kotlin/com/indiehost/launcher/LibraryItem.kt`
- Modify: `app/src/main/kotlin/com/indiehost/launcher/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/kotlin/com/indiehost/launcher/LibraryMergeTest.kt`

**Interfaces:**
- Consumes: `RuntimeRegistry`, `LibraryItem`, `mergeLibrary`, `Game`, `RunnerShortcut`.
- Produces: `launchItem` selects the adapter by `game.engine` and passes the linked pin as a `RunnerShortcut`; MV/MZ scanned rows are launchable.

- [ ] **Step 1: Update the failing merge test**

Replace the "MV and MZ wait for M2" test with one asserting both are launchable with `reason == null`; add a case where a pinned-only MV game without a tree URI is `WEBVIEW_M2`.

- [ ] **Step 2: Run it and confirm it fails**

Run: `gradle :app:testDebugUnitTest --tests '*LibraryMergeTest' --no-daemon --console=plain`
Expected: the launchability assertion fails.

- [ ] **Step 3: Change the merge rule and wire the registry**

MV/MZ with a tree URI and no blocker become launchable. In `MainActivity`, build a `RuntimeRegistry` with GameMaker and both RPG Maker adapters; `launchItem` resolves the adapter by engine, resolves the linked pin, and maps `LaunchOutcome` to the existing status strings. Keep the pinned-only row's direct shortcut launch.

- [ ] **Step 4: Run the tests and build**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`
Expected: green.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "app: launch scanned MV and MZ games through the registry"
```

### Task 4: Documentation and the full gate

**Files:**
- Modify: `ARCHITECTURE.md`, `TESTING.md`, `ROADMAP.md`, `LICENSING.md`, `README.md`, `spec.md`, `docs/adr/README.md`

- [ ] **Step 1: Update the docs**

Link ADR-0007; mark FR-21–FR-23 implemented but device-unverified; add manual cases MT-29…MT-33 for MV load, no remote request, Back confirmation, missing core JS and revoked grant; record WebKit's Apache-2.0 licence and the "encrypted assets not decrypted" limitation.

- [ ] **Step 2: Run the full gate**

Run: `gradle test :app:assembleDebug --no-daemon --console=plain`
Expected: all host tests pass and the APK builds. State plainly that no device case ran.

- [ ] **Step 3: Commit**

```bash
git add ARCHITECTURE.md TESTING.md ROADMAP.md LICENSING.md README.md spec.md docs/adr/README.md docs/superpowers
git commit -m "docs: record the M2 WebView runtime as device-unverified"
```
