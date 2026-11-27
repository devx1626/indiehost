# UI/UX completion roadmap

> **Status:** Working roadmap derived from [the full repository survey](../research/repository-survey-2026-11-27.md).
> **Goal:** Finish the launcher's UI/UX and trust model with original, MIT-safe code, then
> stop. Emulation is a separate, gated future phase.

## Where we are now

- M1–M4 implemented: Room library, SAF scan, merge/detail/covers, offline MV/MZ WebView,
  engine statuses, compatibility template.
- P0/P1 of the earlier UI findings implemented: real drawer search, shared state cards,
  semantic tokens, reserved badge slots, detail focus/confirmation, WebView loading/blocked
  overlay, debug-only Compose previews.
- Host gate green: 19 detector + 3 mapping + 3 registry + 25 app tests; debug APK and signed
  v0.4.0 release built.
- Device verification still open (MT-01…MT-34 and `GameDaoTest`). No performance claims.

## Definition of UI/UX complete

UI/UX work is complete when:

1. Every interactive surface has an explicit empty, loading, error and success state.
2. Pin requests, SAF grants and launches are validated and fail visibly without crashing.
3. Search is local, ranked, accent-insensitive and has a clear affordance.
4. TalkBack focus order and ≥48 dp targets are verified for home, drawer, detail and WebView.
5. Motion is gated on system animation scale; no blur/spring on the Helio A22.
6. Device-less preview renders cover every state in CI.
7. MT-01…MT-34 and `GameDaoTest` have actually run on the baseline device.
8. `COMPATIBILITY.md` contains no guessed numbers and is checked by a host test.
9. Docs, spec, architecture and testing match the shipped behaviour.
10. No hard-rule change was needed to get here.

---

## Phase 1 — Trust and validation (do first)

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| Add `PinItemRequest.isValid` check, re-resolve shortcut, filter `isEnabled && isPinned`, surface accept failure | DroidDeck/ARK pin flow | `PinShortcutActivity.kt`, `ButterscotchShortcuts.kt` | Host test for decision logic; manual case for foreign/cancelled request |
| Add `dataExtractionRules` excluding prefs, Room DB and covers; move covers to `noBackupFilesDir` or exclude explicitly | Android auto-backup docs, DroidDeck private-path model | `AndroidManifest.xml`, new `res/xml/data_extraction_rules.xml`, `CoverStore.kt` | Manifest test asserts rules; no game data in backup |
| Harden WebView: `blockNetworkLoads`, `MIXED_CONTENT_NEVER_ALLOW`, per-game origin isolation decision, SVG policy, path/byte caps, cache clear on destroy | Android WebViewAssetLoader/WebSettings docs, security report | `GameWebViewActivity.kt`, `WebPaths.kt`, `SafTreeResolver.kt` | Extend `WebPathsTest`; device case confirms local load still works |
| Add a one-shot, TTL'd in-process launch token instead of trusting intent extras | DroidDeck `LauncherHandoff`, COSMIC one-shot handover | `MainActivity.kt`, `RpgMakerRuntimeAdapter.kt` | Pure TTL/one-shot host test |

## Phase 2 — Library lifecycle and scan honesty

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| Persist scan runs with `scanId`, phase timings and counts; mark rows `missing` via `lastSeenScanId` instead of deleting | AntennaPod, DroidDeck phased scan | `library/`, `GameScanner.kt`, `MainActivity.kt` | Room/host test: removed file keeps row and pin order |
| Single-query SAF child listing instead of per-document calls | AntennaPod `FastDocumentFile` | `SafTreeResolver.kt`, `SafGameFolder.kt` | Query-count benchmark/test: O(folders), not O(files) |
| Duplicate detection with `duplicateOf` and canonical key; flag, never auto-delete | AntennaPod `FeedItemDuplicateGuesser` | `Game.kt`, `GameEntity.kt`, `GameScanner.kt` | Table-driven pure tests |
| Additive migration skeleton with defaults; replace destructive migration before public release | AntennaPod `DBUpgrader`, COSMIC config versioning | `IndieHostDatabase.kt`, `GameEntity.kt` | Migration test preserves user-owned fields |
| Per-root regrant UI: "Folder no longer available — reconnect" rather than empty library | Material Files, Android SAF docs | `GameFolderStore.kt`, `HomeUi.kt`, `strings.xml` | Manual case revoke→regrant; no duplicate roots |
| Progressive/cancellable scan progress on worker threads with generation tokens | Cinnamon, COSMIC, DroidDeck | `MainActivity.kt`, `HomeUi.kt` | Progress/cancel host test; no recomposition storm |

## Phase 3 — Navigation and feedback

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| Readiness-gated one-shot startup reveal with a failure path that still shows the UI | Cinnamon `main.js`, Bazzite preflight | `MainActivity.kt`, `HomeUi.kt` | Preview of loading/failure states |
| Tiered search ranking (name > keywords > path) with stable sort; extend `AppSearch` | Cinnamon `MATCH_ADDERS`, ARK `SettingsSearch`, Lawnchair | `AppSearch.kt`, `HomeUi.kt` | Table-driven host tests |
| Urgency/status state machine for scan, pin, launch and check feedback; PASS/FAIL/SKIP semantics | Cinnamon `messageTray`, Bazzite diagnostics, DroidDeck device script | `HomeUi.kt`, `MainActivity.kt`, `strings.xml` | No silent pass; explicit next action on failure |
| Capability snapshot (home role, Butterscotch installed, SAF grants, WebView) drives visible actions and disabled explanations | Cinnamon capability gating, COSMIC `toggler_maybe` | `MainActivity.kt`, `HomeUi.kt` | Host test for capability-to-action mapping |
| Bounded, idle-delayed recents with cap and privacy explanation | Cinnamon doc-system, Cinnamon privacy panel | `Recents.kt`, `HomeUi.kt`, `strings.xml` | Pure recents test; clear action visible |

## Phase 4 — Accessibility and visual discipline

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| Contrast tests for every palette pair and accent/ink combination | DroidDeck contrast tests | `IndieTheme.kt`, host test | 4.5:1 floor asserted over sampled gradients |
| Reduced-motion gate via `Settings.Global.ANIMATOR_DURATION_SCALE`; one short fade maximum | COSMIC open issue, Cinnamon software-render detection | `IndieTheme.kt`, `MainActivity.kt`, `HomeUi.kt` | Setting respected; no spring/blur |
| TalkBack focus order and content descriptions for home, drawer, detail, WebView | Lawnchair/ARK a11y, Cinnamon accessible names | `HomeUi.kt`, `GameWebViewActivity.kt` | Manual a11y checklist in `TESTING.md` |
| Device-less preview PNG renders at handheld + portrait, failing on flat colour | DroidDeck `PreviewRenderTest` | debug/testDebug source sets, `TESTING.md` | CI job green offline (cache caveat documented) |

## Phase 5 — Settings and onboarding (only if user evidence supports it)

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| First-run steps: welcome → home role → SAF grant → scan → pin → launch, with skip and contextual help | Bazzite Portal/preflight, Pop!_OS installer, ARK onboarding | new onboarding composable, `MainActivity.kt`, `strings.xml` | Every step has help and a Back path |
| One settings screen with dependency-gated rows and versioned prefs | Cinnamon schema, COSMIC config, ARK settings index | new settings sheet, `IndieTheme.kt` | Hidden/disabled states tested; reversible toggles |
| Local diagnostics/privacy screen: what is stored, retention, copy-report action, no upload | Pop!_OS support-panel, Cinnamon privacy, Bazzite `get-logs` | new screen, `strings.xml` | No serial/IMEI/paths/game titles in report |
| Optional physical-key navigation (D-pad/Tab, Escape) | COSMIC `keyboard_nav`, Lawnchair a11y | `HomeUi.kt` | Decide first; touch-only is acceptable |

## Phase 6 — Verification and release hygiene

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| Device script with PASS/FAIL/SKIP, exit codes, snapshot/restore traps, device identity, crash-buffer check | DroidDeck `device-smoke.sh` | `tools/device_checks.sh` | Script run on baseline; restore verified |
| Dated device report template with APK SHA, method, per-run table and "Validation and limits" | DroidDeck test reports | `TESTING.md`, `docs/testing/` | No overclaiming |
| Machine-checked compatibility matrix with supported/degraded/unsupported tiers | Bazzite `test_builder.py`, COSMIC versioning | `COMPATIBILITY.md`, host test | Matrix edits caught by tests |
| Release notes checked in before release with provenance and SHA256SUMS | DroidDeck `release.yml` | `docs/releases/`, CI | Publish refuses missing notes |
| CI gate: required-job aggregator, no key in tree, version sanity, `:detect` purity, always-upload diagnostics | DroidDeck CI | `.github/workflows/android.yml` | Green on PR; no sign/push without instruction |

---

## Sequencing and dependencies

1. **Phase 1 + Phase 4 first** — they close real trust/a11y gaps and are mostly host-testable.
2. **Phase 2 next** — it changes Room schema and scan behaviour; needs additive migrations before release.
3. **Phase 3** — builds on generation tokens and capability state from phases 1–2.
4. **Phase 5** — evidence-gated; do not add settings/onboarding until usability testing shows a need.
5. **Phase 6** — continuous; each phase adds tests and docs, final gate closes UI/UX.

## Decisions needed from the maintainer

- Approve Phase 1 security changes (especially WebView origin/SVG policy and backup rules).
- Choose recents cap/privacy policy and whether a local diagnostics screen is wanted.
- Decide touch-only vs keyboard/D-pad navigation.
- Decide when destructive Room migration must become additive.
- Approve device verification on the maintainer's workstation with a legal game copy.
- Confirm that "UI/UX complete" does **not** include emulation; see
  [future emulation boundary](../research/future-emulation-boundary.md).
