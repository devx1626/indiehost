# ADR-0001: Hand games to Butterscotch through pinned shortcuts

- **Status:** Accepted
- **Date:** 2026-11-24
- **Deciders:** project maintainer
- **Related:** [ADR-0002](0002-no-butterscotch-fork.md), [spec.md](../../spec.md) §3, [ARCHITECTURE](../../ARCHITECTURE.md) §4.1

## Context

Butterscotch's `GameActivity` is `android:exported="false"` and has no import Intent. A
launcher therefore cannot hand it a game folder directly. The naive conclusion — "a
launcher can never start a game" — is wrong; it only rules out the direct-Intent route.

Android gives the current home app **shortcut-host permission**. Butterscotch already
supports "Add Shortcut to Home Screen" and names each game shortcut `game-<uuid>`. The
system records a pin against the launcher that accepts it.

## Decision

Use the shortcut route:

1. IndieHost declares `CATEGORY_HOME` and can be selected as the home app.
2. It declares an activity answering `android.content.pm.action.CONFIRM_PIN_SHORTCUT`.
3. `PinShortcutActivity` accepts a pin request and finishes.
4. The home screen reads pinned shortcuts through `LauncherApps` with
   `FLAG_MATCH_PINNED`, filtered to the `game-` prefix.
5. Tapping a tile calls `LauncherApps.startShortcut`, which starts Butterscotch's own
   activity in its own process.

No game data crosses the boundary; nothing is linked or repackaged.

## Consequences

**Positive**

- Works on stock Android with no root, no fork and no AGPL obligation.
- The runner owns import, saves and its own process lifecycle.
- A launcher-appropriate mechanism: pins are a public Android API.

**Negative**

- A pin belongs to the accepting launcher. Games pinned while another launcher was active
  must be re-pinned with IndieHost as home.
- IndieHost cannot pass a folder directly; the user must import into Butterscotch first.
- The route depends on Butterscotch continuing to name game shortcuts `game-` and exposing
  them as pinned shortcuts. A future change there breaks the integration.
- `startShortcut` can fail for stale shortcuts or revoked permission; the UI must explain
  the recovery (re-pin).

**Neutral**

- Reading a pinned shortcut does not reveal its Intent; the launcher gets id, label and
  icon only. That is enough for a tile, but not for deep game metadata.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Direct Intent to `GameActivity` | Not exported; impossible without changing Butterscotch |
| Fork Butterscotch to export an import Intent | AGPL-3.0 combined work, maintenance burden, no extra capability (see ADR-0002) |
| Read the Intents Butterscotch fires | Android strips them for other apps |
| AccessibilityService to click through Butterscotch | Gross overreach, fragile, violates user trust |
| Reimplement a GameMaker runner | A multi-year project; Butterscotch already exists |
