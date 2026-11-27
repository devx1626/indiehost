# ADR-0008: Ren'Py is detected but not hosted by IndieHost

- **Status:** Accepted
- **Date:** 2026-11-27
- **Deciders:** project maintainer
- **Related:** [ROADMAP](../../ROADMAP.md) M3, [spec.md](../../spec.md) FR-24

## Context

Ren'Py's Android support is packaging-centric: RAPT (Ren'Py Android Packaging Tool) builds
a **separate, signed APK per game**, optionally split into Play asset packs. There is no
generic installed Ren'Py player that accepts a `content://` folder or `.rpy` script from
another app. The detector already recognises Ren'Py folders, so silently dropping them
would be a bad user experience.

## Decision

- Keep Ren'Py detection.
- Do **not** add a `RuntimeAdapter`, build tools, or an APK-building/orchestration flow.
- The library shows a clear reason: "Ren'Py ships a per-game Android APK. Install it to
  play." Launch stays disabled.
- No network permission or remote service is added to help with this.

## Consequences

**Positive**

- No false promise: a detected game is never shown as launchable when it cannot be.
- No build-tool chain (JDK, SDK, keys) is pulled into the launcher.
- No AGPL/SDK licensing question from bundling a runtime.

**Negative**

- IndieHost cannot play a Ren'Py game from the scanned folder itself.
- The user must obtain or install the game's own Android build.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Bundle a shared Ren'Py runtime | None exists; per-game native APK is the only supported model |
| Orchestrate RAPT builds on-device | Needs JDK/SDK/network, signing keys and large disk; out of scope |
| Detect an installed per-game APK and launch it | No stable package/id convention to map a `.rpy` folder to an APK |

## Open questions

- If a future Ren'Py release ships a generic player with a public intent, revisit with a new
  ADR and add a handoff adapter.
