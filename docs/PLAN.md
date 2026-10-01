# Gym Log project plan

## 1. Status and execution boundary

This plan captures the discussion and separates immediate work from future ideas.
An idea being recorded here is not authorization to implement it now.

| Item | Status |
| --- | --- |
| Working name | Gym Log; final name open |
| Repository | https://github.com/laffy-999/gym-log; public by user choice |
| License | Apache-2.0, selected by the user |
| Android source | Initial implementation exists locally; build and runtime verification pending |
| Product readiness | Not ready to replace an existing workout history |
| Current development mode | Bounded pieces, separate review, integration by one owner |
| Model execution | Kimi-routed documentation/research tasks completed; requested Opus review was not available and reviewer reported GPT-6 |
| Immediate priority | Comfortable, reliable logging of the current workout and current set |

The initial source was written before the user requested smaller delegated pieces.
Further work must follow the slices and verification gates below rather than expanding the whole application at once.

Before the next core slice is accepted, reproduce and address the static review findings:

- Equipment drafts can survive across workouts and override more recent saved values.
- Adding equipment or an exercise bypasses outgoing-draft preservation.
- An initial database-load failure lacks a reload path and can show an apparently empty home screen.

The next UI slice also needs explicit barbell/dumbbell choices and validation of the Save action with the keyboard open.
The requested immediate finish totals and optional feedback remain unimplemented.
These are recorded review findings and missing scope, not runtime-confirmed test results.

## 2. Product principles

1. Log the current set with the least possible interaction.
2. Never require a network, account, community catalog, or AI service to save a set.
3. Remember context instead of asking for it again.
4. Preserve recorded facts and keep comparable equipment histories separate.
5. Let the user decide how many sets and reps to perform at the gym.
6. Keep corrections easy without putting confirmation dialogs after every ordinary save.
7. Add optional features independently and remove features that do not help.
8. Treat attractive analysis as a later complement to the logger, not a reason to delay or clutter it.
9. Prioritize personal usefulness before broad adoption or monetization.
10. Never turn logged activity into unsupported claims about muscle growth, recovery, or health.

## 3. User and real-world context

The initial user does bodybuilding rather than training around estimated maximum strength.
The current phone is a OnePlus 11 5G running LineageOS on Android 16.
The product should support a broad range of Android devices rather than optimize around this one phone's lock screen.
Lock-screen and floating controls are explicitly deferred.

Flexify is useful, free, account-free, and supports exporting data.
The specific frustration is repeated tapping, searching, typing, and navigation while training.
The desired improvement is not another generic exercise library.
It is a shorter path from completing a set to recording it accurately.

### Example session supplied by the user

- Flat bench press on a Panatta machine.
- Barbell incline press.
- Decline bench press.
- Pec deck on a machine whose brand was transcribed as "Jim Laco" and remains unconfirmed.
- Standing multi-press pec deck, with the exact exercise name still to confirm.
- Cable crossovers.
- Dips on a Technogym machine.

This is a logging-flow example, not a prescribed routine or endorsement.
Do not infer sets, repetitions, or loads.
Do not turn uncertain transcriptions into canonical manufacturer data.

## 4. Core behavior

### Start and resume

The user starts an ad hoc workout without creating a routine.
Start records a durable start time and begins the displayed elapsed duration.
Only one workout is active at a time.
The active workout and saved sets must survive app closure and process death.
Returning to the app should return to the active exercise, not the top of a catalog.

Finishing records a durable end time.
The initial duration means wall-clock time from Start to Finish, including rest and exercise changes.
Pausing or excluding breaks is an unresolved later option, not an implicit timer behavior.
The app must not need a continuously running background service merely to calculate elapsed duration.

### Exercise selection

For an ad hoc workout, the path is muscle group, exercise, and equipment if needed.
Show relevant categories such as Chest, Back, Shoulders, Legs, Arms, and Core.
Prefer recently used exercises and previously selected equipment.
A full text search may become an alternative, but must not be required for common logging.
Allow a missing exercise or machine to be added while training and reused afterward.
The first-use setup path can have more steps than repeat use, but it should not recur every set.

A future personal gym inventory can narrow these choices further.
It is not necessary to build or populate the world's machine catalog before the logger works.

### Set entry

Keep exercise, equipment, actual load, actual reps, and Save visible in one logging context.
Store actual completed sets, never planned sets as if completed.
Prefill from comparable equipment history when available, but keep all values editable.
For an unchanged set, the target is one Save tap once the logger is visible.
For a changed rep count, expose large decrement/increment controls and direct numeric entry.
Avoid automatically advancing away from an exercise unless the user chooses that behavior later.

Use immediate local persistence before presenting a saved state.
Prevent unintended duplicate saves while the original save is in progress.
Provide Undo for the latest save and explicit edit/delete actions for earlier records.
Distinguish missing input from a legitimate zero added load for an unweighted bodyweight set.
Weight units and assisted-machine semantics need an explicit design before expanding beyond the initial kg implementation.

### Equipment-specific history

An exercise describes a movement.
Equipment describes the specific setup on which it was performed.
A logged set links to both through a stable equipment identity.

Different makes, models, or relevant configurations must not silently share a progression series.
Reusing one exercise across machines must not require duplicating its conceptual identity.
A later gym-specific machine instance may distinguish identical models at different locations.
The current text name is not a substitute for a future curated manufacturer/model catalog.

For free-weight bench press, provide explicit barbell and dumbbell options rather than requiring custom text names.
These are equipment variants of the same exercise and keep separate comparable histories.
Remember the choice between sets and suggest the last used option when returning to that exercise.
The user should not need to walk through a free-weight category on every set.

Recommended load convention: barbell load means the total bar-plus-plates load; dumbbell load is explicitly labeled per dumbbell.
For paired dumbbell movements, record the number of implements and the entry convention rather than assuming that all exercises use two.
An example of two 20 kg dumbbells for 10 simultaneous reps contributes 400 kg-reps when computing combined recorded load volume.
Do not double the load again if the user chose a combined-load entry convention.
Alternating and unilateral repetition conventions remain a separate design question before their totals can be compared.
These conventions are recommendations to make explicit in the UI, not user-confirmed defaults.

### Finish and immediate summary

Finish must open a short overview immediately, not merely return to a list of sessions.
The overview should show:

- Duration from Start to Finish.
- Number of completed sets.
- Total completed reps.
- Exercises and equipment used, with access to the recorded sets.
- Recorded load volume, when it can be explained accurately.
- Optional personal feeling and a short workout note.

For simple externally loaded sets, recorded load volume is the sum of the effective entered load multiplied by completed reps, applying the recorded per-implement or combined-load convention exactly once.
For example, 50 kg for 10 reps and 50 kg for 8 reps contribute 900 kg-reps of recorded load volume.
Label it as recorded load volume rather than literal mechanical work or proof of an effective workout.
Machine ratios, assisted exercises, bodyweight, range of motion, and different equipment make cross-exercise comparisons imperfect.
Do not estimate unrecorded bodyweight or treat assistance as positive lifted load.
The initial supported semantics and exclusions must be visible before presenting this metric.

### Feeling and notes

Offer a small optional feeling control, such as good, okay, or difficult, and a short free-text note.
The exact wording remains a UI choice to validate.
Allow comments such as "felt strong," "poor sleep," or "knee discomfort."
Do not require notes to finish a workout.
Allow the user to add or correct them later.
Keep feelings separate from numeric metrics.
The app must not diagnose pain, grade the workout as objectively good or bad, or infer a recovery state from the note.

## 5. The core acceptance scenarios

These are behavioral checks, not promises that the current source already passes them.

1. Start a workout without an account, internet access, or routine.
2. Select Chest and flat bench press without typing an exercise search.
3. Add Panatta equipment once, enter actual weight and reps, and save.
4. Save another set without reselecting the exercise or machine.
5. Adjust only reps and save without leaving the logging screen.
6. Switch to another exercise and return without losing saved sets or equipment identity.
7. Record a different machine for the same exercise and verify its previous load is not borrowed from the first machine.
8. Correct a set, undo an accidental save, and verify totals reflect the resulting records.
9. Close and reopen the process and recover the active session and all committed sets.
10. Finish and see duration, sets, reps, and clearly defined recorded load volume immediately.
11. Skip the feeling and note with no extra required workflow.
12. Add a feeling and note, reopen history, and see them unchanged.
13. Complete the flow with the keyboard open, on a smaller display, and with enlarged text without hiding the save action or clipping essential information.
14. Disable every optional future feature and still complete the same flow.

Measure the actual taps and navigation steps for the common path.
A target is not a verified result until observed on a running app.
Do not advertise a logging-time or latency number without measurement.

## 6. Bounded delivery slices

### Slice A: Establish the core data and session contract

Own session start/end, exercise identity, equipment identity, and actual recorded sets.
Ensure reliable local persistence and clear error behavior.
No sync server, community entities, AI, or muscle simulation belongs in this slice.

Acceptance: save, correct, reopen, and finish a real session with comparable equipment histories kept separate.

### Slice B: Make repeated logging comfortable

Own the muscle-first picker, equipment selection, previous-value handling, direct numeric entry, quick rep adjustments, and repeat Save flow.
Review the actual UI with keyboard and larger text.
Do not add routine planning or a catalog marketplace here.

Acceptance: run the user's ad hoc chest session without repeatedly searching or recreating equipment.

### Slice C: Finish and review the workout

Own the immediate completion overview, sets/reps totals, defined load-volume calculation, optional feeling, and notes.
Persist user-entered feedback locally and support later corrections.

Acceptance: finish and reopen a workout with consistent totals and unchanged optional feedback.

### Slice D: Add an optional quiet rest reminder

This is a separate proposed module after the core logging flow is comfortable.
Provide a configurable interval, with one minute as an example rather than a mandatory prescription.
Use an unobtrusive countdown and a brief optional sound or vibration when it expires.
Allow mute, cancel, restart, and disable without blocking logging.
Whether saving a set starts it automatically must be user-controlled.

Specify background and screen-off behavior before choosing Android scheduling APIs.
Do not request broad special permissions or promise exact background delivery without verifying the target Android behavior.
Test that delayed or missed reminders cannot save sets, change recorded reps, or interrupt data entry.

Acceptance: a user who wants a reminder can get one, while a user who disables it sees no extra required interactions.

### Slice E: Local routine sequences and richer set structures

Add saved ordered exercise/equipment sequences without mandatory set or rep targets.
Add linked pairs for supersets and explicit drop-set segments in separately reviewable changes.
Do not force users to plan before starting an ad hoc session.
Choose set-count and summary semantics for supersets/drop sets before including them in totals.

Acceptance: the same logger handles ad hoc and ordered training, and optional set structures preserve accurate history.

### Later phases, not current work

1. Portable data export/import and dependable off-device backup, before encouraging the app as the only long-term training record.
2. Personal gym inventory and optional synchronization, with explicit conflict and deletion handling.
3. Explainable muscle and regional training analysis using licensed, sourced exercise mappings.
4. Optional floating and lock-screen controls validated across device families.
5. Desktop analysis/planning and scoped AI/MCP access.
6. Community equipment/exercise catalogs, shared and rated routines, and gym contributions.
7. Evaluate iOS and optional paid services only after the personal Android experience is useful.

Later phases are a dependency-aware proposal, not fixed dates or promises.
See [FUTURE-MODULES.md](FUTURE-MODULES.md) for the captured vision and unresolved choices.

## 7. Modular engineering without premature infrastructure

Prefer one simple Android application with clear feature boundaries at first.
"Module-wise" means optional capabilities can be added or removed without changing the essential logging flow.
It does not require a plugin system, microservices, or a separate Gradle module for every feature today.

The core owns recorded facts and local persistence.
Optional features consume those facts through narrow interfaces and cannot become prerequisites for saving them.
Database changes need explicit migrations and must preserve existing records.
Disabling or removing a feature must not delete historical data merely because its UI disappears.
Community catalog updates must not rewrite the exercise identity or equipment history of old sets silently.

### Current technical choices, not user mandates

- Native Kotlin and Jetpack Compose for Android UI.
- Local SQLite storage and asynchronous database access.
- Current source targets API 36 and declares API 26 as the minimum.
- Current source stores entered kg values as integer grams rather than binary floating-point load values.
- No network permission or account service in the initial source.

These choices are provisional implementation decisions to validate, not a commitment to an iOS architecture.
No cloud provider or sync protocol has been selected.

## 8. Subagent work and integration protocol

Keep work in small waves with explicit file ownership and no overlapping writers.
A typical wave is one implementation slice plus an independent documentation or review task.
Do not split tightly coupled UI/data changes across agents until the shared contract is written.

Each assignment states:

- One outcome and explicit non-goals.
- Files the agent may edit.
- Inputs and outputs shared with other slices.
- User-visible acceptance checks.
- Evidence to return and unresolved risks.

The integration owner owns the top-level plan and the final product decision.
Subagents do not run competing builds, lint, tests, or formatters during a wave.
Run the build and focused runtime smoke once after integrating the wave, then fix observed failures and rerun only the affected verification.
A review is not a substitute for running the changed behavior.

The requested model pair is Kimi K3 and Opus 5.0.
Model availability and actual resolved identities must be reported honestly.
Session-only routing must not be saved as a global default without the user's approval.
The user selected session-only routing.

Execution note: the Kimi documentation task completed, but the reviewer reported GPT-6 rather than Opus despite the requested override.
Opus did not appear in the available provider catalog after refresh.
Do not describe that work as an Opus review.
Resolve the requested provider access before claiming a two-model Kimi/Opus review workflow.

## 9. Verification and distribution

For the current Android work, produce a debug build and exercise the app on an Android emulator or device.
Verify the complete record-edit-restart-finish path, not compilation alone.
Inspect the visual surface and verify that screens remain usable with keyboard and accessibility text scaling.
Before broad compatibility claims, test a representative older Android version and stock-like and vendor-modified devices.
The founder's Android 16 phone is a useful device check, not the entire support matrix.

Do not publish screenshots as proof of behavior that was not exercised.
Do not label the app production-ready while export/restore and data durability remain unverified.
A public source repository is not the same as a signed app release or Play Store deployment.
The first source publication must clearly state which slices are implemented, reviewed, and verified.

The user approved pushing documents only for the first publication.
Keep the unverified Android source local until the next bounded implementation and runtime-verification slice.
No app build or production deployment is part of this documentation publication.

Additional design records:

- [Architecture and extension boundaries](ARCHITECTURE.md)
- [Reliability and performance gates](QUALITY.md)
- [Security and privacy](../SECURITY.md)
- [Android-to-iOS research](PLATFORMS.md)
- [Monetization research](MONETIZATION.md)

## 10. Open questions to resolve only when relevant

- Final product name and application identifier before public binary distribution.
- Minimum supported Android version after compatibility testing.
- Weight-unit preference, per-hand versus combined load, and assisted-machine recording semantics.
- Exact workout feeling labels and note presentation.
- Whether local routine import from Flexify is desired and what format the user can export.
- Backup destination and restore workflow before relying on this as the only record.
- Exercise/muscle mapping sources, licensing, regional granularity, and how uncertainty is shown.
- Body-map interaction and visual style, including whether rotatable 3D is essential.
- Community moderation, catalog licenses, source attribution, and routine rating criteria.
- The source and permission status of named bodybuilder routines.
- Sync conflict behavior and account options when optional sync is introduced.

Do not ask all of these upfront or let them block the core logger.
