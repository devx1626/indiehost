# ADR-0006: Room for the M1 library; preferences only for recents

- **Status:** Accepted (2026-11-26)
- **Date:** 2026-11-25
- **Deciders:** project maintainer
- **Related:** [ROADMAP](../../ROADMAP.md) M1, [ARCHITECTURE](../../ARCHITECTURE.md) §7, [spec.md](../../spec.md) FR-17

## Context

Today the only "library" is Butterscotch's pinned shortcuts, and `GameRepository` is an
unused in-memory map. M1 needs a persistent collection: scanned games, titles, engines, WAD
versions, folder paths, cover art and timestamps. Recents are a small ordered list of
package names and already work in `SharedPreferences`.

## Decision

- Use **Room** for the game library in `:library`.
  - `GameEntity` with `id`, `title`, `folderPath`, `engine`, `wadVersion`, `coverPath`,
    `addedAt`.
  - DAO + `RoomDatabase`; `RoomGameRepository : GameRepository`.
  - Schema version 1; destructive migration allowed during M1 only. A real migration
    policy starts at the first public release.
- Keep **`SharedPreferences`** for the recents list (`indiehost.recents`/`launches`). It is
  a bounded, order-sensitive list; a database would be overkill.
- Keep **saves** out of Room. For IndieHost-hosted engines, saves go to a shared,
  documented directory; Butterscotch-owned saves stay with Butterscotch.

## Consequences

**Positive**

- Queries, ordering and future filtering (engine, title, recently played) are straightforward.
- Schema changes are explicit and testable.
- The repository seam already exists, so the UI change is contained.
- Recents stay simple and fast.

**Negative**

- Room adds KSP/kapt, a compiler plugin and build time.
- Migrations become a maintenance obligation after the first release.
- `:library` gains Android dependencies and needs instrumented or Robolectric tests.
- A database can be corrupted; a JSON file would be easier to inspect but worse to query.

**Neutral**

- `InMemoryGameRepository` can stay for tests or be deleted once unused.
- The database is app-private and excluded from backup by `allowBackup="false"`.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Keep `InMemoryGameRepository` | Loses the library on process death; M1 requires persistence |
| `DataStore` (Proto/Preferences) | Fine for settings, awkward for a growing list with queries and migrations |
| SQLDelight | Good, but Room is the Android default and the team already knows it |
| A JSON file | Simple, but hand-rolled migrations, concurrency and corruption handling |
| Store games in `SharedPreferences` | Wrong tool for a list of records with fields |

## Open questions

- Exact save directory (`Documents/IndieHost/<game>` vs app-specific external storage),
  decided in M2 with the first hosted engine.
- Whether cover art lives in the database (BLOB) or as a file with a path. This ADR assumes
  a file plus a path; revisit if backup/export needs a single artifact.
- Whether `wadVersion` is nullable for non-GameMaker engines (it is in the model).
