# Platform research: future iOS support

Research date: 2026-10-01.
This document records research only and authorizes no implementation.
Each claim is labeled [Fact] when it comes from a primary source linked below, or [Recommendation] when it is a project judgment.
Facts about pricing are region-dependent and were checked against official pages on the research date; they can change.

## 1. Short answer

Yes, an iOS version of Gym Log is possible later without discarding the Android codebase.
Kotlin Multiplatform core technology is officially stable for Android and iOS, and Google officially supports it for sharing business logic between Android and iOS.
The current source, however, is an Android-only Kotlin/JVM application and is not in any sense already multiplatform.
Sharing code later requires deliberate extraction of portable code; no automatic conversion exists and this document makes no estimate of how much code would be shared.

## 2. Current state: why the existing source is not multiplatform

These are observations of the source at the research date, not defects to fix now.

- [Fact] The app module applies only `com.android.application` and `org.jetbrains.kotlin.android` plugins, so it builds a Kotlin/JVM Android artifact, not Kotlin/Native.
- [Fact] `WorkoutStore.kt` imports `android.content.ContentValues`, `android.content.Context`, `android.database.Cursor`, `android.database.sqlite.SQLiteDatabase`, and `android.database.sqlite.SQLiteOpenHelper`, all of which are Android framework classes that cannot compile in a KMP `commonMain` source set.
- [Fact] `WorkoutStore.kt` also uses `java.math.BigDecimal`, a JVM class that does not exist on Kotlin/Native.
- [Fact] The dependency list includes `kotlinx-coroutines-android`, an Android-specific artifact; the multiplatform artifact is `kotlinx-coroutines-core`.
- [Fact] The current build uses Android activity integration and an Android-targeted dependency configuration, not an iOS target; some AndroidX libraries also have KMP variants, but their presence here does not make this build multiplatform.
- [Fact] SQLite itself is not the portability problem: SQLite is the built-in database on iOS as well as Android, so the on-disk SQLite format can survive a platform expansion; only the Android `SQLiteOpenHelper` wrapper cannot.

Conclusion: the schema, SQL statements, and domain concepts are potentially portable; the Kotlin file currently containing them is not.

## 3. Verified platform support

### Kotlin Multiplatform core

- [Fact] JetBrains lists Android and iOS as Stable platforms for the core Kotlin Multiplatform technology (page updated 10 September 2025, checked 2026-10-01).
- [Fact] Google states that Kotlin Multiplatform is officially supported by Google for sharing business logic between Android and iOS and is stable and production-ready (page updated 2026-09-23).
- [Fact] Google's Jetpack KMP support matrix lists Room, sqlite, DataStore, lifecycle, ViewModel, paging, and others at Tier 1 for Android, JVM, and iOS, meaning fully tested in CI with compatibility tracking.

### Compose Multiplatform shared UI

- [Fact] JetBrains lists Compose Multiplatform on iOS as Stable on the same platform stability page.
- [Fact] Google's matrix marks JetBrains-published Compose artifacts for iOS as built by JetBrains, not by Android; the Android team does not own the iOS Compose port.

### SQLDelight

- [Fact] SQLDelight 2.x generates type-safe Kotlin APIs from SQL and offers a Native driver for iOS, macOS, Linux, and Windows alongside the Android driver, plus documented migration support.

### Flutter

- [Fact] As of Flutter 3.47, Flutter supports deploying to Android API 24 to 37 and iOS 15 to 27, with CI-tested subsets.
- [Fact] Flutter application UI is written in Dart; this Compose UI cannot be reused as Flutter UI, although platform integrations can still call Kotlin code.

### Apple toolchain and costs

- [Fact] Building iOS apps requires Xcode, and every current Xcode version requires macOS; Apple's SDK and system requirements page lists supported macOS versions per Xcode release, with no non-Mac option.
- [Fact] A free Apple developer account can test apps directly on the developer's own devices using Xcode.
- [Fact] TestFlight beta distribution and App Store distribution require an Apple Developer Program membership listed at $99 USD per year on Apple's program page; the Enterprise Program at $299 per year is for internal employee distribution and is irrelevant here.
- [Fact] Pricing is shown in USD and regional pricing, taxes, or program fee waivers may differ; verify before spending.

### OS restrictions that shape features

- [Fact] iOS background execution is system-scheduled: the BackgroundTasks framework lets an app register handlers for tasks the system decides when to run; an app cannot simply keep a timer running in the background.
- [Fact] iOS local notifications are scheduled through `UNUserNotificationCenter`, require explicit user permission, and Apple documents that delivery is attempted but not guaranteed.
- [Fact] Android 13 (API 33) and higher require the `POST_NOTIFICATIONS` runtime permission for non-exempt notifications, and notifications are off by default for newly installed apps targeting Android 13 or higher.
- [Fact] Lock-screen surfaces differ fundamentally: Android offers notification-based controls with custom actions, while iOS lock-screen presence uses separate mechanisms (Live Activities and widgets) with their own lifecycle rules; neither is a port of the other.

## 4. Options compared

### Option A: Kotlin Multiplatform shared domain and data, native UI per platform

Android keeps Jetpack Compose; a future iOS app is written in SwiftUI and consumes the shared Kotlin module as a framework.
[Fact] JetBrains documents three integration methods for the shared module into an iOS app: direct integration through an Xcode build script, Swift Package Manager, and CocoaPods.
[Recommendation] This is the preferred path for Gym Log because the logging UI is the product's core differentiator, platform-native UI keeps each platform's interaction idioms, and the shared layer is exactly the durable, testable part: identities, set semantics, history rules, schema, and import/export.

### Option B: Compose Multiplatform shared UI

- [Fact] Compose Multiplatform on iOS is stable per JetBrains.
- [Recommendation] Sharing UI reduces duplicate presentation work but requires checking native integration, accessibility, keyboard behavior, and the logging interaction on both platforms.
- [Recommendation] Keep it as a real alternative, not a rejected option; compare the same small logging screen in shared Compose and SwiftUI before committing the future iOS UI.

### Option C: Flutter rewrite

[Fact] Flutter supports the needed Android and iOS version ranges per its supported platforms page.
- [Recommendation] For this project, adopting Flutter would mean replacing the Compose UI and usually moving domain code to Dart, while SQL schemas and data semantics could still be preserved.
- [Recommendation] There is no demonstrated requirement that justifies that rewrite now; reassess only if an actual platform constraint makes the Kotlin options unsuitable.

## 5. Recommendation: boring Android-first path that does not foreclose iOS

[Recommendation] Keep building the single Android app now; do not create a KMP shared module, a `commonMain` source set, or any iOS target today, because there is no second consumer to justify the abstraction.
[Recommendation] Instead, maintain four boundaries inside the existing codebase so later extraction has a smaller scope:

1. Keep domain types (`Exercise`, `Equipment`, `Workout`, `LoggedSet`) and pure logic free of `android.*` imports, so the future portable layer is identifiable by inspection.
2. Isolate `java.math.BigDecimal` behind the existing `weightText` and `parseWeight` functions, since the representation (integer grams) is portable even though the formatting helper is JVM-only.
3. Preserve the schema, units, relationships, and migration history independently of the Android wrapper; Room KMP and SQLDelight support SQLite but have different schema and code-generation conventions.
4. Define the store boundary in terms of domain operations rather than `SQLiteDatabase` handles, so a future SQLDelight or Room KMP implementation can sit behind the same boundary without changing callers.

[Recommendation] Do not extract these seams into separate Gradle modules yet; package-level discipline in one module is enough until an iOS target actually exists.

## 6. Database strategy, migrations, and stable identities

- [Recommendation] Continue with explicit, numbered SQLite migrations that preserve records, as the plan already requires; this discipline transfers unchanged to SQLDelight migrations or Room KMP migrations later.
- [Recommendation] Implement and verify each required migration before shipping a schema-changing update to an existing database; the current onUpgrade throws and provides no such migration.
- [Recommendation] Before any export, sync, or second platform ships, introduce stable identities that do not depend on SQLite rowids: rowids are local database internals and differ across devices, exports, and future catalog imports.
- [Recommendation] A natural design is an opaque unique key per exercise, equipment entry, workout, and set, generated at creation and never reused, with the rowid kept as a local implementation detail.
- [Recommendation] Cross-platform and community data sharing must be defined as a portable documented format (for example JSON with explicit fields and versioned semantics), never as a coupling to Android rowids, Android file paths, or Android-specific timestamps.
- [Fact] Equipment identity already matters to the product: different machines must not share progression series, so the equipment identity is exactly the kind of fact that must survive export and re-import unchanged.

## 7. Platform behavior differences to respect later

These affect deferred modules, not the current core.

- Rest reminders: choose Android alarm/notification behavior and permissions according to the required foreground/background delivery contract; iOS can schedule local notifications without continuously running a background timer, but system settings and delivery behavior must be tested.
- Lock-screen or floating logging: [Recommendation] treat the Android and iOS designs as separate features sharing only data-layer outcomes, because the OS surfaces and their constraints are not equivalent; the brief already requires device-dependent behavior to be tested rather than assumed identical.
- Duration tracking: [Recommendation] storing start and end timestamps and computing elapsed time works identically on both platforms and avoids any background-execution dependency; keep it that way.

## 8. Prerequisites and gates before implementing iOS

[Recommendation] Do not start an iOS target until all of the following hold:

1. The Android core acceptance scenarios in the plan pass on real devices, and at least one external user trains with the app.
2. Export and restore are implemented and verified, so users' history is not hostage to a platform transition.
3. Stable identities from section 6 exist in the schema with working migrations.
4. A Mac capable of running a current Xcode version is available, since [Fact] Xcode requires macOS.
5. At least one real iPhone is available for verification; the free Apple account suffices for on-device testing, and the $99 per year program is only needed when TestFlight or App Store distribution is wanted.
6. A decision between Option A (recommended) and Option B is recorded as an ADR.

[Recommendation] Developer-account budget: basic self-testing does not require a paid Apple membership; the standard Apple Developer Program is listed at $99 USD per year for distribution benefits.
This excludes the cost of Mac and iPhone hardware, hosted macOS build infrastructure, and any applicable taxes; verify regional pricing and account restrictions before enrolling.

## 9. Explicitly not claimed

- No claim that any percentage of the codebase will be shared automatically; sharing is bounded by how well the seams above are kept.
- No claim that Compose Multiplatform on iOS behaves identically to Jetpack Compose on Android; stability status is not behavioral equivalence.
- No claim about App Store review outcomes, regional store rules, or alternative iOS distribution in any jurisdiction; those must be re-checked when distribution is planned.
- No claim that lock-screen or background behavior is portable; section 7 lists the documented differences.

## 10. Sources

All links checked on 2026-10-01.

- JetBrains, Stability of supported platforms (KMP core and Compose Multiplatform stability levels): https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- Google, Kotlin Multiplatform on Android Developers (official support statement, Jetpack KMP library matrix, tier definitions): https://developer.android.com/kotlin/multiplatform
- JetBrains, iOS integration methods for a KMP shared module: https://kotlinlang.org/docs/multiplatform-ios-integration-overview.html
- JetBrains, Kotlin Multiplatform overview and use cases (share logic, keep native UI): https://kotlinlang.org/docs/multiplatform.html
- SQLDelight documentation (supported dialects and platforms including the Native driver): https://sqldelight.github.io/sqldelight/
- Flutter, Supported deployment platforms (as of Flutter 3.47): https://docs.flutter.dev/reference/supported-platforms
- Apple, Apple Developer Program (free account capabilities, $99 annual membership, Enterprise $299): https://developer.apple.com/programs/
- Apple, Xcode SDKs and system requirements (Xcode requires macOS): https://developer.apple.com/xcode/system-requirements/
- Apple, BackgroundTasks framework (system-scheduled background work): https://developer.apple.com/documentation/backgroundtasks
- Apple, UserNotifications framework (local notification scheduling, permission, delivery not guaranteed): https://developer.apple.com/documentation/usernotifications
- Google, Notification runtime permission (`POST_NOTIFICATIONS`, Android 13+): https://developer.android.com/develop/ui/views/notifications/notification-permission
