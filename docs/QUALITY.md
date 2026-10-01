# Reliability and performance gates

Status: Acceptance plan, not benchmark results.
No runtime performance or durability result has yet been measured for the initial source.

## The common path

Measure these separately:

1. Start a new ad hoc workout.
2. Resume an existing active workout.
3. Select a recently used exercise and equipment.
4. Save another set with unchanged values.
5. Adjust only reps and save.
6. Finish and render the session overview.

The primary usability target is one Save action for an unchanged set once its logger is visible.
The speed goal is immediate-feeling interaction with no blocking database, network, cryptographic, or large-history work on the UI thread.
Do not claim a sub-second or percentile target has been met without measurements on named hardware and a named build.
Set numerical budgets after the baseline measurement, not by inventing observed latency.

## Reliability scenarios

- Saving a set commits the entire record or nothing.
- A write error retains the user's entry and does not announce success.
- An initial load error offers Retry and never presents an empty archive as if it loaded successfully.
- Repeated Save gestures during an in-progress write do not create unintended duplicates.
- Switching exercises or adding new equipment preserves the outgoing input draft within the session.
- A new workout recalls persisted comparable-equipment values, not stale drafts from a previous workout.
- Editing or deleting a set updates the summary consistently.
- Force-closing and reopening the app restores committed sets and the active workout.
- Rotation and background/foreground transitions do not restart duration or produce duplicate sessions.
- Finish is idempotent from the user's perspective and cannot accidentally create a second workout.
- Device time changes do not produce negative or obviously corrupt duration displays; define recovery behavior before claiming accurate time across clock adjustments and reboots.
- Migration from every distributed schema preserves records and references.
- Out-of-space and read/write failures have explicit, recoverable behavior.

## Performance scenarios

Use a clean install, a normal personal history, and a large representative multi-year history.
Record the dataset size and device details with results.
Measure cold start, warm resume, save acknowledgement, exercise selection, history opening, and finish-summary rendering.
Inspect frame timing while editing reps, opening pickers, and scrolling history.

Do not load, copy, or scan the complete training archive on every recomposition or current-set save.
Use bounded indexed queries for the active workout and last comparable set.
Paginate or lazily load history when it is opened.
Keep derived calculations local to the data that changed.
Do not hide slow persistence behind a false saved indicator.

Current source loads the full archive and performs repeated list scans, so long-history speed is an identified risk, not a solved property.

## Visual and input checks

Inspect the actual app, not only previews or screenshots produced from mock data.
Use a narrow phone display, an ordinary display, and larger accessibility text.
Open the numeric keyboard and verify essential controls remain reachable.
Exercise picker, new-equipment entry, and error dialogs must not become trapped behind the keyboard.
Check tap targets, contrast, text wrapping, navigation bars, and accidental double taps.

The main action should not move unexpectedly after saving a set or showing Undo.
Timer updates should not rebuild unrelated large lists.
A rest reminder must not steal focus from numeric entry or block the next save.

## Security verification

Check the release manifest, permissions, exported components, backup behavior, dependency advisories, logs, and signing setup.
Confirm no private workout data leaves the device in the offline-only build.
Do not infer release-build safety from a debug build or a static manifest alone.
Follow the threat boundaries and future-service gates in [SECURITY.md](../SECURITY.md).

## Build and runtime evidence

After integrating one bounded wave, run the build and relevant checks once.
Run the actual workout flow on an emulator or device and observe saved database state and UI results.
For a bug, reproduce the consumer-visible failure before changing it when practical, then confirm it no longer occurs.
Keep a permanent regression test only when it protects meaningful behavior rather than copying implementation details.
Record which Android version and hardware or emulator configuration were exercised.

A source review, successful compilation, and a passing unit test are useful but are not substitutes for a completed workout flow.
Do not publish a compatibility, security, or performance claim beyond the evidence gathered.
