---
name: ux-review
description: Review a pull request or branch for UX regressions before merge — flow cost before and after (clicks, fields, screens), missing error, empty and loading states, accessibility of the changed UI, design-system drift, missing analytics or feature flag, and unclear UI copy — and report blockers with file:line fixes. Use when asked to review a PR or branch for UX, before merging user-facing changes, or as the final check on a feature.
---

# UX Review

Code review catches bugs in the code. Nobody checks whether the PR added a step to checkout, a dead end, or a sixth button style. This skill does: it compares the user-facing behavior before and after the change and reports regressions with concrete fixes.

## Phase 1: Scope the change

Get the diff: the pull request (`gh pr diff <number>` on GitHub, or your platform's equivalent) or the current branch against its base (`git diff <base>...HEAD`). List the user-facing changes: routes, components, copy, styles, form logic, API responses shown to users. If nothing is user-facing, say so in one line and stop.

For each change, identify the flows it affects, and read their feature folder (`docs/product/<feature-slug>/`) for the brief, budget and tracking plan when they exist.

## Phase 2: Review

If you can run subagents, run these passes in parallel, each with the diff and the affected files; otherwise run them one after another and finish all six before aggregating. Each pass reports every finding with file:line, severity and confidence. Filtering happens in Phase 3.

1. **Flow cost** — for each affected flow, count clicks, fields and screens on the base branch and on the PR, with the `happy-path-audit` definitions. If flow tests with UX budgets exist, run them or read their CI results. Any increase must be intentional and justified in the PR description.
2. **States** — for new or changed screens and actions: loading, empty, error, slow, permission, double submit, and input kept on error (the `unhappy-paths` matrix, limited to the diff).
3. **Accessibility** — changed components only: semantics, names, keyboard, focus, contrast of new colors, target size (the `accessibility-audit` passes, limited to the diff).
4. **Design-system conformity** — new hardcoded colors, spacing or font sizes; new local components that duplicate a design-system one; new variants of an existing pattern.
5. **Measurement and rollout** — a new feature or changed flow has its events (from `tracking-plan.md`, or at least the value moment) and, when risky, a feature flag that defaults to the current behavior. Removed code leaves no orphan events.
6. **Copy** — buttons say what happens ("Send invoice", not "Submit"); errors say what to do; confirmations name the action and its consequence ("Delete 3 invoices? This can't be undone." with a "Delete" button, not "OK"); each object keeps the same name everywhere; no internal jargon or placeholder text.

## Phase 3: Aggregate

Remove duplicates and group:

- **Blockers** — dead ends, data loss, accessibility failures that prevent completion, flow cost over budget without justification, a new feature whose value moment can't be measured.
- **Should fix** — weak error states, design-system drift, unclear copy, missing loading states.
- **Nits** — consistency and polish.

Each finding states the file:line, what the user experiences, and the concrete fix. Drop findings you verified as false positives; keep uncertain ones with their confidence.

## Phase 4: Report

```markdown
## UX review — <PR title>

**Verdict:** Ready / Ready once blockers are fixed / Needs rework

| Flow | Clicks | Fields | Screens |
|---|---|---|---|
| <flow> | 6 → 8 (+2) | 4 → 4 | 3 → 4 |

### Blockers
- `path/to/File.tsx:42` — <what the user experiences> → <fix>

### Should fix
- …

### Nits
- …
```

Show the report in the conversation. Post it on the PR, or apply fixes, only when the user asks; when fixing, start with blockers.
