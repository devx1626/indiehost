# UI/UX research: DroidDeck, Cinnamon, COSMIC

> **Status:** Research notes, not a change proposal.
> **Date:** 2026-11-27
> **Method:** GitHub web/API/raw reads only. No repository was cloned into IndieHost, no
> source file, icon, font, colour value or theme asset was copied, and no build or device
> test was run for this document.

## Why this exists

IndieHost is a home launcher for a low-end Android phone. Its current UI is a single
`LazyColumn` home screen (client bar, hero, recents, library rows, tools) plus an app drawer,
a library detail dialog and an offline WebView game activity. This note records UI/UX ideas
from three larger desktop/front-end projects and maps them onto IndieHost's actual files.

The rule throughout: **take the behaviour, never the bytes.** All three upstream projects
are copyleft or file-level copyleft; IndieHost is MIT.

---

## 1. Licence boundaries

| Source | Licence | Evidence | What is off-limits |
| --- | --- | --- | --- |
| [devx1626/DroidDeck](https://github.com/devx1626/DroidDeck/tree/main) | GPL-3.0 | [LICENSE](https://github.com/devx1626/DroidDeck/blob/main/LICENSE) | Kotlin/JNI source, `ThemeColors.palette()` logic, icons, fonts, artwork, colour hexes |
| [linuxmint/cinnamon](https://github.com/linuxmint/cinnamon) | GPL-2.0-or-later | [README.rst](https://github.com/linuxmint/cinnamon/blob/master/README.rst), [COPYING](https://github.com/linuxmint/cinnamon/blob/master/COPYING) | GJS/Clutter code, SCSS, SVG/PNG icons, `.ui` files, translations, palette values |
| [pop-os/cosmic-epoch](https://github.com/pop-os/cosmic-epoch) | Umbrella repo has no root licence; components are GPL-3.0 ([launcher](https://github.com/pop-os/cosmic-launcher/blob/master/LICENSE.md), [workspaces](https://github.com/pop-os/cosmic-workspaces-epoch/blob/master/LICENSE), panel, settings); toolkit [libcosmic](https://github.com/pop-os/libcosmic) is MPL-2.0 | [repo metadata](https://api.github.com/repos/pop-os/cosmic-epoch), [TRADEMARK.md](https://github.com/pop-os/cosmic-epoch/blob/master/TRADEMARK.md) | Rust/RON source, theme files, icons/wallpapers/sounds, `cosmic-` name and App-ID prefixes |

Two disambiguations:

- The local `.opencode/skills/design-styles/references/cosmic/DESIGN.md` is an unrelated MIT
  sci-fi web style guide. It is **not** Pop!_OS COSMIC and is not evidence here.
- IndieHost's own `IndieTheme.kt` palette is original and stays as it is. None of the
  upstream hex values should be adopted.

---

## 2. What each project actually does well

### DroidDeck — front-end navigation and state craft

DroidDeck is a SteamOS-like launcher/front end. Its UI is a rail + pane system: tabs are a
bottom bar in portrait and a side rail in landscape; each pane shows art-led grids;
selecting a card opens a detail page over the rail; dialogs and sheets layer on top.

| Pattern | Evidence |
| --- | --- |
| Reconfigurable tab registry with placement, hidden/shelved state and badges | [NavTabs.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/NavTabs.kt) |
| Orientation-aware rail with selected/focused/hovered/pressed states and count/attention badges | [FrontEndRail.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/FrontEndRail.kt) |
| Single source of navigation truth; back stack page → rail → More → exit; focus restored after navigation | [FrontEndScreen.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/FrontEndScreen.kt) |
| Adaptive art grid with deterministic gradient + initial fallback when art is missing | [FrontEndArt.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/FrontEndArt.kt) |
| Per-source and per-state chips | [StoreChips.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/StoreChips.kt) |
| Contrast-safe theme derivation and an animation-scale gate | [ThemeColors.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/ThemeColors.kt), [Theme.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/Theme.kt) |
| One gliding focus ring shared by all controls, suppressed when animations are off | [FocusGlide.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/FocusGlide.kt) |
| Loading UI with named stages, detail text, progress segments, cancel/report/retry and auto-focused first action | [SessionLoading.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/SessionLoading.kt) |
| Designed empty state, not a text line | [EmptyLibrary.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/EmptyLibrary.kt) |
| Time-limited discoverability hint and hidden corner entry | [DrawerEntryHint.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/DrawerEntryHint.kt) |
| Labels that shrink to fit instead of clipping | [FitText.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/FitText.kt) |
| Device-less Compose previews for empty/library/runtime-missing states | [compose-previews.md](https://github.com/devx1626/DroidDeck/blob/main/docs/compose-previews.md), [LauncherPreviews.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/debug/java/com/droiddeck/launcher/ui/LauncherPreviews.kt) |

Also relevant: heavy use of `Role`, `contentDescription`, `onClickLabel`, `focusGroup`,
explicit `FocusRequester`s and `bringIntoViewRequester`, and `testTagsAsResourceId`
([TestTags.kt](https://github.com/devx1626/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/ui/TestTags.kt)).
Do **not** import its blur, infinite canvas, coroutines or Material components wholesale —
they are wrong for the Helio A22.

### Cinnamon — search, states and desktop conventions

Cinnamon is GPL-2.0-or-later. Its menu is a two-pane layout (sidebar + search/categories/app
list); its overview is a modal shaded layer; its notification tray has urgency levels and
transient/resident behaviour; its settings are searchable and hide empty categories.

| Pattern | Evidence |
| --- | --- |
| Two-pane menu IA with independently hideable sidebar/favorites/recents | [menu settings-schema.json](https://github.com/linuxmint/cinnamon/blob/master/files/usr/share/cinnamon/applets/menu@cinnamon.org/settings-schema.json), [_startmenu.scss](https://github.com/linuxmint/cinnamon/blob/master/data/theme/cinnamon-sass/widgets/_startmenu.scss) |
| Keyboard-first list navigation that skips non-interactive children | [applet.js](https://github.com/linuxmint/cinnamon/blob/master/files/usr/share/cinnamon/applets/menu@cinnamon.org/applet.js) (`VisibleChildIterator`, `_onKeyPressEvent`) |
| Weighted, debounced, stale-result-safe search: name > keywords > description > id; first result focused; clear icon while searching | [applet.js](https://github.com/linuxmint/cinnamon/blob/master/files/usr/share/cinnamon/applets/menu@cinnamon.org/applet.js) (`MATCH_ADDERS`, `_doSearch`) |
| Favourites as an ordered id list with change signals and drag-to-reorder | [appFavorites.js](https://github.com/linuxmint/cinnamon/blob/master/js/ui/appFavorites.js) |
| Responsive icon grid with minimum icon size and pagination; animation defaults off | [iconGrid.js](https://github.com/linuxmint/cinnamon/blob/master/js/ui/iconGrid.js) |
| Modal overview with a temporary cover pane, gesture cleanup and input suppression | [overview.js](https://github.com/linuxmint/cinnamon/blob/master/js/ui/overview.js) |
| Notification urgency/transient logic and a pluralised empty state | [messageTray.js](https://github.com/linuxmint/cinnamon/blob/master/js/ui/messageTray.js), [notifications applet](https://github.com/linuxmint/cinnamon/blob/master/files/usr/share/cinnamon/applets/notifications@cinnamon.org/applet.js) |
| Reusable empty `Placeholder` (icon + title + optional description) | [placeholder.js](https://github.com/linuxmint/cinnamon/blob/master/js/ui/placeholder.js) |
| Semantic theme tokens instead of raw colours | [_colors.scss](https://github.com/linuxmint/cinnamon/blob/master/data/theme/cinnamon-sass/_colors.scss) |
| Dialog focus discipline: grab initial focus, restore it, Escape = No, raise IME above lightbox | [modalDialog.js](https://github.com/linuxmint/cinnamon/blob/master/js/ui/modalDialog.js) |

### COSMIC — tokens, search states and reserved space

COSMIC is a set of repos; the umbrella has no root licence, but concrete components are
GPL-3.0 and the `libcosmic` toolkit is MPL-2.0. The COSMIC name/logo/App-ID prefix are
trademarks ([TRADEMARK.md](https://github.com/pop-os/cosmic-epoch/blob/master/TRADEMARK.md)).

| Pattern | Evidence |
| --- | --- |
| Search is always active; Enter activates, Tab moves focus, Escape clears; result index resets on query change | [cosmic-launcher/src/app.rs](https://github.com/pop-os/cosmic-launcher/blob/master/src/app.rs) |
| Result rows have distinct active/hovered/pressed styles | [app.rs](https://github.com/pop-os/cosmic-launcher/blob/master/src/app.rs) |
| Workspace cards use a 4dp accent border when active; hover/pin affordances **reserve space even when hidden** | [cosmic-workspaces-epoch/src/view/mod.rs](https://github.com/pop-os/cosmic-workspaces-epoch/blob/master/src/view/mod.rs) |
| High-contrast dark/light theme variants and a layered surface model | [libcosmic/src/theme/mod.rs](https://github.com/pop-os/libcosmic/blob/master/src/theme/mod.rs) |
| 4dp-based spacing scale, small radius set, compact/standard/spacious density | [spacing.rs](https://github.com/pop-os/libcosmic/blob/master/cosmic-theme/src/model/spacing.rs), [corner.rs](https://github.com/pop-os/libcosmic/blob/master/cosmic-theme/src/model/corner.rs) |
| Keyboard nav (Tab/Shift+Tab, Escape, F11, Ctrl+F) and accessibility as a default feature | [keyboard_nav.rs](https://github.com/pop-os/libcosmic/blob/master/src/keyboard_nav.rs), [Cargo.toml](https://github.com/pop-os/libcosmic/blob/master/Cargo.toml) |
| Component state model: base/hover/pressed/selected/focus/disabled/on, with disabled alpha 0.5 | [style/button.rs](https://github.com/pop-os/libcosmic/blob/master/src/theme/style/button.rs) |
| Spring motion exists but is deliberately not recommended here | [spring.rs](https://github.com/pop-os/cosmic-comp/blob/master/src/backend/render/animations/spring.rs) |

---

## 3. Cross-cutting patterns worth borrowing

1. **Search is a surface, not a filter box.** All three projects give search its own focus
   model, clear affordance, result ranking and no-results state. IndieHost's drawer currently
   has only a text field and a "no matches" message; the placeholder even says "Search your
   library" although it filters installed apps.
2. **Explicit states beat silence.** Empty, loading, blocked, gone and error states are
   first-class UI with a title, a reason and a next action. IndieHost has status text and a
   spinner, but no shared pattern for "why can't I launch this?" or "what just failed?".
3. **Reserve space for conditional badges.** A badge appearing should not reflow a row.
4. **Theme tokens should carry meaning.** Surfaces, on-surface, focus, disabled and error
   states belong in one place; IndieHost already has a palette object but still hardcodes a
   few colours in composables.
5. **Motion should be cheap and optional.** Cinnamon's menu animation defaults off and
   DroidDeck suppresses focus glide when animations are disabled. On the A22, prefer no
   animation or a single 120–150 ms fade; no blur or spring solvers.
6. **Focus and semantics are part of the design.** Keyboard/TalkBack order, content
   descriptions, auto-focused first action and focus restoration are consistent patterns.
7. **Modal layers need discipline.** Scrim, input suppression, Escape/Back dismissal and
   focus restoration apply to IndieHost's detail dialog and WebView exit confirmation.

---

## 4. Concrete applications to IndieHost (ranked)

These are original re-implementations using the existing `Thread` + `mutableStateOf`
patterns, no new dependencies, no `INTERNET`, no copied art.

### P0 — small, high value

| # | Change | Files | Why / evidence |
| --- | --- | --- | --- |
| 1 | **Make drawer search real.** Autofocus the field on open, normalise case/accents, keep a clear button when non-empty, order results deterministically, and fix `search_apps` from "Search your library" to "Search apps". Keep Back/Escape closing the drawer. | `HomeUi.kt` (`AppDrawer`), `MainActivity.kt`, `strings.xml` | Cinnamon `_doSearch`, COSMIC launcher search. |
| 2 | **One reusable `StateCard`** (icon/letter, title, optional "how to fix" line, action) for empty drawer results, empty library, scan progress, missing runtime and WebView failure. | `HomeUi.kt`, `strings.xml` | Cinnamon `placeholder.js`, DroidDeck `EmptyLibrary.kt`. |
| 3 | **Semantic state colours and small-text a11y.** Map focus/pressed/disabled to `Indie.Accent`/`Indie.AccentDeep`/`Indie.Text.copy(alpha = .5f)` in one place; raise 10sp status labels to ≥12sp; add content descriptions for lettered cover placeholders; keep ≥48dp targets. | `IndieTheme.kt`, `HomeUi.kt` | COSMIC component-state model, Cinnamon a11y. |
| 4 | **Reserve a fixed badge slot** in `RecentTile`/`GameRow` so PIN NEEDED / M3 / BLOCKED appearing never reflows the row. | `HomeUi.kt` | COSMIC workspaces hidden-until-hover pin button. |

### P1 — meaningful UX improvement

| # | Change | Files | Why / evidence |
| --- | --- | --- | --- |
| 5 | **Staged status with actions.** Replace bare status text/spinner with a compact card: stage label ("Reading data.win", "Scanning folders", "Starting game"), reason, and Retry/Close where applicable. | `MainActivity.kt` (`status`, `scanMessage`), `HomeUi.kt` | DroidDeck `SessionLoading.kt`. |
| 6 | **Detail dialog focus discipline and two-step Remove.** Auto-focus Play/link on open, restore focus on dismiss, and require a confirm for destructive Remove. | `HomeUi.kt` (`GameDetailDialog`), `MainActivity.kt` | Cinnamon `modalDialog.js`, DroidDeck `CardDetailHost.kt`. |
| 7 | **WebView loading + inline blocked state.** Show a minimal "Loading game…" overlay until first page finish; replace the throttled `Toast` with an inline "Network requests are blocked" label plus a "Back to library" action. Keep `blockIfRemote` and the exit confirmation. | `GameWebViewActivity.kt`, `strings.xml` | Cinnamon transient-failure affordance, DroidDeck ended actions. |
| 8 | **Debug-only Compose previews** for empty library, loading apps, blocked game, no usage access and no drawer matches; document how to inspect them without a device. | new `app/src/debug/.../LauncherPreviews.kt`, `TESTING.md` | DroidDeck preview suite. |

### P2 — only after user evidence

| # | Change | Files | Why / evidence |
| --- | --- | --- | --- |
| 9 | **Split the long home column into lightweight panes** (Home / Library / Tools) with one `selectedPane` state and a BackHandler chain drawer → detail → pane → home. Do not add a navigation framework. | `MainActivity.kt`, `HomeUi.kt` | DroidDeck rail/pane IA. Risk: adds taps on the primary launcher surface. |
| 10 | **A single settings surface** grouped by task, with dependency-aware visibility (scan options only when a folder is granted). | new settings sheet, `IndieTheme.kt`, `strings.xml` | Cinnamon settings IA. Defer until there are real settings. |

### Explicitly not recommended

- Copying any upstream Kotlin/Rust/SCSS code, colour hexes, fonts, icons or wallpapers.
- Blur backdrops, spring solvers, focus-glide animations or continuous loops on the A22.
- Replacing `mutableStateOf`/plain `Thread` with coroutines/DI just to match these projects.
- Changing the application id, signing, or release process (CON constraints).
- Adding a network permission or remote service for storefront-style features.

---

## 5. Suggested sequencing

1. Do P0 items 1–4 together as one UI-consistency change; they are small, testable with
   previews, and do not touch launch behaviour.
2. Do P1 items 5–8 as a "feedback and states" change, after MT-21…MT-34 can be run so the new
   states can be verified on hardware.
3. Revisit P2 only if usability testing shows the single-column home is a real problem.

Nothing in this document changes the ship criteria: the host gate, device checks and
compatibility matrix remain the source of truth.
