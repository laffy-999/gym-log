# Gym Log monetization research

## 1. Status and scope

This document is research, not a monetization decision.
No billing, server, sync, AI, or store-integration work is authorized by this document.
The immediate priority remains the free Android core logger described in [PLAN.md](PLAN.md).
Any paid option would only be evaluated after the personal Android experience is useful; it need not wait for an iOS launch.
Facts are cited to official pages checked on 2026-10-01, and everything not cited is labeled as proposal or assumption.
Store prices and commissions vary by region and date, so every number below must be rechecked before any real pricing decision.

## 2. Evidence from comparable products

### 2.1 Fitness trackers and their free/paid boundaries

| Product | License and cost model | Free boundary | Paid boundary | Source checked 2026-10-01 |
| --- | --- | --- | --- | --- |
| Hevy | Proprietary, free app with Hevy Pro purchases | Core logging, community, and a limited number of routines and custom exercises | Unlimited routines, unlimited graph history, unlimited custom exercises, advanced measurements | [App Store listing](https://apps.apple.com/us/app/hevy-gym-log-workout-tracker/id1458862350) |
| Strong | Proprietary, free accounts with Strong PRO upgrade | Account and logging free forever, data export any time | PRO adds advanced charts and analysis features | [strong.app](https://www.strong.app/) |
| Liftosaur | AGPL-3.0 open source; its official site features user reports of premium purchases | Public materials describe free tracking, programs, offline mode, and account-based cloud storage | Exact current premium feature boundary was not verified; do not assume cloud sync is paid | [GitHub repository](https://github.com/astashov/liftosaur), [liftosaur.com](https://www.liftosaur.com/) |
| Flexify | MIT open source with voluntary sponsorship and donation links | Offline-first logger; user reports it is free and requires no account | No paid tier identified in the reviewed repository | [GitHub repository](https://github.com/brandonp2412/Flexify) |

Observed facts from these sources.

- Hevy's App Store description states the app is free and shows no ads, and its listed Pro features are expansions of limits rather than removal of core logging.
- Hevy's public web listing does not publish Pro prices, so prices are shown in-app and must be treated as regional and unverified here.
- Strong's homepage states accounts are free forever and that users can export their data any time.
- Strong's current PRO prices were not confirmed on a public page during this research, so no Strong price is claimed here.
- Liftosaur combines open-source development with reported premium purchases, but the reviewed pages do not establish a precise current premium entitlement list.
- Flexify demonstrates voluntary donation links in this app category; those links alone do not prove that donations cover its development costs.

Interpretation for Gym Log, stated as analysis rather than fact.

- These trackers offer basic logging without an upfront purchase, but public product pages do not prove their profitability or retention.
- Hevy-style limits on saved routines or history depth would conflict with the Gym Log principle that core logging and history stay unrestricted.
- The acceptable paid surface for Gym Log is therefore hosted services and optional extras, not the local logger.

### 2.2 Open-source projects funded by managed hosting

| Project | Model | Paid tiers as published | Self-host option | Source checked 2026-10-01 |
| --- | --- | --- | --- | --- |
| Bitwarden | Open-source password manager with free tier and paid plans | Premium 1.65 USD per month billed annually at 19.80 USD; Families 3.99 USD per month for 6 users; taxes excluded | Documented self-host option included even for personal plans | [Bitwarden pricing](https://bitwarden.com/pricing/) |
| Joplin | Open-source note app funded by Joplin Cloud subscriptions | Basic 2.99 EUR per month or 28.69 EUR per year; Pro 5.99 EUR per month or 57.48 EUR per year; Teams 7.99 EUR per user per month | Joplin Server self-hosting remains available | [Joplin plans](https://joplinapp.org/plans/) |

Observed facts from these sources.

- Bitwarden keeps a fully functional free tier and charges roughly 20 USD per year for personal premium features.
- Joplin's own FAQ states that subscriptions support development of the project as a whole, including the open-source applications.
- Both projects offer self-hosting, with product-specific licenses and entitlements; self-host availability does not imply that every paid feature is freely licensed.

Interpretation for Gym Log.

- Managed sync and backup is a promising funding pattern for Gym Log; Joplin is the direct hosted-sync example, while Bitwarden also demonstrates that free sync can coexist with paid extras.
- The Joplin framing, where the subscription openly funds the free open-source app, matches the Gym Log intent to keep the logger genuinely useful for free.

### 2.3 Donations and sponsorships

Observed facts from the GitHub Sponsors documentation checked on 2026-10-01.

- GitHub Sponsors charges no fees for sponsorships from personal accounts, so 100 percent of those sponsorships reach the sponsored developer or organization.
- Sponsorships from organization accounts carry a fee of up to 6 percent, split into a 3 percent credit card processing fee and a 3 percent GitHub service fee.
- Creators can publish up to 10 one-time tiers and 10 monthly tiers with prices in US dollars.
- Source: [About GitHub Sponsors for open source contributors](https://docs.github.com/en/sponsors/receiving-sponsorships-through-github-sponsors/about-github-sponsors-for-open-source-contributors).
- Flexify's repository demonstrates the donation pattern in this exact app category through its GitHub Sponsors and cryptocurrency links.

Interpretation for Gym Log.

- Donations cost nothing to gate and fit a project whose core must stay free, but donation income alone is unpredictable and should not be planned as the only funding for paid hosting.

### 2.4 Store billing and commissions

Observed facts from Apple sources checked on 2026-10-01.

- Apple's Small Business Program reduces the App Store commission to 15 percent on paid apps and in-app purchases for developers earning up to 1 million USD in annual proceeds.
- Source: [App Store Small Business Program](https://developer.apple.com/app-store/small-business-program/).
- Under the unified EU terms effective 2026-10-01, Apple charges 26 percent on In-App Purchase sales in the EU, or 15 percent for Small Business Program participants and for subscriptions after their first year.
- Alternative payment processing inside an EU app carries 20 percent, or 10 percent for program participants.
- Out-of-app offers with an actionable link in the EU carry a store services commission of 15 percent, or 10 percent for program participants, on sales within 7 days of the link tap.
- Apps distributed in the EU outside the App Store pay a 5 percent Core Technology Commission on digital transactions.
- EU developers offering alternative payment options must maintain their chosen payment options for 12 months.
- Source: [Changes for apps in the European Union](https://developer.apple.com/support/apps-in-the-eu/).

Observed facts from Google Play sources checked on 2026-10-01.

- In markets where Google's newer regional structure has not rolled out, developers enrolled in the 15 percent tier pay 15 percent on the first 1 million USD of annual earnings and 30 percent above that.
- In those markets, auto-renewing subscriptions are charged 15 percent regardless of revenue.
- For alternative billing systems offered in South Korea or India, the service fee is reduced by 4 percent.
- In the EEA, developers may offer alternative billing and may direct users outside the app to promote offers, subject to program requirements.
- A newer regional fee structure applies to the EEA, United Kingdom, and United States from 2026-06-30 and to Australia and Japan from 2026-09-30, where auto-renewing subscriptions are charged 10 percent plus a 5 percent billing fee, and other transactions are charged 20 to 25 percent plus a 5 percent billing fee, with reduced rates for external web links.
- Source: [Google Play service fees](https://support.google.com/googleplay/android-developer/answer/112622).

Interpretation for Gym Log.

- Store commissions are regional, program-dependent, and actively changing in 2026, so any unit-economics model must treat the fee as a per-region variable rather than a single constant.
- A web subscription is not automatically exempt from app-store rules or fees; linking, entitlements, reporting, and regional eligibility must be checked before choosing a checkout flow.
- The cited transaction commissions do not charge for an ordinary free download, but developer membership/registration and operating costs are separate.

## 3. Recommended free and paid boundaries

This section is a recommendation, not a decision.

### 3.1 Why local paywalls are weak here

Gym Log is Apache-2.0, so anyone can build or fork the source and remove a local paywall.
A feature gate inside the APK is therefore not a durable moat, only an inconvenience that a fork can strip.
The defensible value is the hosted service, operational trust, data safety, and ongoing maintenance, which a fork does not automatically replicate.
This matches the Bitwarden and Joplin pattern, where the code is open and the managed service is the paid product.

### 3.2 Recommended free-forever boundary

- Unrestricted logging of workouts, sets, equipment identity, corrections, and history.
- Personal saved routines, including superset and drop-set structures when they are built.
- Portable export and import of all recorded data in a documented format.
- All analysis that runs locally on the user's own data, including charts and load-volume views.
- Community basics, meaning browsing, downloading, and submitting catalog entries and routines with attribution, subject to moderation capacity.

### 3.3 Recommended paid-optional boundary

- Managed multi-device sync and off-device backup operated by the project, once such a service exists.
- Voluntary supporter contributions, optionally acknowledged with an opt-in badge or thanks, without locking useful local features behind payment.
- An optional family or household managed-service plan, if sharing and key management can remain clear and private.
- Optional, explicitly metered AI features whose per-use cost is visible, added only after the read-only scoped design in [FUTURE-MODULES.md](FUTURE-MODULES.md) is settled.
- Later coach or gym-facing services, such as gym-maintained machine inventories or coach client management, evaluated only after the personal product is stable.
- A creator and community marketplace for routines is deferred beyond these, and any licensing payments for named athlete content require explicit permission from rights holders.
- Community adaptations must not be presented as official athlete plans; selling third-party content or implying endorsement requires a rights and provenance review.

### 3.4 License separation for content

The Apache-2.0 license covers the software, not community-contributed content.
Catalog entries, gym inventories, and shared routines need their own explicit contribution license chosen before community features launch.
Each shared routine needs provenance fields distinguishing official plans, licensed reproductions, and community adaptations.
This separation prevents a permissive code license from being misread as permission to redistribute copyrighted training content.

### 3.5 Sync, hosting, and moderation sustainability

A hosted sync service has real per-user storage and bandwidth costs, so an unlimited free sync tier would risk the project's sustainability.
If infrastructure measurement later shows the per-user cost is negligible, a limited free sync tier is preferable for goodwill, with paid tiers for heavier use.
If costs are material, sync should launch as a paid service from day one rather than launching free and being restricted later, because removing a free capability damages trust more than never offering it.
Community catalog and routine hosting adds moderation labor, which is a recurring human cost that donations alone may not cover.
Any free community tier therefore needs rate limits and clear moderation capacity before launch.

### 3.6 AI costs and privacy

Hosted AI requests have usage-dependent costs, so an unlimited free hosted AI promise is unsafe without measured funding and controls; local models or user-supplied provider access are different cost models to evaluate later.
Workout history is sensitive personal data, so AI queries should run on explicit user request, with clear statements about what leaves the device.
A privacy-respecting default is local analysis free, cloud AI paid and opt-in, and no AI in the essential logging path.

### 3.7 Data portability on cancellation

Cancelling a paid subscription must never lock the user out of their data.
Local data stays on the device regardless of subscription state, and export remains free.
A cancelled sync subscriber should get a grace window with a documented final export of server-held data before deletion.
This policy follows the principle that the user's training record belongs to the user, and it matches the export-any-time norm visible in Strong's public materials.

### 3.8 Explicit non-goals

- No advertising in the app.
- No sale or brokerage of user training data.
- No gamified interruptions, streak pressure, or engagement mechanics inserted into the logging flow.
- No paywall on logging, history, routines, export, or local analysis.

## 4. Staged funding plan, all stages proposals

| Stage | Trigger | Funding mechanism | Notes |
| --- | --- | --- | --- |
| 0 | Now | None | No monetization while the Android core is being built |
| 1 | First public, verified release | GitHub Sponsors and donation links | Zero fee on personal sponsorships per GitHub documentation; income unpredictable |
| 2 | Managed sync service exists and costs are measured | Low-priced sync and backup subscription | Follows the Bitwarden and Joplin hosted-service pattern |
| 3 | AI module exists with scoped read-only design | Metered or quota-based AI add-on | Only with explicit per-use cost and privacy disclosure |
| 4 | Coach and gym services proven useful | Service subscriptions for coaches or gyms | B2B-style revenue that does not touch personal logging |
| 5 | Much later | Creator marketplace with licensing payments | Requires rights-holder permission and a separate content license framework |

Each stage is independent, and stopping at any stage leaves a working free product.

## 5. Hypothesis price ranges

These are proposals anchored to the cited evidence, not market facts or decisions.

- Test roughly 20 to 30 EUR per year or 2 to 3 EUR per month for managed sync and backup, with regional equivalents determined later; this is a willingness-to-pay hypothesis, not a price commitment.
- Avoid lifetime managed-sync or unlimited-AI promises: a one-time payment does not remove recurring infrastructure, support, and recovery obligations.
- A metered AI add-on priced at cost plus a transparent margin once real per-query costs are measured, with no flat unlimited AI tier.
- Regional pricing differences on app stores mean any published price must be a local-currency schedule, not a single global number.

## 6. Transparent unit-economics sketch

This model uses assumed variables only and contains no measured costs.
Start from revenue net of collected sales tax or VAT, then subtract the actual applicable store commission or processor percentage, processor fixed fee, refunds, variable service costs, and support costs.
Do not apply both web processing and store processing mechanically; use the real channel contract, including any store fee that still applies to an external purchase.

| Variable | Meaning | Assumed example value, not measured |
| --- | --- | --- |
| P | Monthly price excluding tax for this example | 2.50 EUR |
| Store fee | Assumed eligible transaction commission | 15 percent |
| Web percentage | Illustrative processor percentage, not a quoted tariff | 3 percent |
| Web fixed fee | Illustrative per-payment fee, not a quoted tariff | 0.30 EUR |
| Variable service cost | Storage, bandwidth, support, and backup allocation per payer | 0.30 EUR per month |

With those assumptions, store-channel contribution is 2.50 × 0.85 - 0.30 = 1.825 EUR per paying subscriber per month.
Web-channel contribution with no additional store commission is 2.50 × 0.97 - 0.30 - 0.30 = 1.825 EUR per paying subscriber per month.
The example shows why fixed fees matter at low monthly prices and why web checkout is not automatically cheaper.
Annual billing can amortize a fixed transaction fee, but refunds, retention, and regional tax treatment still matter.

Funding a free majority requires an additional calculation.
Monthly operating contribution equals paying users multiplied by payer contribution, minus free hosted users multiplied by their variable cost, minus fixed infrastructure, moderation, and other operating costs.
For an illustrative 10,000 users with 3 percent paying, 300 × 1.825 = 547.50 EUR is available before free-user costs and fixed costs.
If the other 9,700 users each incur an assumed 0.02 EUR per month of shared hosting/moderation cost, 194 EUR is consumed before fixed costs or development time.
Neither the conversion rate nor either cost assumption has been measured for Gym Log.
Offline-only users do not necessarily incur a per-user server cost, which is a major benefit of keeping the core local-first.
Replace all assumptions with actual measurements and include founder time before calling the service sustainable.

## 7. What to validate before pricing

- Measured infrastructure cost per synced user from a real deployment, replacing the placeholder above.
- Measured AI cost per typical query session, if the AI module is ever built.
- Willingness to pay, tested with an optional waitlist or survey, not assumed from competitor prices.
- Conversion and retention behavior in comparable apps, gathered from published shareholder or developer reports where available rather than guessed.
- Store commission and tax treatment per target region at decision time, because the 2026 fee schedules are changing.
- Support and moderation time per active user once community features exist.
- Whether a limited free sync tier is affordable after measuring the per-user cost.

## 8. Risks and tradeoffs

- Charging for anything risks community goodwill in an open-source project, which is mitigated by keeping the free boundary wide and never shrinking it.
- Forks can undercut any paid local feature, which is acceptable because the paid product is the service, not the binary.
- A free sync tier could become a large uncapped cost, so it requires measurement and limits before launch.
- Subscription revenue may never cover developer time, so the project should be scoped to remain healthy on donations alone.
- Store policy changes, as the 2026 EU and regional fee shifts show, can alter economics after launch, so pricing must be reviewable.
- Selling athlete-branded routines without permission creates copyright and endorsement risk, which is why marketplace monetization stays deferred and requires explicit licensing.

## 9. Sources

All sources checked on 2026-10-01.

- [Hevy on the App Store](https://apps.apple.com/us/app/hevy-gym-log-workout-tracker/id1458862350), free app with Pro upgrade features listed.
- [Strong](https://www.strong.app/), free forever accounts and data export claims.
- [Liftosaur on GitHub](https://github.com/astashov/liftosaur) and [liftosaur.com](https://www.liftosaur.com/), AGPL-3.0 tracker with paid subscription evidence in store reviews.
- [Flexify on GitHub](https://github.com/brandonp2412/Flexify), MIT free tracker funded by donations.
- [Bitwarden pricing](https://bitwarden.com/pricing/), free tier, premium annual pricing, self-hosting.
- [Joplin plans](https://joplinapp.org/plans/), Joplin Cloud pricing and the statement that subscriptions fund the open-source apps.
- [GitHub Sponsors documentation](https://docs.github.com/en/sponsors/receiving-sponsorships-through-github-sponsors/about-github-sponsors-for-open-source-contributors), fee structure.
- [Apple App Store Small Business Program](https://developer.apple.com/app-store/small-business-program/), 15 percent commission terms.
- [Apple changes for apps in the EU](https://developer.apple.com/support/apps-in-the-eu/), unified EU terms effective 2026-10-01.
- [Google Play service fees](https://support.google.com/googleplay/android-developer/answer/112622), fee tiers and regional schedules.
