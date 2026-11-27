# M2 — RPG Maker WebView runtime design

> **Status:** Approved by the maintainer's "do all of A–K" instruction; the loading
> approach is recorded in [ADR-0007](../../adr/0007-webview-offline-assets.md).

## Intent

M1 detects RPG Maker MV/MZ folders but cannot launch them. M2 must prove the
"one `RuntimeAdapter` per engine" claim with a second real runtime and give the library a
second launchable engine, without adding a network permission or copying game files.

## Goals

- Launch a scanned MV or MZ folder offline from the home screen.
- Select both GameMaker and RPG Maker through one adapter registry seam.
- Behave like a game: no browser chrome, Back confirms leaving, audio pauses in background.
- Fail visibly on a missing `index.html`/core JS, a revoked SAF grant, or a remote request.

## Non-goals / anti-goals

- No `INTERNET` permission, localhost server, Wine, emulation or Butterscotch bundling.
- No decryption of RPG Maker's optional encrypted assets in this milestone.
- No save export UX; FR-20 stays deferred until the runtime is device-proven.
- No Ren'Py/LOVE/ScummVM runtime (M3).

## Constraints

- `minSdk 24`, `targetSdk 34`; `:detect` stays pure Kotlin/JVM.
- Room calls remain on background threads; no coroutines or DI framework.
- SAF grants are tree-scoped; a revoked grant must produce a message, not a crash.
- The debug APK must compile; no device is available here, so device claims stay unverified.

## Approaches

1. **WebViewAssetLoader + SAF PathHandler** (chosen). No copy, no socket, same-origin HTTPS.
   Risk: WebView may check `INTERNET` before interception; must be verified on device.
2. **Copy into app storage + `InternalStoragePathHandler`**. Simpler and avoids that risk,
   but duplicates data and still needs SAF reads. Rejected unless (1) fails on device.
3. **localhost HTTP server**. Adds a socket and a permission story. Rejected by the roadmap.

## Design

- `:runtimes`: add `RuntimeRegistry(adapters)` and give `RuntimeAdapter.launch` an optional
  `RunnerShortcut` so GameMaker keeps its pin handoff. Add `RpgMakerRuntimeAdapter` for MV
  and MZ; its `launch` starts `GameWebViewActivity` by a manifest action string with the
  game id as an extra — `:runtimes` must not depend on `:app`.
- `:app`: `GameWebViewActivity` loads the game row from Room on a worker thread, resolves
  `www/index.html` and `www/js/rpg_core.js` under the SAF tree, then loads
  `https://appassets.androidplatform.net/game/www/index.html`.
- A custom `PathHandler` resolves path segments with `DocumentFile.findFile`, rejects
  traversal, and returns `WebResourceResponse` with a guessed MIME type. `WebViewClient`
  blocks every off-origin request and navigation.
- Back shows a leave confirmation (not `goBack`). Rotation uses `configChanges`; pause
  suspends WebView and abandons audio focus.
- `mergeLibrary` marks an MV/MZ scan launchable when it has a tree URI and no blocker.
- Tests: registry selection, adapter engines, and pure path-safety helpers.

## What is left to the implementer

- Exact MIME table, confirmation copy, and the Activity's error strings.
- Device verification: offline load without `INTERNET`, Back/audio, revoked grant, and an
  MZ title. Until then FR-21/22/23 are "implemented, device-unverified".
