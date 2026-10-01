# 0001: Logging first, optional features later

Status: Accepted from the user's stated priorities.

## Context

The user wants a personal bodybuilding logger that is more comfortable than repeatedly adding, searching, and editing exercises in Flexify.
The discussion also includes a broad future vision for analysis, community content, sync, and AI.
The user explicitly asked that these ideas be recorded without building everything at once.

## Decision

Prioritize the current workout and current set.
Start with ad hoc exercise selection, equipment-specific records, fast repeated set entry, reliable local storage, workout duration, and a short finish overview with optional personal feedback.
Do not require accounts, routines, networking, AI, or a community catalog to log training.

Treat rest reminders and all later capabilities as independently evaluated additions.
Saved routines specify exercise order without requiring predetermined set or rep counts.
Keep or remove an optional feature based on its usefulness without compromising the core or erasing historical records.

Use bounded implementation slices, explicit file ownership, separate review, and integration checks rather than assigning the whole application to every agent.

## Consequences

The full vision remains documented, but it is not an immediate implementation backlog.
Lock-screen and floating controls, regional muscle visualization, community catalogs, sync, desktop planning, AI/MCP, and iOS do not block the initial logger.
Optional features must not add mandatory interactions to ordinary set recording.
A working feature is accepted only after its actual user path is exercised.
