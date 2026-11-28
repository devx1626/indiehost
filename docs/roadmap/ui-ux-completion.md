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
- Host gate green: 19 detector + 3 mapping + 7 runtime + 37 app tests; debug APK and signed
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
| [x] Add `PinItemRequest.isValid` check, re-resolve shortcut, filter `isEnabled && isPinned`, surface accept failure — `PinPolicy` pure helper + 4 host tests; manual case MT-35 still open | DroidDeck/ARK pin flow | `PinPolicy.kt`, `PinShortcutActivity.kt`, `ButterscotchShortcuts.kt` | Host test for decision logic; manual case for foreign/cancelled request |
| [x] Add `dataExtractionRules` excluding prefs, Room DB and covers; covers excluded explicitly rather than moved | Android auto-backup docs, DroidDeck private-path model | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` | Resource compiles with the manifest; D2D behaviour still needs a device check |
| [x] Harden WebView: `blockNetworkLoads`, `MIXED_CONTENT_NEVER_ALLOW`, path segment/length caps, 64 MB asset cap, cache clear on destroy, per-game origin port, SVG as octet-stream | Android WebViewAssetLoader/WebSettings docs, security report | `GameWebViewActivity.kt`, `WebPaths.kt`, `SafTreeResolver.kt` | MT-36 must confirm local load still works with `blockNetworkLoads=true` |
| Add a one-shot, TTL'd in-process launch token instead of trusting intent extras | DroidDeck `LauncherHandoff`, COSMIC one-shot handover | `MainActivity.kt`, `RpgMakerRuntimeAdapter.kt` | Pure TTL/one-shot host test |

## Phase 2 — Library lifecycle and scan honesty

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| [x] Persist scan runs with `scanId`, timestamps and counts; mark rows unavailable via `lastSeenScanId` instead of deleting (Room v2 + additive migration) — host tests assert watermark and MISSING reason; MT-39 still open | AntennaPod, DroidDeck phased scan | `library/`, `GameScanner.kt`, `MainActivity.kt` | Room/host test: removed file keeps row and pin order |
| [x] Single-query SAF child listing instead of per-document calls | AntennaPod `FastDocumentFile` | `SafTreeResolver.kt`, `SafGameFolder.kt` | One DocumentsContract query per folder; code reviewed, provider benchmark still needs a device |
| Duplicate detection with `duplicateOf` and canonical key; flag, never auto-delete | AntennaPod `FeedItemDuplicateGuesser` | `Game.kt`, `GameEntity.kt`, `GameScanner.kt` | Table-driven pure tests |
| Additive migration skeleton with defaults; replace destructive migration before public release | AntennaPod `DBUpgrader`, COSMIC config versioning | `IndieHostDatabase.kt`, `GameEntity.kt` | Migration test preserves user-owned fields |
| Per-root regrant UI: "Folder no longer available — reconnect" rather than empty library | Material Files, Android SAF docs | `GameFolderStore.kt`, `HomeUi.kt`, `strings.xml` | Manual case revoke→regrant; no duplicate roots |
| [x] Progressive/cancellable scan on the worker thread; `ScanProgress` events update `scanMessage` | Cinnamon, COSMIC, DroidDeck | `GameScanner.kt`, `MainActivity.kt` | Host test asserts progress and immediate stop on cancel |

## Phase 3 — Navigation and feedback

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| [x] Readiness-gated startup reveal: spinner until the first Room read completes, then the UI even if the read failed | Cinnamon `main.js`, Bazzite preflight | `MainActivity.kt` | No permanent splash; failure still reveals with status |
| [x] Tiered search ranking (label prefix > label substring > package) with stable sort; `AppSearchTest` covers it | Cinnamon `MATCH_ADDERS`, ARK `SettingsSearch`, Lawnchair | `AppSearch.kt`, `HomeUi.kt` | Table-driven host tests |
| Urgency/status state machine for scan, pin, launch and check feedback; PASS/FAIL/SKIP semantics | Cinnamon `messageTray`, Bazzite diagnostics, DroidDeck device script | `HomeUi.kt`, `MainActivity.kt`, `strings.xml` | No silent pass; explicit next action on failure |
| Capability snapshot (home role, Butterscotch installed, SAF grants, WebView) drives visible actions and disabled explanations | Cinnamon capability gating, COSMIC `toggler_maybe` | `MainActivity.kt`, `HomeUi.kt` | Host test for capability-to-action mapping |
| Bounded, idle-delayed recents with cap and privacy explanation | Cinnamon doc-system, Cinnamon privacy panel | `Recents.kt`, `HomeUi.kt`, `strings.xml` | Pure recents test; clear action visible |

## Phase 4 — Accessibility and visual discipline

| Task | Source patterns | Files | Acceptance |
| --- | --- | --- | --- |
| [x] Contrast tests for every text/surface and accent/ink combination — `ContrastTest`; `TextMuted` lifted to `#939CA5` so raised cards also clear 4.5:1 | DroidDeck contrast tests | `IndieTheme.kt`, `Contrast.kt`, host test | 4.5:1 floor asserted on all four surfaces |
| [x] Reduced-motion gate via `Settings.Global.ANIMATOR_DURATION_SCALE`; the only animation (`animateContentSize` on state cards) is disabled when scale is 0 | COSMIC open issue, Cinnamon software-render detection | `Motion.kt`, `HomeUi.kt`, host test | Setting respected; no spring/blur |
| [ ] TalkBack focus order — decorative marks and letter placeholders are hidden from semantics; manual MT-37 still open | Lawnchair/ARK a11y, Cinnamon accessible names | `HomeUi.kt`, `GameWebViewActivity.kt` | Manual a11y checklist in `TESTING.md` |
| [ ] Device-less preview PNG renders; [x] preview state coverage test verifies all key states still have previews | DroidDeck `PreviewRenderTest` | debug/testDebug source sets, `TESTING.md` | PNG rendering deferred until the offline Robolectric jar story is settled |

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
| [x] Device script with PASS/FAIL/SKIP, exit codes 0/1/2, device identity, crash-buffer check; [ ] snapshot/restore traps still to add | DroidDeck `device-smoke.sh` | `tools/device_checks.sh` | Script run on baseline; restore verified |
| Dated device report template with APK SHA, method, per-run table and "Validation and limits" | DroidDeck test reports | `TESTING.md`, `docs/testing/` | No overclaiming |
| [x] Machine-checked engine-route table; [ ] device tier rows still need real measurements | Bazzite `test_builder.py`, COSMIC versioning | `COMPATIBILITY.md`, `CompatibilityTest.kt` | Engine additions caught by host test |
| [x] Release notes checked in with provenance and SHA-256; [ ] CI publish gate still to add | DroidDeck `release.yml` | `docs/releases/0.4.0.md`, CI | Publish refuses missing notes |
| [x] CI runs all host tests and gates on `:detect` purity and tracked keys; [ ] required-job aggregator/artifacts still to add | DroidDeck CI | `.github/workflows/android.yml` | Green on PR; no sign/push without instruction |

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
