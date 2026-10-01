# Security and privacy

## Current status

Gym Log is early development software, not a security-audited product.
This document distinguishes observed source properties from requirements that still need implementation and verification.
It does not promise that data cannot be stolen or lost.

The current Android source uses normal application-private SQLite storage.
It contains no application-level database encryption, sync service, AI connection, analytics SDK, or network permission.
The manifest sets `allowBackup=false` and exports the launcher activity only.
These are source observations, not a completed runtime security assessment.
Manufacturer-specific device transfer behavior must not be assumed disabled solely because of `allowBackup=false`.

## Assets to protect

- Workout history, equipment records, routines, and personal training patterns.
- Optional feeling and discomfort notes, which may be sensitive health-adjacent information.
- Future account sessions, sync credentials, encryption keys, and private backups.
- Future links between a public community identity and otherwise private training records.

Collect only what is necessary for the selected functionality.
Do not collect location, contacts, health-platform data, advertising identifiers, or telemetry just because they might be useful later.

## Protection boundaries

| Threat | Baseline or required mitigation | Limit |
| --- | --- | --- |
| Another ordinary Android app reads private files | Android application sandbox and internal app storage | Not a guarantee on a rooted or compromised OS |
| Lost locked phone | Secure OS configuration, screen lock, and device encryption | Device and user configuration matter; an unlocked phone is different |
| Workout sent to a server without consent | No network functionality in initial logger; explicit opt-in for future services | Exporting or sharing intentionally moves data outside the app |
| Accidental deletion, update failure, or device loss | Durable writes, migrations, verified backup/restore before sole-record use | Local storage alone is not backup |
| Credentials extracted from app files | Later use OS-protected key storage for credentials and wrapping keys | Keystore does not prevent all use of keys by a compromised app process |
| Server compromise | Prefer end-to-end encrypted personal backup/sync, with a documented key and recovery design | Not implemented; metadata and availability still need protection |
| AI service sees private notes | Read-only scopes, selected fields/date ranges, separate consent before transmission | External processing cannot be advertised as private from that provider |
| Malicious community content or imports | Validate schemas and bounds; treat all text as data, not instructions or executable code | Moderation and abuse response are still needed |

No app can reliably protect plaintext being used on a fully compromised device.
Do not promise protection against root access, an unlocked device in an attacker's hands, malicious accessibility services, or a compromised operating system.
Lock-screen shortcuts will require a separate privacy decision because convenience can expose data or permit changes without unlocking.

## Core release requirements

1. Keep the database in app-private internal storage, not a shared external directory.
2. Export no components beyond those required for documented Android integration.
3. Do not accept workout mutations from arbitrary external intents.
4. Do not log workout contents, notes, credentials, or database dumps.
5. Show useful errors without displaying raw internal exceptions or sensitive records.
6. Use parameterized SQL and validate numeric and textual boundaries before committing writes.
7. Make committed writes atomic and distinguish an unsaved draft from a saved set.
8. Make initial-load failures recoverable rather than showing an apparently empty history.
9. Review backup and device-transfer rules explicitly and test them on supported versions.
10. Ship release builds without debug access and protect signing credentials outside the repository.
11. Keep dependencies limited, pinned, and maintained, and review security advisories before distribution.
12. Review the merged release manifest, permissions, exported components, and runtime network behavior.

Application-layer database encryption is a design option, not a feature already present.
Before promising it, select a maintained cross-platform-capable database solution and design key loss, biometric changes, backup, restore, and migration behavior.
Encrypting a database with a key stored next to it is not a sufficient design.
Adding cryptography without recoverable keys can increase permanent data-loss risk.
No custom cryptographic algorithms should be invented for this project.

## Future backup and sync

Core logging must complete locally while the server is unreachable.
Use authenticated HTTPS with normal certificate validation and disallow cleartext transport in release configuration.
Do not add a trust-all certificate handler or expose raw SQL over an API.
Authorization must be checked server-side for every user-owned record.
A record's ID is not an access-control mechanism.

Prefer a client-encrypted personal backup/sync design if its recovery and sharing tradeoffs can be made understandable.
Do not label TLS or server-side disk encryption as end-to-end encryption.
An end-to-end encrypted service cannot also perform arbitrary server-side analysis of plaintext history without an additional user-authorized disclosure or different processing design.
Keep public community content separate from private workout storage and keys.

Before enabling sync, specify stable identities, revisions, idempotency, deletion handling, conflicts, key management, and account recovery.
Test repeated uploads, offline edits, deletion-versus-edit conflicts, expired credentials, and a compromised or unavailable server.
Sync is not backup: synchronized deletion can remove every live copy unless separate retention exists.

## Future AI and MCP access

An AI integration should begin with user-approved, scoped read-only queries over selected history.
Default to excluding free-text discomfort notes and personal identifiers unless the user chooses to include them.
Do not give an agent unrestricted database credentials, file access, or arbitrary SQL execution.
Treat exercise descriptions, imported routines, community text, and retrieved notes as untrusted input, not authority to invoke tools.

Proposed workout changes should be previewed and explicitly accepted before a write.
Separate permissions for reading history, drafting routines, modifying plans, and deleting records.
Never let an AI result silently alter past completed sets.
Use revocable credentials and document which provider receives what information and for how long.
AI service errors or quota exhaustion cannot block core logging.

## Reporting a vulnerability

Do not put personal workout data, credentials, database files, or exploit details into a public GitHub issue.
GitHub private vulnerability reporting is enabled for this repository.
Use [Report a vulnerability](https://github.com/laffy-999/gym-log/security/advisories/new) to send a private report.
There is no bug-bounty or response-time commitment at this stage.

## Primary references

Checked 2026-10-01.

- [Android security best practices](https://developer.android.com/privacy-and-security/security-best-practices): private storage, minimal permissions, component exposure, secure transport, and data handling.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore): key extraction resistance, hardware support, authentication restrictions, and the distinction between protecting a key and preventing its use in a compromised process.

These references support the design principles, not a claim that this app has implemented or passed every control.
