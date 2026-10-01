# Architecture and extension boundaries

Status: Design direction for incremental implementation, not a description of completed infrastructure.

## Objective

Keep the current workout logger simple, fast, reliable, and usable without any optional service.
Preserve a practical path to iOS, synchronization, analysis, and community participation without implementing those systems now.
The application should have replaceable parts, not a speculative plugin framework.

## Target dependency direction

```text
Android UI / later iOS UI
          |
          v
Workout operations and portable domain types
          |
          v
Repository contract
          |
          v
Local storage adapter

Optional sync, analysis, reminders, and AI adapters
          |
          v
Narrow workout queries or explicit commands
```

The user interface should not perform SQL or network requests.
Domain operations should not depend on Android activities, Compose, SwiftUI, HTTP clients, or AI providers.
The local store is the source of truth for the logging experience.
Optional clients must not become required for a local commit.

This is a target boundary, not a reason to create empty interfaces or a module per entity.
Extract a seam when the next real capability needs it.
Keep the first implementation small and concrete.

## Current source versus target

| Area | Current source | Required next work |
| --- | --- | --- |
| Android UI | Jetpack Compose in MainActivity.kt | Verify actual phone-sized interaction and keyboard handling |
| State | AndroidViewModel directly owns WorkoutStore | Correct draft/session and recovery defects before relying on it |
| Storage | SQLiteOpenHelper, integer local IDs, schema version 1 | Prove durability and establish migration policy before schema updates |
| History access | Materializes the full archive | Bound active-screen queries and paginate history before long-history performance claims |
| Portable logic | Data classes and conversions share a file with Android database code | Separate pure calculations/types when the first sharing boundary is introduced |
| Sync | Absent | Add explicit identity, revision, conflict, and authorization contracts before transport |
| AI/MCP | Absent | Expose selected read queries first, never the internal database directly |
| iOS | Absent | Research and validate shared logic/data versus shared UI before extraction |

An AndroidViewModel, SQLiteOpenHelper, Android Context, and platform notification APIs cannot simply be moved into Kotlin commonMain.
See [PLATFORMS.md](PLATFORMS.md) for platform research and migration options.

## Core data ownership

### Recorded facts

A workout owns start/end timestamps and optional personal feedback.
A set owns the actual recorded weight, units/semantics, reps, timestamp, and relationship to an exercise/equipment configuration.
An exercise identity describes the movement and is distinct from a manufacturer's machine.
An equipment identity identifies the relevant setup, with room for a later gym-specific instance.
A routine is a plan or sequence, not evidence that a set happened.

Free-weight barbell and dumbbell variants are explicit equipment choices, not separate unrelated exercise identities.
Represent load units and per-implement versus combined-load convention explicitly before calculating total volume.
The number of implements and unilateral or alternating rep convention must not be inferred merely from an exercise name.
Remember the chosen equipment and load-entry convention without adding a per-set configuration step.

Preserve order and the original equipment relationship.
Renaming or merging a community catalog entry must not silently alter past performance records.
Later muscle mappings and analytics are interpretations of recorded facts, not replacements for those facts.
Store interpretation version or provenance when needed for reproducible analysis.

### Current IDs and future portable identity

Current local integer primary keys are an implementation detail, not globally unique public identities.
Before the first cross-device export/merge or sync protocol, add stable randomly generated identifiers for independently created records.
Keep domain identity separate from account ownership and community catalog identity.
Use a versioned format rather than exporting raw internal table assumptions as an API contract.

Define schema migrations that preserve records, references, order, and units.
Do not ship a destructive reset or silently discard unknown data when an update is incompatible.
Use explicit unsupported-version errors and a recovery path.
A migration test should begin with a realistic old database and prove the same user-visible history after upgrading.

### Mutations and failure semantics

A successful Save means the local transaction committed, not merely that a background task was queued.
The UI must preserve entered values when a save fails and explain how to retry.
Repeated triggers while a write is in progress must not create unintended duplicate records.
UI drafts belong to the active workout and equipment, not an unbounded cross-workout cache.
Persist committed facts and retain recoverable active-session context.
Do not claim unsaved keystrokes are durable unless draft persistence is actually implemented.

## Future sync boundary

Use a sync adapter over a deliberate change/export contract, not remote writes into UI state.
Specify idempotent operations, record revisions, deletion markers or equivalent retention, conflict rules, and server authorization before enabling it.
Wall-clock timestamps alone are not a sufficient conflict-resolution strategy across devices.
The transport should batch work in the background and avoid blocking Save.

The first multi-device workflow must cover:

- Two devices create sessions while offline.
- One set is edited on one device and deleted on another.
- An upload succeeds but its acknowledgement is lost.
- The user's account or access token is revoked.
- A server becomes unavailable or restores an older backup.
- A catalog entry changes without rewriting private history.

Do not silently pick the newest received record and call that safe conflict handling.

## Future AI and community boundaries

Expose a narrow history-query interface with limits and explicit scopes.
Separate read access, proposed routine edits, accepted edits, and historical deletion privileges.
Keep personally entered notes private unless selected for a specific analysis.
Community content is untrusted data and belongs in a separate import/catalog boundary.
Downloading a routine creates a personal editable copy, not a live remote control over the active workout.

Public content can use a different storage and authorization model from private training history.
A shared community must not require public workouts or expose a member's gym attendance by default.
Read [SECURITY.md](../SECURITY.md) before adding any transport or external-data integration.

## Feature retention and removal

An optional module can be disabled without migrating users away from the core logger.
Removing its UI does not authorize deletion of its recorded data.
Keep exportable history and either retain the data schema or migrate it explicitly.
No module may register a mandatory step between adjusting reps and committing a set.

This applies to rest timers, saved sequences, muscle visualization, syncing, AI, community features, and paid-service prompts.
If a module stops providing useful value, remove or simplify that capability rather than preserve it only because it was implemented.
