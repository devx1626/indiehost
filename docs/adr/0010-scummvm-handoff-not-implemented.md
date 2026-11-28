# ADR-0010: ScummVM is detected; handoff needs the target id

- **Status:** Accepted
- **Date:** 2026-11-27
- **Deciders:** project maintainer
- **Related:** [ROADMAP](../../ROADMAP.md) M3, [spec.md](../../spec.md) FR-24

## Context

ScummVM's Android app added support in May 2024 for starting a specific game through an
intent: `Intent.ACTION_MAIN` with data `Uri.fromParts("scummvm", target, null)`, where
`target` is the game's configured ScummVM id. That id only exists after the user adds the
game inside ScummVM (its own file browser and SAF grant). There is no documented intent
that takes a raw game folder path.

## Decision

- Keep ScummVM detection and show a reason in the library: "Open it in ScummVM after adding
  the folder there." Launch stays disabled.
- Do **not** add a `RuntimeAdapter` in M3. Guessing a target id from a folder name would
  launch the wrong game or nothing at all.
- Do **not** bundle, fork or repackage ScummVM.

## Consequences

**Positive**

- No wrong-game launches and no unverified package visibility assumption.
- Clear separation: ScummVM owns its game list and saves, IndieHost owns the library.

**Negative**

- A two-step user flow remains (add in ScummVM, then optionally hand off later).

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Derive the ScummVM target from the folder name | No stable mapping; ambiguous ids are common |
| Use a `content://` folder intent | No documented support; upstream intent takes a configured target |
| Embed a native ScummVM port | Large, unresolved runtime/licensing scope |

## Open questions

- Is `ScummVMActivity` exported in current Play Store builds after the 2025 "safer Intents"
  change?
- Which `<queries>` entry is needed for the intent to resolve on Android 11+?
- Can the target id be read back from ScummVM's configuration without touching game data?
