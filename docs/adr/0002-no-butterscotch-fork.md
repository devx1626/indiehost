# ADR-0002: Never fork or link Butterscotch

- **Status:** Accepted
- **Date:** 2026-11-24
- **Deciders:** project maintainer
- **Related:** [ADR-0001](0001-shortcut-handoff.md), [LICENSING](../../LICENSING.md), [spec.md](../../spec.md) CON-01

## Context

Butterscotch is AGPL-3.0. It is a from-scratch C reimplementation of the GameMaker runner
and the best available Android runtime for VM builds. IndieHost needs to start games but
must not turn itself into a derivative work or take on AGPL source-offer obligations.

## Decision

IndieHost treats Butterscotch as a **separate application**:

- The user installs it from the Play Store or a signed APK.
- IndieHost starts its exported activities and pinned shortcuts through Android IPC.
- No Butterscotch source, native library, asset or runner code is copied, linked, bundled
  or repackaged.
- No JNI, AIDL, shared library or ContentProvider connects the two apps.

## Consequences

**Positive**

- The launcher stays under its own licence; no AGPL obligation attaches to this repository.
- Butterscotch can be updated independently.
- No maintenance burden for a C runner we did not write.

**Negative**

- Installation is two apps, not one.
- IndieHost cannot change Butterscotch's import flow, save handling or UI.
- The integration is limited to public Android surfaces and can break if Butterscotch
  changes its shortcut contract.
- The user must import each game into Butterscotch and pin it.

**Neutral**

- If the product ever needs a custom runner, that is a new, explicitly AGPL-licensed
  project, not a quiet fork of this one.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Fork `ButterscotchAndroid` and add an import Intent | Makes the combined work AGPL-3.0; we would have to publish the fork's source and maintain it; no capability we need beyond the shortcut route |
| Bundle a prebuilt Butterscotch APK | Redistribution and AGPL compliance burden; Play Store updates bypass us; user trust |
| Link its native libraries into IndieHost | Clearly a derivative work; also an ABI/packaging nightmare |
| Reimplement the runner | Out of scope for a launcher |

## References

- AGPL-3.0, especially §5 (conveying) and §13 (remote network interaction).
- [LICENSING.md](../../LICENSING.md) for the practical boundary and what would change it.
