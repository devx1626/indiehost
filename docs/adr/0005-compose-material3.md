# ADR-0005: Compose + Material3 with a custom palette

- **Status:** Accepted
- **Date:** 2026-11-25
- **Deciders:** project maintainer
- **Related:** [ARCHITECTURE](../../ARCHITECTURE.md) §2, [spec.md](../../spec.md) §7.6

## Context

The launcher is one screen plus a drawer, built from system data. It must feel like a game
client, start fast on a weak phone, and be easy to iterate on without XML layouts and view
binding. The existing code is already Compose.

## Decision

- Jetpack Compose with the Compose BOM `2024.09.02`.
- Material3 components (`Button`, `OutlinedButton`, `OutlinedTextField`,
  `CircularProgressIndicator`, `MaterialTheme`).
- A custom dark-only palette and typography in `SteamTheme.kt`; no dynamic colour, no light
  theme.
- No XML layouts; `themes.xml` only sets the window background and system bar colours to
  avoid a white flash before Compose draws.

## Consequences

**Positive**

- UI is declarative and colocated with the state it renders.
- A single palette object keeps the game-client look consistent.
- Dark-only avoids a second design pass and fights neither icons nor game art.
- `minSdk 24` is supported by the chosen Compose versions.

**Negative**

- Compose adds APK size and build time compared with a handful of XML layouts.
- Recomposition mistakes can cost frames on the A22; state should stay small and stable.
- The custom palette is not a full design system; new components must be styled by hand.
- No light theme means the app ignores the system theme by design.

**Neutral**

- Material3 is used for behaviour and accessibility semantics, not for its default look.
- The palette is inspired by a game client but contains no third-party artwork; the
  `Steam*` names are internal codenames and must be renamed before release (CON-07).

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| XML Views + ViewBinding | More boilerplate for a small, state-driven screen; no benefit here |
| Material Components (View) | Same as above |
| Compose with default Material colours | Would not deliver the game-client look the product needs |
| A full custom design system | Overkill for one screen; revisit if the UI grows |
