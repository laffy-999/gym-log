# Gym Log

**Log the current set with the least possible interaction.**

An Android-first, local-first workout logger for bodybuilding.
Gym Log is a working name, not a final brand.

## Why this exists

Workout tracking should not interrupt training.
Repeatedly opening an exercise database, searching, selecting equipment, and re-entering numbers makes logging feel like work.
This project starts with a personal need: a comfortable logger that remembers the current exercise and lets the lifter record the next set immediately.

The goal is not to replace a strength-record dashboard with another crowded dashboard.
The goal is to spend less time handling the phone and still keep trustworthy training history.

## The core experience

1. Start a workout and its duration timer.
2. Pick a muscle group and exercise without typing through an entire catalog.
3. Select equipment once when it matters.
4. Adjust actual weight and reps, then save the set.
5. Repeat without reselecting the exercise or equipment.
6. Finish and see a short summary, with an optional feeling and note.

No mandatory account, internet connection, routine, or prescribed set count should stand between a lifter and a saved set.
Previously recorded values are suggestions, never claims about a set that has not been performed.

## Equipment matters

The same movement on two machines can have very different loads.
A Panatta chest press and a Matrix chest press must not share a misleading load progression.
Exercise identity and equipment identity are separate.
The app should remember the setup and history that match what the lifter is actually using.
Free-weight variants such as barbell and dumbbell bench press should be explicit, remembered equipment choices too.

## Grow without compromising the logger

Future possibilities include ordered routines, supersets and drop sets, optional rest reminders, muscle-region visualizations, durable backups, sync, desktop planning, and AI-assisted analysis.
A later community could contribute machine and exercise information and share, rate, and adapt routines.
A visually rich body map could make training patterns easier to understand without pretending that a workout log measures exact muscle growth or recovery.
A later iOS app should let more people join the community while keeping the same logging-first principles.

These are ideas, not permission to build everything at once.
Add a capability independently, try it in real training, and keep it only if it helps.
The core logger must still work when every optional capability is disabled.

## Open source, personal usefulness first

The project is licensed under [Apache-2.0](LICENSE).
An optional paid service may eventually fund hosting or other conveniences.
Monetization is secondary to making the app genuinely useful, and must not hold basic logging or access to personal data hostage.

## Project documents

- [Detailed plan and current status](docs/PLAN.md)
- [Future modules and captured ideas](docs/FUTURE-MODULES.md)
- [Architecture and future extension boundaries](docs/ARCHITECTURE.md)
- [Reliability and performance gates](docs/QUALITY.md)
- [Security and privacy](SECURITY.md)
- [Android-to-iOS research](docs/PLATFORMS.md)
- [Monetization research](docs/MONETIZATION.md)
- [Decisions](docs/adr/0001-logging-first-scope.md)

## Development status

The local Android core now builds and runs on an Android 16 emulator.
It supports offline sessions, muscle-first exercise selection, equipment-specific loads, repeated saves, Undo, corrections, immediate completion totals, and optional editable feedback.
History shows the muscle groups and per-exercise set counts of each workout, and a completed workout can be deleted after confirmation.
Settings offer kg or lb entry, a load step for the quick load buttons, dark (default), light, or system theme, favorite exercises, and renaming, hiding, or deleting unused exercises and equipment.
A training split can be built in Settings from templates (full body, push/pull/legs, upper/lower, 4-day, 5-day) and single days, combined and edited freely; Start suggests the next day and highlights its muscle groups, while any day or a free workout stays one tap away.
An optional rest timer starts after each saved set (default 1:30), can be extended or stopped, and alerts with sound and vibration; while the app is closed it uses a notification, on time only if Android's "Alarms & reminders" permission is granted.
Split days can list their exercises; during that day they appear as one-tap chips with the sets done so far.
Supersets alternate between two exercises automatically and start the rest after each round; drop sets record extra segments under one set.
Each equipment option has a progress view with recent sessions and its heaviest set, the home screen shows sets per muscle group for this and last week, a plate calculator shows per-side plates for barbell loads, and a home-screen widget shows the running workout or the next split day.
Settings → Backup exports everything to one JSON file and imports it again, replacing all data only if the whole file is valid.
Barbell loads are the total bar-plus-plates load; the paired dumbbell option uses the load per dumbbell and simultaneous reps.
Assistance and unspecified loads do not contribute to recorded load volume.
See [verification evidence and remaining limits](docs/QUALITY.md#verified-local-core-2026-10-05).

The public repository contains the documents and, from this round on, the Android source.
This is a debug build for evaluation, not a signed production release.
Keep an exported backup somewhere other than the phone; there is no automatic off-device backup, sync, or iOS app.

## Build and try the local Android app

Use JDK 17 or 21 with `JAVA_HOME` configured, Android SDK platform 36, and build-tools 35.0.0.
Set the SDK location in the untracked `local.properties` file or through `ANDROID_HOME`.
The project includes a Gradle 8.13 wrapper.

```fish
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The minimum configured Android version is 8.0 (API 26); runtime verification so far covers Android 16 (API 36), not every supported OS version.
Run device tests only on a disposable emulator, not a phone holding real workout history.
Gradle's connected-test lifecycle can uninstall the app and its data.

```fish
./gradlew :app:lintDebug :app:connectedDebugAndroidTest
```
