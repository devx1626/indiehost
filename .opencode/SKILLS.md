# Project-local agent skills

This directory is the **project-local mirror** of the global OpenCode skill set in
`~/.config/opencode/skills/`. It is committed so a fresh clone, a CI agent or another
contributor gets the same development and design guidance without a manual install.

Installed **2026-11-25**. Forty-two skills, 366 files, about 4.1 MB. These are
development-time instructions only: none of this is packaged into the APK, and none of it
changes the app's permissions or runtime dependencies.

## Sources

| Source | Commit | Licence | Skills |
| --- | --- | --- | --- |
| [obra/superpowers](https://github.com/obra/superpowers) | `22f0b463e7a5e579904dd6211a520321035526f7` | MIT | 13 workflow skills |
| [Drjacky/claude-android-ninja](https://github.com/Drjacky/claude-android-ninja) | `5d40f862493a5e43daffa2cfaf5619a013b8f114` | Apache-2.0 | `android-ninja` + 40 references |
| [Trystan-SA/product-engineer-skills](https://github.com/Trystan-SA/product-engineer-skills) | `5e0534c97f29b82a5e2077000eb1cb8732f03296` | MIT | 15 UX/product skills |
| [anthropics/skills](https://github.com/anthropics/skills) | `e4bb5d960d87126ff3b3a8af1a104b7db67560ba` | Apache-2.0 | `frontend-design`, `skill-creator` |
| [bergside/awesome-design-skills](https://github.com/bergside/awesome-design-skills) | `f631a09b4fcc0166f2e2c1a8c81906ef680c57e8` | MIT | `material`, `design-styles` catalog |
| [mxyhi/ok-skills](https://github.com/mxyhi/ok-skills) | `3ae9eb922f9db7b66a5327529afd550d78d60fb0` | Apache-2.0 / MIT | 9 architecture and research skills |

Each skill keeps its upstream `LICENSE`/`LICENSE.txt`. The repository's
[LICENSING.md](../LICENSING.md) records the third-party notice.

## Precedence

For IndieHost the order is:

1. [`AGENTS.md`](../AGENTS.md), [`spec.md`](../spec.md) and the other repository docs.
2. The existing code's patterns and pinned versions.
3. A loaded skill.

Third-party skills are **advice, not policy**. Do not apply `android-ninja`'s newer stack
(Hilt, Room 3, AGP 9, Kotlin 2.x, `targetSdk 37`) to this repository without an explicit
request. IndieHost is Kotlin 1.9.24, AGP 8.x, `compileSdk`/`targetSdk` 34, Compose BOM
2024.09.02, and uses no DI framework.

The project's hard rules still apply: no game data, no Butterscotch fork/link/bundle, no
`INTERNET` permission or network call, no Wine/x86 translation/emulation, no YYC/GMRT
builds, and `:detect` stays pure Kotlin/JVM.

## What is installed

- **Workflow:** brainstorming, plans, execution, subagents, TDD, debugging, code review,
  worktrees, verification, writing skills.
- **Android/build:** `android-ninja` and its Kotlin/Compose/Gradle/Room/testing/
  performance/accessibility/security references.
- **Architecture:** `codebase-design`, `improve-codebase-architecture` (explicit-only),
  `diagnosing-bugs`, `domain-modeling`, `karpathy-guidelines`.
- **UX/product:** UX review, accessibility audit, happy-path/unhappy-path audits, flow
  tests, usability tests, interviews, design-system work, experiments.
- **Design:** `frontend-design`, `material`, `design-styles` (67 styles behind one
  catalog), `prototype`.

## Tooling gaps

| Skill | Needs | Status |
| --- | --- | --- |
| `get-api-docs` | `chub` | missing; use `find-docs`/`webfetch` |
| `flow-test` | Playwright or Cypress | missing; adapt to Android/instrumented tests |
| `instrument-feature` | analytics SDK | not allowed in IndieHost; adapt the method |
| `brainstorming` | `node` | present; starts a loopback-only server |
| `skill-creator` evals | Python 3 + optional model API | present; needs configuration |

## Syncing

The global copy is the source of truth for this workstation. To refresh the project mirror:

```bash
cp -a ~/.config/opencode/skills/. .opencode/skills/
git add .opencode/skills .opencode/SKILLS.md
```

Keep the directory name as the skill ID; OpenCode derives IDs from the path, not the
frontmatter `name`. Do not put loose `*.md` files at the root of `.opencode/skills/`:
OpenCode treats root-level Markdown as a skill. This manifest lives one level up for that
reason.

The `design-styles` skill is deliberately one skill with 67 reference directories. Do not
split it into 67 standalone skills; that would add 67 descriptions to every prompt.
