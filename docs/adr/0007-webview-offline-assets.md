# ADR-0007: Load RPG Maker games from SAF through WebViewAssetLoader

- **Status:** Accepted
- **Date:** 2026-11-27
- **Deciders:** project maintainer
- **Related:** [ROADMAP](../../ROADMAP.md) M2, [spec.md](../../spec.md) FR-21–FR-23, [ARCHITECTURE](../../ARCHITECTURE.md)

## Context

M2 has to run RPG Maker MV/MZ games that the M1 scanner finds inside a user-granted SAF
folder. The launcher must not add the `INTERNET` permission, must not duplicate a game's
files, and must not open a socket. `file://` URLs break the same-origin policy that MV/MZ
JavaScript relies on, and WebView cannot read `content://` documents directly.

`WebViewAssetLoader` maps `https://appassets.androidplatform.net/...` to locally supplied
bytes and calls a `PathHandler` on a worker thread, so the game keeps a web origin without
a real network request. The default domain is reserved by Android for this purpose.

## Decision

- Use `androidx.webkit:webkit` `WebViewAssetLoader` with a custom `PathHandler` mounted at
  `/game/`. The handler resolves each path segment under the scanned SAF tree with
  `DocumentFile`, opens an `InputStream`, and returns a `WebResourceResponse`. Nothing is
  copied; the game folder stays where the user put it.
- Load `https://appassets.androidplatform.net/game/www/index.html`.
- `shouldInterceptRequest` returns a local response for every request to that origin and a
  blocked response for anything else; `shouldOverrideUrlLoading` refuses off-origin
  navigation. No remote request is allowed to succeed.
- WebView settings: JavaScript on, DOM storage on, file access and content access off,
  no JavaScript bridge, no universal access from file URLs.
- Back shows a leave-game confirmation instead of browsing history. The activity uses
  `configChanges` so rotation does not restart the game, pauses WebView and abandons audio
  focus in `onPause`, and restores both in `onResume`.
- Add an adapter registry (`Map<Engine, RuntimeAdapter>`) in `:runtimes`. `RuntimeAdapter`
  gains an optional `RunnerShortcut` parameter so GameMaker can keep its pinned-shortcut
  handoff while MV/MZ use the same `launch` seam.

## Consequences

**Positive**

- No `INTERNET` permission, no localhost server, no duplicated game data.
- Web assets keep a same-origin HTTPS URL, which MV/MZ scripts expect.
- The game folder remains under the user's SAF grant; removing the grant stops the game.

**Negative / risks**

- **Device-unverified:** WebView's permission check for intercepted HTTPS URLs without
  `INTERNET` must be confirmed on a phone. If Chromium refuses before interception, this
  decision is void and M2 needs a different loading scheme.
- Encrypted MV/MZ assets (`.rpgmvp`, `.rpgmvo`) are served with a generic MIME type and
  are not decrypted by this version; a game that needs decryption will show broken art.
- SAF providers can be slow; the path handler runs on WebView's worker thread, not ours.
- A malicious game's JavaScript is untrusted code. There is no bridge, and off-origin
  navigation is blocked, but in-game JS is not sandboxed beyond WebView's own model.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Copy the game into app storage and use `InternalStoragePathHandler` | Doubles disk use and still needs SAF reads; unnecessary with a live grant |
| localhost HTTP server | Adds a socket and a permission story; explicitly rejected by the roadmap |
| `file://` URLs | Same-origin breakage and no SAF access |
| A dedicated engine process / native port | Out of scope for M2; no proven runtime model for MV/MZ on Android |

## Open questions

- Does WebView require `INTERNET` for an intercepted `https://appassets.androidplatform.net`
  request? Answer on device before claiming FR-21.
- Which MIME types do encrypted RPG Maker asset extensions need in practice?
- Where MV/MZ save data lands inside WebView storage, and how to export it (FR-20/M2).
