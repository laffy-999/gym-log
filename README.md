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

This first publication contains documents only.
Experimental Android source exists locally but is not included in this publication and has not yet passed a build or device smoke check.
No usable app release is claimed here.
