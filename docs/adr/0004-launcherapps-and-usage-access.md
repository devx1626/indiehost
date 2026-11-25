# ADR-0004: `LauncherApps` for apps and shortcuts; usage access optional

- **Status:** Accepted
- **Date:** 2026-11-24
- **Deciders:** project maintainer
- **Related:** [ARCHITECTURE](../../ARCHITECTURE.md) §4, [SECURITY](../../SECURITY.md) §3

## Context

A home app needs two system data sources:

1. The list of launchable apps for the drawer.
2. A recents list.

Android has no public "recent apps" API for launchers. The options are the usage-access
app-op, an AccessibilityService, or remembering our own launches. The drawer has two
options: a raw `PackageManager` query or `LauncherApps`.

## Decision

- **Drawer and shortcuts:** use `LauncherApps`.
  - `getActivityList(null, Process.myUserHandle())` for launchable activities.
  - `startMainActivity` to launch.
  - `hasShortcutHostPermission`, `getShortcuts(FLAG_MATCH_PINNED)` and `startShortcut` for
    Butterscotch games.
- **Recents:** use usage access when the user grants it, and always merge locally
  remembered launches. Never request an AccessibilityService.

## Consequences

**Positive**

- `LauncherApps` returns exactly what a launcher should show and start; it is the API the
  system intends for this role.
- Shortcut-host permission comes with the home role, so the game library needs no extra
  permission.
- Recents works at all without a dangerous permission, and improves when the user opts in.
- No accessibility abuse, no background service, no polling.

**Negative**

- Without usage access, recents only shows apps opened from IndieHost.
- Usage access is an app-op buried in Settings; the UI must explain and deep-link it.
- `LauncherApps` calls can throw `SecurityException`/`IllegalStateException` when the
  profile is locked or the home role changes; every call site must handle it.
- A pin belongs to the accepting launcher (see ADR-0001).

**Neutral**

- The drawer excludes IndieHost itself; the Home key is the way back.
- One tile per package, even if an app registers several launcher activities.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| `PackageManager.queryIntentActivities` | Includes non-launcher activities, needs manual filtering, and is not the launcher API |
| AccessibilityService for recents | Gross overreach, battery cost, user-trust violation, Play policy risk |
| `ActivityManager.getRunningTasks` | Deprecated/restricted; not a public recents API |
| No recents row | Worse product; local launches alone are a reasonable fallback, not the whole feature |
| `UsageStatsManager.queryUsageStats` only | Less precise than `queryEvents` for "brought to foreground"; events give the ordering we need |
