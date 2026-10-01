# Gym Log future modules record

This document is a preservation record of deferred ideas, not an implementation commitment or delivery plan.
Nothing listed here is built, scheduled, or approved for build, and the presence of an idea below grants no permission to implement it.
The parent project owns the top-level delivery sequencing in PLAN.md, so this record deliberately avoids ordering, milestones, or priority ranking.
Each entry distinguishes what the user actually stated from proposals offered for consideration.
Every module below is optional, and the core workout logger must remain fully functional with every one of these modules disabled or absent.
Any module that ever becomes load-bearing for basic set logging has failed its design brief.
Workout start and stop duration tracking, the immediate finish summary of sets, reps, and recorded weight times reps, and the optional workout feeling or free-text note are core roadmap requirements owned by the parent project, so they are intentionally absent from this record.
Recommended safety boundary: users may describe a workout as good or bad, but the app should not automatically grade its physiological effectiveness or diagnose discomfort from metrics.

## Retain and remove principle for all modules

- A module is retained only while it keeps logging the current set fast, effortless, and reliable for the person using it.
- A module is removed or simplified when it adds mandatory steps, mandatory accounts, or mandatory network access to core logging.
- A module is removed when it adds friction that outweighs the value users actually report from it, regardless of how impressive it is.
- A module is removed when its maintenance burden exceeds its usefulness to the primary goal of quick logging.
- Modules are added as independently removable pieces so the app keeps only features that prove useful over time.
- Retention decisions belong to real usage feedback, not to the ambition of the original idea.

## Module: saved workout sequences

### Purpose
- The user wants saved routines stored as ordered sequences of exercises and equipment that can be started quickly at the gym.
- The user explicitly requires that these sequences carry no mandatory set or rep prescriptions, because actual weights and reps are decided at the gym.
- The proposal layer is that a sequence acts as a checklist skeleton that pre-selects the next exercise and equipment to reduce navigation between sets.

### Dependencies
- This module depends on stable exercise and equipment identity in the core store, including the distinction between an exercise and its machine variants.
- It depends on the core ability to pick or add equipment at log time, since a sequence may reference equipment not present at the current gym.

### Boundaries
- Sequences never prescribe required sets, reps, or loads, and never block or nag the user for deviating from the saved order.
- Sequences are an accelerator for starting the next log entry, not a workout player that dictates the session.
- Proposed usability target: skipping, reordering, or ignoring a sequence should require minimal interaction and never block ad hoc logging.

### Unresolved questions
- Should sequences remember the equipment per entry, or only the exercise with equipment chosen fresh each time?
- How should a sequence behave when a saved machine is unavailable at the current gym?
- Should sequences be shareable from day one, or only after the community routines module exists?

### Retain and remove criteria
- Retain if starting a workout from a saved sequence is measurably faster than logging from scratch.
- Remove or downgrade to plain notes if users routinely ignore the sequence structure and log freehand anyway.

## Module: supersets and drop sets

### Purpose
- The user wants support for normal straight sets, linked pairs of exercises for supersets, and structured drop sets.
- The user requires these to be accessible beyond the founder's own training habits, so the concept must be general rather than hardcoded to one routine.

### Dependencies
- This module depends on the core set logging model and on a way to link consecutive set entries without breaking the simple default flow.
- It benefits from the saved sequences module but must not require it, since ad hoc supersets decided mid-session are expected.

### Boundaries
- Straight sets remain the default and require no understanding of supersets or drop sets from the user.
- Superset and drop set linking is a lightweight annotation or grouping on log entries, never a separate required logging mode.
- Grouping must not change how weight and reps are entered, only how entries relate to each other afterward.

### Unresolved questions
- Is a superset a property of the workout session, of the sequence, or of a pair of set entries?
- How are drop sets represented so history and later analysis can still read individual weights and reps per drop?
- How should drop set groupings interact with the optional rest timer module described below without coupling the two?

### Retain and remove criteria
- Retain if linking sets adds no taps to the straight-set path and users actually create linked groups.
- Remove the structured drop set variant if simple repeated logging of decreasing weights serves users equally well.

## Module: optional rest timer

### Purpose
- The user wants an optional, non-intrusive rest timer with a quiet sound.
- Vibration and an independently removable implementation are proposals consistent with the user's modular approach.

### Dependencies
- This module depends only on the moment a set is saved in the core logger, which can serve as its start signal.
- Sound and vibration behavior is device and OS dependent and must be tested on target devices rather than assumed.

### Boundaries
- The timer never blocks logging, never auto-starts the next set entry, and never nags when ignored.
- The timer is off by default until the user enables it, and disabling or removing it changes nothing about logging or history.
- Timer durations are user-chosen conveniences, not prescriptions, and the module must not judge rest length as good or bad.

### Unresolved questions
- Should the timer live only inside the app, or also surface through the floating logger or lock screen module when those exist?
- Should suggested durations attach to superset and drop set groupings, or stay a single global preference?
- How should sound and vibration behave under silent mode, focus modes, and Bluetooth audio on the range of target devices?

### Retain and remove criteria
- Retain if users enable it and keep it enabled over weeks of real training.
- Remove if it is frequently disabled after trial or if device inconsistency makes its cues unreliable.

## Module: personal and public gym inventories

### Purpose
- The user wants to gradually add machines to the catalog while training, building a personal record of what exists where they work out.
- The user defers public inventories maintained by gyms themselves to much later, and that piece is a distant idea only.

### Dependencies
- The personal inventory layer depends on core equipment identity, where the same movement on a Panatta and a Matrix machine share exercise history but keep distinct performance records.
- Any public inventory layer depends on the community catalog module for moderation, attribution, and duplicate handling.

### Boundaries
- The personal inventory is local-first and private by default, with no account and no upload.
- Public gym-maintained inventories are out of scope until community infrastructure exists and are recorded here only so the idea is not lost.
- Inventory data must never gate logging; logging on an unlisted machine is always possible by adding it on the spot.

### Unresolved questions
- How is a personal gym inventory scoped, per venue, per device, or as one flat personal list?
- What happens to personal inventory entries when a community catalog later offers a canonical match for the same machine?
- What verification, if any, would make a gym-maintained public inventory trustworthy without inventing unapproved mechanisms?

### Retain and remove criteria
- Retain the personal inventory if it reduces repeat entry of machines the user trains on weekly.
- Remove or archive the public inventory idea permanently if no credible moderation and accuracy model emerges when it is revisited.

## Module: floating logger and lock screen logging

### Purpose
- The user wants a floating logger that sits over other apps, and later lock-screen rep adjustment with an explicit confirmation step.
- Proposed initial shortcut: weight comes from the app while the quick-adjust surface handles reps and confirmation.

### Dependencies
- This module depends on an active workout session in the core logger that can accept set edits from outside the main UI.
- Lock screen behavior is device and OS dependent, so it must be tested per platform and never assumed to behave identically on Android and iOS.

### Boundaries
- The floating and lock screen surfaces are shortcuts into the same logging model, never a parallel store of their own.
- Confirmation is required before a lock screen adjustment is committed, to protect against accidental pocket input.
- The core app without any overlay permission remains the complete logging experience.

### Unresolved questions
- Which overlay and lock screen capabilities are actually available and reliable on the user's LineageOS device and on the broad range of Android models targeted?
- What is the minimal control set on these surfaces, and does weight adjustment ever belong there or stay app-only?
- How does the overlay behave across Android versions given known platform restrictions on overlays and background activity?

### Retain and remove criteria
- Retain the floating logger if it demonstrably cuts time from finishing a set to having it logged.
- Remove the lock screen variant if device inconsistency makes it unreliable on a meaningful share of target phones.

## Module: iOS platform support

### Purpose
- The user wants a later iOS version so the wider community can participate.
- Android core logging remains first; platform sharing and migration options are researched separately in PLATFORMS.md.

### Dependencies
- iOS support depends on a stable, documented export and import format so history can move between platforms.
- Platform-specific features like lock screen logging need independent feasibility testing on iOS rather than assumptions carried over from Android.

### Boundaries
- Android remains the initial platform, but portable data and logic boundaries should preserve a practical path to iOS.
- No iOS work begins before the core Android logger is validated and in real use.

### Unresolved questions
- Native iOS, shared-logic cross-platform, or export-only interoperability are all undecided approaches.
- Which current Kotlin and Compose choices would constrain or ease a later iOS effort?

### Retain and remove criteria
- Retain the idea while the user or a meaningful user base actually requests iOS access.
- Revisit the implementation approach if it degrades core logging; export interoperability alone is not a substitute for the requested iOS app.

## Module: body muscle visualization

### Purpose
- The user wants a beautiful muscle visualization of the body, potentially a rotatable 3D model.
- A front and back 2D map is recorded as a proposal only and is explicitly not an approved replacement for the desired visual ambition.

### Dependencies
- This module depends on the exercise emphasis mapping module, since the map is only as meaningful as the estimated contribution data behind it.
- It depends on art or model assets whose licensing permits use in an open-source project.

### Boundaries
- The visualization is a review and insight surface, and it must never sit in the logging path or add taps to recording a set.
- Visual fidelity is a genuine user requirement for this module, so a degraded placeholder should not be shipped under its name.

### Unresolved questions
- Is 3D worth its asset, rendering, and maintenance cost compared with a polished 2D presentation?
- What licensed body model or artwork sources exist that fit an open-source project?
- Should the map respond to recent weeks of training only, or also to all-time history?

### Retain and remove criteria
- Retain if users open the map and it changes how they plan or notice their training.
- Remove if it becomes a decorative screen that users visit once and ignore.

## Module: exercise emphasis and compound contribution mapping

### Purpose
- The user wants compound movements recorded with primary and secondary muscle contribution, such as bench press counting toward chest, front shoulders, and triceps.
- The user wants regional emphasis such as upper versus lower chest, views over recent weeks, volume and frequency summaries, and possibly imbalance guidance.

### Dependencies
- This module depends on logged workout history from the core logger and on exercise identity from the catalog.
- It requires credible, licensed mapping data connecting exercises to muscle regions, and that data's provenance must be recorded.
- Mappings must be editable by the user, because no static table will match every machine variant and individual.

### Boundaries
- Data supports logged activity and estimated exercise emphasis only, and must never claim precise physiological muscle tension, hypertrophy, or recovery measurement.
- Fake precision is forbidden, so contribution estimates must carry visible uncertainty and must avoid double-counting shared volume across muscles.
- Any imbalance guidance is informational reflection of logged data, not medical advice, and must be worded accordingly.
- No invented physiological mechanisms may be presented as fact anywhere in this module.

### Unresolved questions
- What licensed anatomy and exercise-mapping datasets exist with provenance acceptable to an open-source project?
- How is uncertainty displayed honestly without making the visualization confusing or ugly?
- How are user-edited mappings reconciled with catalog updates without silently overwriting personal corrections?

### Retain and remove criteria
- Retain if the emphasis summaries are honest, licensed, and actually read by users when reviewing training.
- Remove the imbalance guidance element first if it drifts toward unsupported physiological or medical claims.

## Module: archive, export, backup, sync, and desktop

### Purpose
- The user wants a long-term archive of training history with portable, documented export and import formats.
- The user wants backup independent from the phone, optional synchronization, and later desktop analysis and planning with plans synced back to the phone.

### Dependencies
- Everything in this module depends on a stable, versioned, documented data format for the core log.
- Sync and desktop features depend on that format plus a conflict approach that never silently loses logged sets.

### Boundaries
- Local reliable persistence is foundational core behavior, not part of this optional module.
- Backup, sync, and desktop are strictly optional layers, and core logging and export must work with all of them disabled.
- Export must never be gated behind an account, a network connection, or any paid tier.

### Unresolved questions
- What export format balances human readability, tool compatibility, and forward compatibility across versions?
- Who operates optional sync, and what does privacy-preserving sync look like for health-adjacent training data?
- Does the desktop surface become a full planning tool or stay a focused analysis and sequence-authoring companion?

### Retain and remove criteria
- Retain export and import permanently as a trust guarantee, since users must always be able to leave with their data.
- Remove or redesign sync if it compromises local-first logging or data ownership; an account for an optional hosted service is not itself a blocker.

## Module: AI and MCP data access

### Purpose
- The user wants later AI or MCP access for focused historical queries and proposed routine tuning suggestions.
- Recommended security boundary: begin with scoped read-only access and require explicit approval before write operations.

### Dependencies
- This module depends on stable workout history and on the documented export format as the natural read interface.
- Any write capability depends on an explicit approval flow that has not been designed and is not approved.

### Boundaries
- No AI sits in the essential logging path, and logging works identically with no AI feature present or enabled.
- Read access is scoped to the user's own training data, and no data leaves the device without an explicit user choice.
- Routine tuning output is a proposal the user may ignore, never an automatic change to saved sequences or logged data.

### Unresolved questions
- What concrete questions do users actually want answered that simple history views cannot provide?
- Where does processing happen, and what are the privacy implications of sending training history to any external service?
- What does an approval flow for writes look like without becoming nagware?

### Retain and remove criteria
- Retain only if real query needs emerge that history screens cannot meet.
- Remove entirely if its only function is novelty, or if it pressures the app toward accounts and data collection.

## Module: community exercise and machine catalog

### Purpose
- The user wants community submissions to grow a very large catalog of exercises and machines with make, model, and variants.
- The catalog should also capture gym availability, with duplicate management, attribution, licensing, and moderation as explicit requirements.

### Dependencies
- This module depends on the core equipment identity model where machine variants share exercise history but keep separate performance records.
- It depends on a moderation process, contributor attribution, and license tracking that do not yet exist.

### Boundaries
- The built-in catalog and personal on-the-spot additions keep core logging complete with community features entirely disabled.
- Submissions require provenance for names, images, and descriptions, and copyrighted material must not be redistributed without permission.
- Real-world examples show why care is needed, such as a machine name transcribed as 'Jim Laco' that remains unconfirmed and must not be canonicalized by guesswork.
- Community data about gym availability is user-reported and may be stale, so it must be labeled as such rather than presented as verified fact.

### Unresolved questions
- Who moderates, with what tooling, and how are disputes and duplicates resolved at scale?
- What license governs community-contributed catalog content, and how is attribution displayed?
- How are brand and model names normalized without inventing canonical entries for uncertain transcriptions?

### Retain and remove criteria
- Retain if submissions measurably improve catalog coverage without drowning the project in moderation debt.
- Remove or freeze the public catalog if moderation, licensing, or duplicate chaos exceeds the value of contributed entries.

## Module: community shared routines

### Purpose
- The user wants community routines that can be shared, downloaded, copied, edited, and rated.
- The user wants recognizable athlete-inspired routines, including names and years where relevant.
- Recommended publication boundary: require source provenance and distinguish official plans from community adaptations.

### Dependencies
- This module depends on the saved sequences module, since shared routines are shared sequences.
- It depends on the community catalog's moderation and attribution infrastructure.

### Boundaries
- Shared routines may include optional guidance, but importing or following one must never force prescribed sets or reps on the recipient.
- There must be no implied endorsement by any named athlete, and attribution is historical and informational only.
- Copyrighted paid plans must not be redistributed without permission, and athlete-inspired routines must be labeled as adaptations when they are not the official source.
- Ratings reflect community usefulness and must not be presented as evidence of training effectiveness or safety.

### Unresolved questions
- What provenance fields are required at submission time to keep athlete attributions honest?
- How are official-versus-adaptation labels verified, and who has authority to apply them?
- How does rating abuse or manipulation get handled without building heavy account infrastructure?

### Retain and remove criteria
- Retain if shared sequences save users real planning time and provenance stays clean.
- Remove the athlete attribution feature first if provenance cannot be kept honest, since misleading attribution is worse than none.

## Module: monetization and optional paid services

### Purpose
- The user stated the project is open-source first, with monetization strictly secondary.
- Managed sync, backup, optional AI, coaching, and gym services are candidates to research, not user-approved pricing or packaging decisions.

### Dependencies
- Any paid service depends on the corresponding optional module existing first, such as sync for managed backup.
- It depends on clear free-versus-paid boundaries; recommendations are researched in MONETIZATION.md before any pricing decision.

### Boundaries
- Baseline logging and data export must never be gated behind payment, accounts, or network access.
- No monetization mechanism may degrade, nag, or darken the core logging experience.
- This entry records possibilities only and commits to none of them.

### Unresolved questions
- Which, if any, of the listed services has real demand from actual users?
- What payment, privacy, and support obligations would each service create, and are they acceptable for this project?

### Retain and remove criteria
- Retain this record until an explicit decision is made, then replace it with the decided policy.
- Remove any candidate service whose only plausible funding model would require gating baseline logging or export.

## Cross-cutting concerns for all community and data modules

- Provenance is required wherever content originates outside the app, including athlete routines, machine data, images, and mapping datasets.
- Moderation is a prerequisite for every community feature, and no community feature ships before its moderation path exists.
- Privacy defaults are local-first, and training data never leaves the device without an explicit user action.
- Licensing must be verified for every bundled or downloaded asset, including body models, catalog text, and images.
- No module may present estimates as measurements, and no module may make or imply medical claims.
- A user may record a subjective good or bad feeling; no module may turn logged metrics into an objective workout grade or a diagnosis of discomfort, injury, fatigue, or readiness.
- The project code is licensed Apache-2.0 as accepted by the user, while licenses for community-contributed content and bundled assets remain open questions recorded in the relevant modules above.
