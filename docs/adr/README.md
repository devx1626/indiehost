# Architecture Decision Records

Short records of decisions that are expensive to reverse. Format: context, decision,
consequences, alternatives. New records are numbered sequentially and never rewritten;
supersede an old one with a new ADR instead.

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-shortcut-handoff.md) | Hand games to Butterscotch through pinned shortcuts | Accepted |
| [0002](0002-no-butterscotch-fork.md) | Never fork or link Butterscotch | Accepted |
| [0003](0003-module-boundaries.md) | Four modules with a pure-Kotlin detector | Accepted |
| [0004](0004-launcherapps-and-usage-access.md) | `LauncherApps` for apps/shortcuts; usage access optional | Accepted |
| [0005](0005-compose-material3.md) | Compose + Material3 with a custom palette | Accepted |
| [0006](0006-room-in-milestone-1.md) | Room for the M1 library; prefs only for recents | Proposed |

## Adding an ADR

1. Copy the structure of an existing record.
2. Number it after the highest existing record.
3. Set status to `Proposed`, `Accepted`, `Superseded` or `Rejected`.
4. Link it from [ARCHITECTURE](../../ARCHITECTURE.md#11-design-decisions) and, if it changes
   behaviour, from [spec.md](../../spec.md).
5. Do not edit an accepted decision; write a new one that supersedes it.
