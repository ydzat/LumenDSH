# M5 Content Releases

Status: COMPLETE within U01-U04 on 2026-10-25. M4.5 remains closed by user acceptance; its waived checks were not rerun as gates. Presentation and composed appearance/voice profiles share the release lifecycle. See the consolidated evidence below.

## Implemented behavior

The existing authenticated loopback MCP device bridge exposes `assistant_release_status`, `assistant_stage_release` and `assistant_release_action`. No new runtime dependency, DSH deployment, alternate agent, or conversation is created. Refreshing the existing bridge's tool list exposes the methods; the actual same-agent voice release is verified below.

A release manifest contains exactly `schemaVersion: 1`, a bounded `releaseId`, `componentId: presentation | profile`, SHA-256 `baseVersion` and `targetVersion`, UTF-8 JSON `payload`, `minClientCode`, `activationMode: reload`, and `verificationNote`. Payloads use the existing 8192-byte presentation parser. The complete envelope is bounded to 24000 bytes; journal records are bounded to 65536 bytes. Payload size is computed from the embedded string. No migration is supported. Authentication comes from the paired device bridge, not the hash or caller-supplied verification note.

Candidates are stored separately in app-private AtomicFile journals. Schema, compatibility, exact base and payload integrity are checked before staging and again before activation. Repeated requests with the same ID/content return the existing receipt; a conflicting ID is rejected. Activation and rollback require a foreground, idle-audio boundary. The previous configuration is recorded before the active preference changes. Presentation reload preserves the current DSH session. A changed presentation also invalidates the habitat rendering cache.

An interrupted `validating` receipt is rejected. Interrupted `activating`, `verifying` or `recovering` receipts restore the recorded previous configuration on initialization. Recovery failures remain explicit and block new activation. Explicit rollback requires that the current payload still matches that release's target. There are at most 32 retained release records; rejected, staged and recovered records can be explicitly discarded. No automatic deletion of an active or unresolved recovery record occurs.

Activation remains `verifying` until the foreground runtime applies the exact profile, checks the initialized TTS engine/parameters and successfully draws the real habitat view. Failure restores the prior content. UI health is asynchronous and is distinct from human visual or acoustic approval. A real process termination during verification was recovered on the next Activity start.

## Reused implementation

- Existing PresentationSettings schema, immutable version archive and Android AtomicFile storage.
- Existing authenticated device bridge and installed MCP SDK; no separate update service.
- `tooling/content-release/receipts.mjs`: pure SHA-256 and atomic JSON receipt helpers extracted from guzhou079-arch/deepseek-harness-android, revision `a5af7b105b7fdb006e3fc3be2a2179d83907c2cb`, `selfbuild/scripts/release-receipts.mjs`. MIT copyright and complete license retained beside the file. Project paths, compilation/publisher functions and bundled runtime were excluded.
- BackupValidator was inspected. This increment accepts one bounded JSON payload, with no ZIP extraction; archive validation reuse belongs to appearance-package releases.

## Local CLI

Run in the existing independent Termux environment with `DSH_ROOT` and `SLIVER_WOLF_TOKEN_FILE` configured. The token is consumed internally; never print or copy its contents.

```sh
node tooling/content-release/release.mjs status
node tooling/content-release/release.mjs stage my-release candidate.json candidate.receipt.json
node tooling/content-release/release.mjs activate my-release
node tooling/content-release/release.mjs rollback my-release
```

Stage records the exact manifest locally before transmitting it. It does not activate. After an uncertain result, inspect status by release ID; do not assume the operation failed. Status includes the user's presentation configuration and should not be posted publicly.

## Completed scope and device evidence

| Gate | Actual evidence |
| --- | --- |
| U01 | The current DSH agent generated its own Node release script in the same selected conversation, staged voice rate 0.9, observed runtime rate 0.9 with state active, then restored the exact previous profile hash. Receipt `m5-voice-mvn6f48o-397cf8812f92`: active -> recovered, restored=true. |
| U02 | Android ReleaseChecks rejected modified hashes, stale base and incompatible client versions; checked ID conflicts, repeat requests and activation-time base changes. The active configuration stayed unchanged on rejection. |
| U03 | ReleaseCrash persisted a real production presentation candidate at verifying and terminated the actual app process. MainActivity startup restored the previous version. A new instrumentation process reported ACTUAL_PROCESS_DEATH_RECOVERY_PASS. |
| U04 | A real profile referencing an unavailable TTS engine passed structural staging but failed runtime health. The prior profile was restored; REAL_VOICE_HEALTH_FAILURE_RECOVERY_PASS. Isolated injected renderer-failure checks also exercised the shared recovery branch; they are not presented as a real renderer crash. |

A licensed generated Codex-v2 fixture crossed the authenticated artifact boundary, was imported inertly, activated through a profile release and rolled back exactly: REAL_APPEARANCE_IMPORT_ACTIVATION_ROLLBACK_PASS. The normal presentation release/rollback path was also exercised on the phone.

The legacy profile JSON contains Java float serialization details. `assistant_prepare_profile` accepts displayName, appearanceVersion and the existing six-field voice configuration, returning the exact canonical payload and target digest without activation. Agents should use it instead of reproducing Java float serialization. `assistant_import_appearance` accepts only a completed authenticated artifact operation key, reusing the established bounded ZIP importer. No user path is accepted.

Normal speech playback is not replayed after recovery. Current appearance/profile versions remain protected by existing library references, while release journals retain exact previous payloads. Recovery-required records prevent another activation. This release has no schema migration, arbitrary executable content, native hot replacement, remote updater or new audio engine.

The initial code-35 build/evidence was a presentation-only increment. Consolidated M5 behavior is included in the code-38 application and identical code-39 repair DEX; see [native release evidence](m6-native-releases.md). The same-agent bash sandbox initially restricted direct device-loopback access; the exact local script succeeded after normal one-time DSH approval. DSH was never restarted or upgraded.

Local silent M5 demonstration: `artifacts/demos/20261025-030342-m5-complete-79b155f7.mp4` (decoded duration 7.977 seconds), showing a real activated presentation tag and the same controlled conversation. The demo release subsequently restored its exact original hash. Screen/video frames were inspected. Neither silent clip is acoustic evidence.
