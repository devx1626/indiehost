# Repository survey — UI/UX and architecture prior art

> **Status:** Research synthesis, not a change proposal.
> **Date:** 2026-11-27
> **Method:** GitHub API, raw file fetches, Android developer docs and public web search via
> ten parallel research agents. No repository was cloned into IndieHost, no file was edited
> by the agents, and no code, icon, font, colour value or theme asset was copied.
> **Rule:** IndieHost is MIT. Every source below is GPL/LGPL/MPL/Apache/CC-BY-SA or
> unlicensed. Take **behaviour and architecture ideas only**; reimplement in original
> Kotlin/Compose and cite the source in docs.

---

## 1. Source inventory

| Source | URL | Licence | Read depth |
| --- | --- | --- | --- |
| DroidDeck (user fork + upstream) | [fork](https://github.com/devx1626/DroidDeck/tree/main) · [upstream](https://github.com/Droid-Deck/DroidDeck/tree/main) | GPL-3.0; HUD file adds attribution | Deep: launcher, state, settings, theme, input, library, session, tests, CI, docs |
| Cinnamon | https://github.com/linuxmint/cinnamon | GPL-2.0+ (mixed LGPL/GPL-3 vendored areas) | Deep: session, panel, menu, search, overview, notifications, settings schema, themes, a11y, gestures, persistence |
| COSMIC | https://github.com/pop-os/cosmic-epoch + 57 cosmos repos | Meta no SPDX; components GPL-3.0; libcosmic MPL-2.0; assets CC BY-SA | Deep: launcher, workspaces, panel, settings, config, theme, comp/motion, a11y, docs/CI |
| Bazzite | https://github.com/ublue-os/bazzite | Apache-2.0 (submodules vary) | Deep: release matrix, atomic updates, rollback, profiles, kargs/just, docs, CI, hardware preflight |
| Wider Pop!_OS org | https://github.com/pop-os (345 public repos surveyed) | Mostly GPL/LGPL/MPL; some MIT | Patterns from `installer`, `upgrade`, `launcher`, `firmware-manager`, `snapshot`, `support-panel`, `docs`, release tooling |
| Compatible Android launchers | ARK-Launcher, Lawnchair, KISS, Olauncher, OpenArcade, Pegasus | ARK/Lawnchair Apache-2.0; OpenArcade MIT; KISS/Olauncher/Pegasus GPL | Deep on Apache/MIT repos only |
| Library/scan prior art | Material Files, AntennaPod, AOSP DocumentsUI | GPL-3.0 / Apache-2.0 | Deep on SAF grant registry, single-query listing, upsert/duplicate rules, migrations |
| Security prior art | DroidDeck, Material Files, Cinnamon privacy panel, Android docs | mixed | Deep on exported components, pin validity, WebView hardening, backup rules |
| Testing/docs/CI prior art | DroidDeck, Bazzite, Cinnamon, COSMIC | mixed | Deep on preview renders, device scripts, release gates, compatibility matrices |
| Future emulation boundary | FEX, Box64, Wine, Proton, Winlator, Mobox, GameNative, Termux, Butterscotch public docs | MIT/LGPL/GPL/AGPL/proprietary mix | High-level only; no implementation proposed |

**Licence traps:** DroidDeck's HUD adds attribution beyond GPL-3; Pegasus has a trademark clause; COSMIC reserves the `cosmic-` name/App-ID namespace; Cinnamon has LGPL/GPL-3 vendored files; Bazzite's Apache grant explicitly gives no trademark rights. None of these materials may be vendored into IndieHost.

---

## 2. Domain findings

### 2.1 DroidDeck (deep)

**Architecture.** `HomeActivity.kt` is a router only; `FrontEndActivity.kt` is a single `ComponentActivity` with ~150 `mutableStateOf` fields, a main `Handler`, named background `Thread`s that post results back, and a sequence counter to drop stale results. No ViewModel/DI backbone. Relevant: [HomeActivity.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/HomeActivity.kt), [FrontEndActivity.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/FrontEndActivity.kt).

**Pin flow.** `PinRequestActivity.kt` validates `LauncherApps.getPinItemRequest(intent)` with `request.isValid`, handles shortcut and widget requests, and confirms before accepting. `HomePins.kt` loads with `FLAG_MATCH_PINNED`, filters enabled/pinned, keys by package/id/user and refreshes from system state. Direct analogue of IndieHost's `PinShortcutActivity`/`ButterscotchShortcuts`. Do not touch Butterscotch internals.

**State stores.** `HomeFolders`/`HomeIcons`/`HomeHidden` are small `object` stores over SharedPreferences with load-once guards and explicit migrations; `NavTabs.kt` has `LAYOUT_VERSION = 2` migration. Useful shape, but IndieHost's Room decision stands.

**Theme.** `ThemeColors.kt` derives readable ink from worst-case contrast and tests 33 gradient steps for ≥4.5:1; `Theme.kt` builds a dark scheme and scales density. Reimplement the *test*, not the algorithm source.

**Input.** `InputRouter.kt` defines an `InputOutput` seam; `PhysicalShortcuts.kt` validates bindings on read and rejects virtual/HOME/POWER keys. Future gamepad work only.

**Covers/library.** `AddedGameArt.kt` resolution chain: chosen → local folder art → cached → executable icon, with `.miss`/provenance markers and TTL; `GameExePicker.kt` bounds walks (5 levels, 400 entries, no symlinks). Offline-only subset is applicable.

**Testing/CI.** ~700 JVM tests; Robolectric preview renders at handheld 960×540dp and portrait 540×960dp, failing on flat-colour output ([compose-previews.md](https://github.com/devx1626/DroidDeck/blob/main/docs/compose-previews.md)); generated settings map; documented flakes; source-tree/merged-tree pinning in CI; signed releases via GitHub Environment secrets. Do **not** import its `INTERNET`, `QUERY_ALL_PACKAGES`, `MANAGE_EXTERNAL_STORAGE`, foreground services, Wine/Proton/FEX stack, or HUD.

### 2.2 Cinnamon (deep)

**Startup.** `js/ui/main.js` defines `RunState` and reveals the UI only once data is ready (`backgroundManager.whenReady`), on idle, with a catch path that still reveals. Avoids a permanent splash on failure.

**Menu/search.** Tiered scoring: `MATCH_ADDERS = [0 name, 1000 keywords, 2000 description, 3000 id]`, `SEARCH_TYPE_ORDER = ['app','favorite','recent']`, `NO_MATCH`, decomposing/accent-insensitive match, `INITIAL_BUTTON_LOAD = 30`, generation token to drop stale async results. Files: [applet.js](https://github.com/linuxmint/cinnamon/blob/master/files/usr/share/cinnamon/applets/menu@cinnamon.org/applet.js), [appFavorites.js](https://github.com/linuxmint/cinnamon/blob/master/js/ui/appFavorites.js).

**Settings schema.** `settings-schema.json` supports `dependency: key | !key | key=value | comparisons` on rows **and sections**; writes debounced 250 ms with sanity checks; schema MD5 triggers upgrades. Relevant: [xlet-settings-ref.xml](https://github.com/linuxmint/cinnamon/blob/master/docs/reference/cinnamon-tutorials/xlet-settings-ref.xml), [js/ui/settings.js](https://github.com/linuxmint/cinnamon/blob/master/js/ui/settings.js).

**Feedback.** `messageTray.js` has `HIDDEN→SHOWING→SHOWN→HIDING`, urgency LOW/CRITICAL, queue sorted by urgency, per-app opt-out with normalised IDs, 20-item cap, critical bypasses busy suppression.

**Gestures.** `gesturesManager.js` has `lookup_way_back()` so the opposite gesture closes a view even without an explicit close binding; phase `start|end|follow`, 150 ms velocity window, flick projection, 100–300 ms duration.

**Performance/a11y.** Software-render detection disables animations; layout updates batched on idle; recents clamped to 20 in the fast layer; accessible names combine label+description; focus groups for action buttons. Apply as behaviour, never copy GJS/SCSS/icons/colours. Cinnamon's Meson tests are currently disabled — do not mirror that gap.

### 2.3 COSMIC (deep)

**Launcher.** `cosmic-launcher/src/app.rs` has explicit `SurfaceState { Visible, Hidden, WaitingToBeShown }`; `VecDeque<Message> queue` buffers activations until results arrive; one-shot `hand_over` string; generation-based cancellation; result classification (window before app); icon fallback chain; visible `Ctrl+N` hints. Backend `pop-os/launcher` is JSON-IPC plugins with crash isolation — study only, do not add sockets.

**Config.** `libcosmic/cosmic-config` uses versioned keys (`v<N>`), local-over-system defaults, `#[serde(default)]` additive fields, atomic transactions, previous-version fallback and path-traversal guards. Semantics transferable to Room/SharedPreferences; RON/watch machinery not.

**Theming.** Look data separated from widgets; `Mode::{Dark,Light,HighContrastDark,HighContrastLight}`; theme bundle store preserves interface keys when swapping looks. Keep IndieHost's fixed palette; borrow high-contrast shape only.

**Motion.** `cosmic-comp` derives alpha from state+time; reduced-motion support is still an **open issue** upstream. Use a single short fade gated on `ANIMATOR_DURATION_SCALE`; never port `spring.rs` (GPL→LGPL→MIT chain).

**A11y/input.** `keyboard_nav.rs`: Tab/Shift+Tab, Escape, F11, Ctrl+F, only when widgets ignore the event; `toggler_maybe` disables unsupported rows. Use as a pattern for capability-gated settings.

**Trademark.** COSMIC name/logo and `com.system76.*`/`cosmic-` prefixes are reserved ([TRADEMARK.md](https://github.com/pop-os/cosmic-epoch/blob/master/TRADEMARK.md)). Never use.

### 2.4 Bazzite

**Explicit matrix.** `build.py` holds a hand-written 14-image `IMAGES` table because "an explicit table is the only version of that logic that's reviewable at a glance"; golden tests assert it. Single source feeds CI matrix and changelog. Transferable to `COMPATIBILITY.md`.

**Rollback/ready state.** `bazzite-rollback-helper` has `list/current/rollback/rebase`, detects signed vs unverified refs, refuses KDE↔GNOME rebases; greenboot healthcheck triggers rollback after failed boots. Local, reversible, self-describing configuration.

**Preflight.** `on_gui_login.sh` blocks CSM boot, detects <5 GB RAM, serves offline docs on 127.0.0.1, detects wrong NVIDIA image with QR code and an explicit "I know what I'm doing" escape hatch. "Explain before acting, offline" is the transferable part.

**Diagnostics.** `96-bazzite-anticheat.just` prints PASS/FAIL/SKIP with exact remediation commands; `yafti.yml` offers update/rollback/tweak cards with status scripts. Build a local compatibility self-check, not telemetry.

**Must not copy:** uupd/OSTree/bootc, Proton/Wine/umu/DXVK/Distrobox/Waydroid, `fpaste`, remote release checks, controller fixups, hardware claims, branding.

### 2.5 Wider Pop!_OS org

345 repos surveyed; relevant patterns:

| Repo | Pattern |
| --- | --- |
| [upgrade](https://github.com/pop-os/upgrade) | D-Bus status/signal state machine with named phases and `(completed,total)`, `ReleaseRepair`, hostile-case TESTING.md |
| [launcher](https://github.com/pop-os/launcher) | JSON-IPC plugin isolation, metadata descriptors, regex routing, history, crash resilience |
| [installer](https://github.com/pop-os/installer) | `--test` dry-run, explicit `INSTALL`/`REFRESH` modes, wizard Views + Check/Error/Success screens, contextual help |
| [distinst](https://github.com/pop-os/distinst) | Plan in memory, validate, then apply — good analogue for SAF/library changes |
| [firmware-manager](https://github.com/pop-os/firmware-manager) | Changelog before upgrade, per-device progress, success removes action, failure restores it, telemetry disabled |
| [snapshot](https://github.com/pop-os/snapshot) | Commented config with include/exclude and rollback semantics |
| [support-panel](https://github.com/pop-os/support-panel) | Local diagnostics with deliberately minimal fields; serial number left empty |
| [docs](https://github.com/pop-os/docs) + `pop/scripts/validate` | mdBook IA and a CI gate requiring README/LICENSE/TESTING in every repo |
| [repo-release](https://github.com/pop-os/repo-release), [buildchain](https://github.com/pop-os/buildchain) | Explicit, reviewed release manifests; signed manifest + shasum |

### 2.6 Compatible Android launchers

| Repo | Licence | Value |
| --- | --- | --- |
| [ARK-Launcher](https://github.com/jrs8205/ARK-launcher) | Apache-2.0 | Single-Activity Compose Material3 launcher; pin request activity/state; back swallowing registered before content; one root gesture detector; settings index + keyword search; Room + prefs; extensive host tests |
| [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) | Apache-2.0 | AOSP-grade launcher layout, device profiles, `allapps/search`, accessibility delegates, device-less tests |
| [OpenArcade](https://github.com/kys0ff/OpenArcade) | MIT | Game-library front end, Room repositories/use cases, no INTERNET permission; architecture inspiration only (Koin/Coroutines stack not transferable) |
| KISS / Olauncher / focus_launcher | GPL | Search-as-home, minimal home, SharedPreferences; **ideas only** |
| Pegasus Frontend | GPL + trademark clause | Game library/filtering ideas only |
| Xross | Apache-2.0 but multi-emulator + RetroAchievements | Conflicts with hard rules |

Highest-value ARK files: `PinRequestActivity.kt`, `SystemPinRequest.kt`, `PinRequestScreen.kt`, `BackOnHome.kt`, `AppDrawerScreen.kt`, `SettingsSearch.kt`, `HomeScreen.kt`, `LauncherAppsSource.kt`, `OnboardingFlow.kt`.

### 2.7 Library/scan patterns

| Source | Pattern |
| --- | --- |
| [Material Files](https://github.com/zhanghai/MaterialFiles) | `DocumentTree` stable ID generated once, `addOrReplace` by id, persisted URI only, RW→RO fallback, sorted `persistedUriPermissions`, release on removal, settings version upgrade |
| [AntennaPod](https://github.com/AntennaPod/AntennaPod) | `FastDocumentFile` single query for all child metadata; local folder scan with upsert by stable identifier; `updateFromOther` preserves user state; `removeUnlistedItems` only for exhaustive scans; `DBUpgrader` additive migrations; `CoverLoader` primary→fallback→placeholder |
| DroidDeck | One-shot TTL'd `LauncherHandoff` token instead of attacker-fillable Intent extras; phased scan timing; inode/dev dedupe; orphans marked safe, not deleted |
| COSMIC launcher | Deferred action queue and one-shot handover string; icon fallback chain |
| AOSP DocumentsUI | Canonical SAF `ACTION_OPEN_DOCUMENT_TREE` + persistable permission behaviour |

Concrete implications: stable `scanId` + `scan_runs`, `lastSeenScanId` watermark (mark unavailable, never auto-delete), duplicate key `duplicateOf`, single-query SAF listing, progress/cancel with plain `Thread`, additive Room migrations, key covers by game id, per-root regrant UI.

### 2.8 Testing/docs/CI

- **Preview renders** (DroidDeck): debug-only previews rendered to PNG via Robolectric at two sizes; fail on exception or flat colour. Offline jar caveat: Robolectric normally downloads `android-all-instrumented`; needs pre-populated cache or workstation-only status.
- **Change-scoped tests** (DroidDeck `pre_pr.sh`): map changed Kotlin files to `<File>*Test.kt`; full suites are CI's job.
- **Device script contract**: document what it does *and restores*; PASS/FAIL/SKIP counters; exit codes 0/1/2; snapshot/restore traps; device identity; crash-buffer check; SKIP means "not applicable", never silent pass.
- **Compatibility matrix as test** (Bazzite `test_builder.py`): hand-written expected table; golden test catches version bumps that forget matrix updates.
- **Release notes checked in before release**; provenance footer with build/release SHAs; `SHA256SUMS.txt`.
- **CI gates**: required-job aggregator, merged-tree pinning, no-key-in-tree, version sanity, `:detect` purity, always-upload diagnostics, named flake list.

### 2.9 Security/privacy

- **Backup**: `allowBackup="false"` is insufficient on API 31+ for some OEM device-to-device transfers; add `dataExtractionRules` excluding `indiehost.folders`, `indiehost.recents`, Room DB and `files/covers`. Move covers to `noBackupFilesDir` or exclude explicitly.
- **Pin requests**: add `request.isValid` before inspecting; filter `isEnabled && isPinned` after `getShortcuts`; accept() may return false.
- **WebView**: already strong; add `blockNetworkLoads = true` and `MIXED_CONTENT_NEVER_ALLOW`, isolate per-game origin (all games currently share `appassets.androidplatform.net/game/` DOM storage), serve SVG as octet-stream or sanitise, cap path segments/bytes, clear cache on destroy. Verify `blockNetworkLoads` does not break intercepted local requests.
- **Transparency**: Cinnamon `cs_privacy.py` model — explain what is stored (tree URIs, ≤512 px covers, ≤12 recent packages, 3-day window), with clear/remove actions. Gate debug logs behind `BuildConfig.DEBUG`.
- **Do not adopt**: DroidDeck's `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, exported agent provider, Material Files network roots, any telemetry.

### 2.10 Future emulation boundary (research only)

Every plausible Android PC-game stack (FEX/Box64 + Wine/Proton + DXVK/VKD3D + rootfs/PRoot, or Winlator/Mobox/GameNative integrations) collides with at least one current hard rule: no Wine/x86 translation/emulation, no INTERNET, no Butterscotch fork/link/bundle, no YYC/GMRT, no game data in repo. Butterscotch itself is AGPL-3.0 bytecode-only (WAD 8–17); only starting its exported activities/shortcuts is allowed.

Maintainer inputs required before any such phase: exact games and engines, legal basis, device floor, performance targets, permission changes, licence posture, distribution channel, explicit spec amendments, and confirmation that Butterscotch remains launch-only. See [future-emulation boundary notes](./future-emulation-boundary.md) (to be written from the research report) and the questions in section 5.

---

## 3. Cross-cutting synthesis — patterns ranked

| Rank | Pattern | Sources | IndieHost fit |
| --- | --- | --- | --- |
| 1 | Pin request validation + explicit states + stale-result sequence guard | DroidDeck, ARK, Android docs | Direct fit to `PinShortcutActivity`/`ButterscotchShortcuts`; high security value |
| 2 | Generation token for all async UI results | Cinnamon, COSMIC, DroidDeck | Scan, cover decode, search, detail load; plain `Thread` compatible |
| 3 | Single-query SAF listing + stable IDs + upsert-never-delete + stale watermark | AntennaPod, Material Files | Direct fit to `GameScanner`/`GameFolderStore`/Room |
| 4 | Device-less preview renders + change-scoped host tests | DroidDeck | CI-detectable UI states without a device |
| 5 | Tiered local search with accent folding and stable ranking | Cinnamon, ARK, Lawnchair, KISS | Extends the already-implemented `AppSearch` |
| 6 | Readiness-gated startup reveal + failure path that still reveals | Cinnamon, Bazzite preflight | `MainActivity` startup |
| 7 | Capability-gated actions/settings (`isEnabled`, `isPinned`, provider version, SAF grant) | Cinnamon, COSMIC, Pop!_OS | Avoids silent no-ops |
| 8 | Urgency/state machine for feedback with PASS/FAIL/SKIP | Cinnamon, Bazzite, DroidDeck device script | Replaces scattered status text/toasts |
| 9 | Additive/versioned config with defaults and reversible changes | COSMIC config, Pop!_OS snapshot, DroidDeck prefs | Room migration policy, prefs export/import |
| 10 | Contrast and a11y tests; reduced-motion gate | DroidDeck, COSMIC, Cinnamon | `IndieTheme`, TalkBack focus, low-end motion budget |
| 11 | Explicit compatibility matrix with tiers and machine checks | Bazzite, COSMIC, DroidDeck | `COMPATIBILITY.md` |
| 12 | Offline-first onboarding/help that explains before asking | Bazzite, Pop!_OS installer, ARK | First-run SAF/home-role flow |

---

## 4. Master applicability matrix

| IndieHost section | High-value sources | Rank | Notes |
| --- | --- | --- | --- |
| `MainActivity.kt` state/lifecycle | DroidDeck `FrontEndActivity`, Cinnamon `main.js`, COSMIC `SurfaceState` | High | Named threads + `ui.post` + sequence guards; no ViewModel/DI |
| `HomeUi.kt` home/drawer | ARK `HomeScreen`/`AppDrawerScreen`, Cinnamon menu, COSMIC launcher | High | Tiered search, focus, empty states, reserved badges |
| `PinShortcutActivity.kt` | DroidDeck/ARK pin activities, Android `PinItemRequest` docs | Highest | `isValid`, re-resolve, explicit failure states |
| `InstalledApps.kt`/`Recents.kt` | Lawnchair, ARK `LauncherAppsSource`, Cinnamon doc-system | High | Profile-safe enumeration, 20-item cap, pure recents function |
| `IndieTheme.kt` | DroidDeck contrast tests, COSMIC high contrast, Cinnamon theme manager | Medium-High | Keep palette; add contrast/a11y tests and motion gate |
| `GameFolderStore.kt`/`GameScanner.kt` | Material Files, AntennaPod | Highest | Grant lifecycle, single query, watermark, progress/cancel |
| `library/` (Room) | AntennaPod DBUpgrader, COSMIC config versioning, ARK repositories | High | Additive migrations; never `CONFLICT_REPLACE` for game rows |
| `GameWebViewActivity.kt` | Android WebViewAssetLoader docs, COSMIC handoff, Cinnamon stale guards | High | Hardening flags, origin isolation, loading/blocked states |
| `runtimes/` adapters | pop-launcher plugin isolation, DroidDeck handoff token | Medium | Keep launch-only Butterscotch boundary |
| `detect/` (pure Kotlin) | DroidDeck bounded walks, Cinnamon fast-layer caps | Medium | Keep pure; add caps and pure scoring tests |
| Settings (not yet built) | Cinnamon schema deps, COSMIC settings pages, ARK settings index, Bazzite recipe conventions | Medium | One screen, dependency-gated, reversible |
| `TESTING.md`/`tools/device_checks.sh` | DroidDeck device smoke + previews, Bazzite golden matrix | Highest | Close the device-verification gap |
| CI/release/docs | DroidDeck CI, Bazzite changelog/attestation, pop validate script | High | Gates, provenance, doc/data hygiene |

---

## 5. Open questions for the maintainer

1. Which of the ranked patterns should become the next implemented change? Recommendation: items 1–4 first (pin validation, generation tokens, SAF/library watermark, preview renders).
2. Is a settings surface in scope now, or should capability-gated actions stay inline for v0.4.x?
3. Is workspace-like paging desired, or does the single-column home stay?
4. What is the recents cap/privacy policy — keep 12/3 days, or adopt 20 with an explicit opt-out?
5. Are per-game WebView DOM storage isolation and SVG policy changes acceptable (could affect some games)?
6. Should `dataExtractionRules` and `noBackupFilesDir` for covers land in the next patch release?
7. Is physical-keyboard/D-pad navigation a target, or touch-only for the low-end profile?
8. Should a local diagnostics/privacy screen show stored data and a copy-report action, with no upload?
9. When should destructive Room migration be replaced by additive migrations?
10. For the future emulation phase: the 14 questions in the boundary report (games, legal basis, INTERNET, hard-rule amendments, Butterscotch launch-only, device floor, performance bar, DRM, licences, distribution, storage, repo separation, approval evidence).

---

## 6. Bottom line

The research converges on a small set of high-value, licence-safe moves: make pin/scan/launch results generation-guarded and explicitly validated; make SAF/library changes staged, upserted and reversible; make feedback and settings capability-gated and honest; add device-less preview renders and a machine-checked compatibility matrix; and keep everything local, offline and MIT. Everything involving Wine/x86/emulation, `INTERNET`, Butterscotch internals, copyleft code or trademarked assets stays ruled out.
