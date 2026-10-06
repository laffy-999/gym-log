# Reliability and performance gates

Status: Acceptance gates plus local Android core verification.
The evidence below is specific to a debug build and an Android 16 emulator, not a production or physical-device performance claim.

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

The core now loads only active/detail sets, the latest comparable set per equipment, and a 30-workout history page.
An active-session save does not materialize the full archive.
The large-history smoke result below is not a frame-time or percentile benchmark.

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

## Verified local core: 2026-10-05

Environment: Android 16 / API 36 Google APIs x86_64 emulator, Pixel 5 profile, KVM, software graphics, and a debug APK built using JDK 21 and Gradle 8.13.
Normal UI inspection used 1080 × 2340 pixels.
The narrow-display walkthrough used 720 × 1280 pixels at 320 dpi (360dp wide), with text scale 1.5.

- The app and instrumentation APK build successfully.
- A fresh lint run reports zero errors and seven dependency-version advisories.
  The pinned versions were retained for this core slice rather than mixing in an unverified platform upgrade.
- Twenty-four on-device regression tests pass, covering v1, v2, and v4 migrations, load semantics, equipment separation, corrections, feedback persistence, history pagination and per-exercise grouping, workout deletion, catalog protection, kg/lb conversion, settings persistence, split templates/rotation/history names, day exercise lists, drop-set totals and cascade deletion, superset alternation/grouping/undo, progress sessions and heaviest set, plate selection, backup round trip and rejection of five malformed files without changes, rest timer behavior, input bounds, draft transitions, failed-load recovery, in-flight input, duplicate-trigger prevention, and equipment-convention conflicts.
- On the emulator, combining two split templates, editing a day, starting the suggested day, and the highlighted muscle grid were exercised in the UI.
- Rest alerts were observed in all three cases: in the foreground (vibration, no notification), in the background (notification), and after the app process was killed (notification posted by the alarm).
- The emulator's existing v4 data upgraded to v5 with history and weekly counts intact.
- In the UI: a day with planned exercises, one superset round (rest started only after the second exercise), two drop segments (2 sets, 31 reps, 1580 kg-reps), the progress view, the plate dialog (100 kg = 25 + 15 per side; 101 kg reported as not exact), the placed widget with a live timer and its update to the next day after Finish, and export to Downloads then import, which restored the database table by table to the exported state.
- The logger chips and weekly card were checked at 360dp width with 150% text.
- A real v1 database was upgraded while preserving its original set IDs, equipment links, loads, reps, and timestamps.
- The actual UI completed offline start, muscle/exercise selection, explicit equipment selection, named-equipment creation, repeated Save, rep adjustment, Undo, process-death recovery, Finish, history correction/deletion, and optional feedback save/reopen.
- A 50 kg × 10 barbell set, 50 kg × 8 barbell set, and paired 20 kg × 10 dumbbell set displayed 3 sets, 28 reps, and 1300 kg-reps.
- Editing the first barbell load to 55 kg changed that total to 1350 kg-reps; deleting the dumbbell set left 2 sets, 18 reps, and 950 kg-reps.
- A paired 22 kg × 9 set displayed 396 kg-reps on the narrow screen with enlarged text.
- Save stayed reachable above the numeric keyboard and retained focus after acknowledgement.
- The feedback form remained scrollable above the keyboard; the persisted feeling and note survived force-stop and history reopening.
- Making the emulator database temporarily inaccessible blocked the normal start flow; restoring it and pressing Retry recovered the existing history and dismissed the error.
- A disposable archive containing 2000 additional completed workouts and 100000 additional sets still allowed the actual app to resume, save, and finish with correct totals.
  One cold activity launch with that archive reported 1248 ms via `am start -W`; this is a single emulator observation, not a latency guarantee.
  The synthetic archive was removed afterward.
- The packaged app has no Internet permission; besides its generated internal receiver permission it requests only VIBRATE, POST_NOTIFICATIONS, and SCHEDULE_EXACT_ALARM, all for the rest timer.

Remaining limits: these features are not yet verified on the physical OnePlus, other Android versions, sustained frame-time and percentile measurements, real full-disk failure injection, OEM transfer behavior, release signing/security review, and automatic off-device recovery.
Unsaved logging drafts survive equipment switches within the process but are not claimed durable across process death.
Do not use this build as the only copy of important training history.
