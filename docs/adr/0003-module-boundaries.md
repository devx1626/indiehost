# ADR-0003: Four modules with a pure-Kotlin detector

- **Status:** Accepted
- **Date:** 2026-11-24
- **Deciders:** project maintainer
- **Related:** [ARCHITECTURE](../../ARCHITECTURE.md) §2, [spec.md](../../spec.md) §8

## Context

The product needs to fingerprint several engines, host a UI, and grow one runtime adapter
per engine. Engine detection is pure file parsing and is the part most likely to accumulate
subtle bugs (WAD versions, chunk walking, YYC detection). It should be testable without an
emulator or a phone.

## Decision

Split the code into four Gradle modules:

```
:app       Compose UI, activities, system data sources
:runtimes  RuntimeAdapter + per-engine implementations (Android)
:library   Game model and GameRepository seam
:detect    Pure Kotlin/JVM: Engine, EngineDetector, GameMakerWad
```

Dependencies flow one way: `:app` → `:runtimes` → `:library` → `:detect`. `:detect` has no
Android imports and is tested with JUnit on the host.

## Consequences

**Positive**

- The detector's 12 tests run in seconds on any JDK, with no SDK or device.
- Android APIs are confined to `:app` and `:runtimes`.
- Adding an engine has an obvious home: enum + detector branch + adapter.
- The dependency direction prevents UI concerns from leaking into parsing.

**Negative**

- More Gradle modules than a small app strictly needs.
- `:library` is currently a thin placeholder; `GameRepository` and several adapter methods
  are unused, which can mislead a reader.
- Cross-module changes (e.g. adding an engine) touch several build files.
- The promised adapter registry does not exist yet; the second adapter must introduce it.

**Neutral**

- `:detect` could later be published as a standalone library, but that is not a goal.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Single `:app` module | Detector tests would need Robolectric or a device; parsing and UI would blur |
| Feature modules (`:home`, `:drawer`, `:library`) | Premature for one screen and four small files |
| Put the detector in a separate repository | Versioning overhead with no consumer |
| Use a third-party engine detector | None covers GameMaker WAD/YYC plus the other signatures with our constraints |
