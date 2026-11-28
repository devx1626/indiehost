# ADR-0009: LÖVE is detected; handoff is a candidate, not built in M3

- **Status:** Accepted
- **Date:** 2026-11-27
- **Deciders:** project maintainer
- **Related:** [ROADMAP](../../ROADMAP.md) M3, [spec.md](../../spec.md) FR-24

## Context

LÖVE has two Android distribution models:

1. A **generic player APK** that can open external `.love` files (the official
   `org.love2d.android` build).
2. An **embed/fused build** that packages one game into its own APK/AAB.

The scanner recognises `.love` folders. A handoff to the generic player looks plausible
(`ACTION_VIEW` with a `content://` URI and `FLAG_GRANT_READ_URI_PERMISSION`), but the
public intent contract, package visibility rules and SAF grant propagation have not been
verified on a device.

## Decision

- Keep LÖVE detection and show a reason in the library: "LÖVE's Android player can open
  this `.love` file; handoff is not wired up yet."
- Do **not** add a runtime adapter or an untested intent in M3. An adapter that silently
  fails would be worse than an honest "detected, not launchable" row.
- Do **not** build or repackage any `.love` file into an APK.

## Consequences

**Positive**

- No unverified IPC contract is shipped.
- No fork or repackage of LÖVE's player, and no new permission.

**Negative**

- Detected LÖVE games cannot be launched from IndieHost yet.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Ship the fused game inside IndieHost | Repackages a runtime/game; licensing and scope problems |
| Try `ACTION_VIEW` on the `.love` URI now | Unverified package/id and grant behaviour; would produce silent failures |

## Open questions

- Does the official player accept a SAF `content://` URI with a temporary read grant?
- Which package name should be in `<queries>`?
- If those answers are yes on a device, add a `LoveHandoffAdapter` in a later milestone.
