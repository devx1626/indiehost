# Security

> **Status:** Policy · **Last updated:** 2026-11-25
> **Related:** [spec.md](spec.md) · [LICENSING](LICENSING.md) · [ARCHITECTURE](ARCHITECTURE.md) · [ROADMAP](ROADMAP.md)

IndieHost is a local Android launcher with no backend, no account and no network
permission. The security surface is small, but it includes two exported activities, a
sensitive app-op and (from M2) untrusted game HTML inside a WebView.

---

## 1. Supported versions

| Version | Supported |
| --- | --- |
| `0.3.x` debug builds | Yes, for development |
| Public/release builds | None yet — signing and application id are not final |

There is no security contact yet. Until one is published, report issues privately to the
repository owner rather than opening a public issue with exploit details. Never attach game
data to a report.

---

## 2. Assets and trust boundaries

| Asset | Sensitivity | Where it lives |
| --- | --- | --- |
| Game files and saves | Copyrighted, personal | Butterscotch's private storage; future IndieHost game folders |
| Recent-app history | Personal behaviour | `SharedPreferences` + system usage log |
| Home-role/shortcut access | Powerful launcher capability | Granted by the system to the current home app |
| Game folder paths | Personal filesystem layout | Future Room database |
| Launcher availability | The user's home screen | This app |

Trust boundaries:

- **Android system → IndieHost:** intents, pin requests, `LauncherApps` results.
- **Other apps → IndieHost:** exported `MainActivity` and `PinShortcutActivity`.
- **IndieHost → Butterscotch:** `startShortcut` / `startActivity` only.
- **Game HTML → WebView (M2):** untrusted content from a user-supplied folder.

---

## 3. Permissions and manifest surface

| Item | Risk | Mitigation |
| --- | --- | --- |
| `CATEGORY_HOME` | Being the home app grants shortcut-host permission | Intended; the user chooses it in Settings |
| `CATEGORY_LAUNCHER` | App can be opened from another drawer | Intended |
| `CONFIRM_PIN_SHORTCUT` activity (exported) | Any app can send a pin request; a malicious app could try to pin a shortcut | Validate package/id (gap); `getPinItemRequest` only returns a request for a genuine system pin |
| `PACKAGE_USAGE_STATS` (app-op) | Reveals app usage | Optional; data stays local; no analytics |
| `<queries>` for all launcher apps | Package visibility | Standard for a launcher; needed for the drawer |
| `<queries>` for Butterscotch | Package visibility | Needed to resolve its launch intent |
| No `INTERNET` | — | Positive control: the launcher cannot exfiltrate anything |

`android:allowBackup="false"` prevents paths, recents and future library data from being
uploaded to Google backup.

---

## 4. Exported components

### 4.1 `MainActivity`

- Exported because it must answer `MAIN`/`HOME` and `MAIN`/`LAUNCHER`.
- `singleTask`, `excludeFromRecents`.
- It accepts no extras and performs no privileged action based on intent data.
- `onNewIntent` only resets the drawer state.

Risk: low. A malicious app can bring the launcher to the foreground, which is equivalent
to pressing Home.

### 4.2 `PinShortcutActivity`

- Exported because the system sends `android.content.pm.action.CONFIRM_PIN_SHORTCUT`.
- Translucent, `noHistory`, finishes immediately.
- Currently accepts **any** `REQUEST_TYPE_SHORTCUT` request and calls `accept()`.

Risk: a malicious app could ask the system to pin a shortcut and have IndieHost accept it.
IndieHost will not display a non-`game-` shortcut, so the practical impact is a stray pin
recorded against the launcher, not code execution. Still, the correct behaviour is to
validate:

```kotlin
val shortcut = request.getShortcutInfo()
val fromButterscotch = shortcut?.`package` == GameMakerRuntimeAdapter.BUTTERSCOTCH_PACKAGE
val isGame = shortcut?.id?.startsWith(ButterscotchShortcuts.GAME_SHORTCUT_PREFIX) == true
if (fromButterscotch && isGame) request.accept()
// Otherwise finish without accepting. PinItemRequest has no reject(); not accepting
// simply lets the request expire.
```

This is backlog P1 in [ROADMAP.md](ROADMAP.md#backlog). Add a manual test (MT-19) when it
lands.

---

## 5. Data handling

- **Recents:** package names only, in `SharedPreferences("indiehost.recents")`. No
  timestamps of sensitive content, no app usage durations.
- **Usage access:** queried on demand; the event list is not persisted.
- **Checker cache:** one file at `cacheDir/import/data.win`, deleted before each check. It
  is app-private and excluded from backup. Consider deleting it after the report is built
  (backlog).
- **Future Room library:** game titles, paths and cover paths. Keep it app-private;
  `allowBackup=false` already covers it.
- **Future WebView:** save data (localStorage/IndexedDB) is game data; document where it
  lives and never upload it.

No analytics, crash reporting, advertising or remote config is present or planned.

---

## 6. Future surfaces and required controls

### 6.1 SAF folder scan (M1)

- Use `OpenDocumentTree` and persist the grant; do not request broad storage permissions.
- Resolve child documents through `DocumentsContract`; do not concatenate user input into
  paths.
- Handle revoked grants, missing directories and symlinks without crashing.
- Never scan outside the granted tree.

### 6.2 WebView (M2)

Untrusted game HTML/JS is the largest future risk.

Required defaults:

- `javaScriptEnabled = true` (games need it), but **no** JavaScript bridge unless a
  concrete feature requires one.
- `allowFileAccess = false`, `allowContentAccess = false`,
  `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`.
- Mixed content: never allow.
- Block remote requests; a game that needs the network fails visibly.
- Do not enable `addJavascriptInterface` for a general-purpose bridge.
- Consider running the WebView in a separate process if a future bridge handles sensitive
  data.
- Treat `localStorage`/IndexedDB as user data; document backup/export.

### 6.3 Room (M1)

- No sensitive secrets in the database.
- If paths are stored, they are user-chosen and app-private.
- Do not log full paths in release builds.

---

## 7. Threat model summary

| Threat | Likelihood | Impact | Current control |
| --- | --- | --- | --- |
| Malicious app pins a shortcut through IndieHost | Low | Low (stray pin, not shown) | None — validation is backlog P1 |
| Another app reads IndieHost's recents | Low | Low | Prefs are app-private; no exported provider |
| Usage data leaves the device | Very low | Medium | No network permission; no analytics |
| Game data is committed or uploaded | Medium (human error) | High (copyright) | `.gitignore`, docs, review, `allowBackup=false` |
| AGPL violation by bundling Butterscotch | Low | High (legal) | CON-01, ADR-0002, review |
| WebView escapes to local files/network (M2) | Medium if misconfigured | High | Required WebView settings above |
| Path traversal in folder scan (M1) | Medium if naive | Medium | Use SAF APIs; validate tree membership |

---

## 8. Hardening checklist

Before a public release:

- [ ] `PinShortcutActivity` validates package and `game-` prefix.
- [ ] Checker cache is deleted after the report.
- [ ] No `INTERNET` permission.
- [ ] `allowBackup="false"` remains.
- [ ] No debug logging of file paths or package lists in release.
- [ ] WebView settings match §6.2.
- [ ] Exported components reviewed against the manifest.
- [ ] A security contact is published.
- [ ] Release signing and application id are final.

---

## 9. Reporting a vulnerability

Open a private security advisory on the repository, or contact the maintainer directly.
Include:

- Affected build/commit.
- Android version and device.
- Reproduction steps.
- Impact.
- A suggested fix, if you have one.

**Do not include game files, save files or personal data.** A minimal synthetic repro is
enough.
