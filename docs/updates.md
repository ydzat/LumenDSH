# Unified Agent Self-Improvement

Status: accepted direction. See the [update contract](contracts/updates.md) for the v0.1 implementation boundary. Presentation and appearance/voice profile releases are implemented and verified; see [current scope and evidence](m5-content-releases.md).

## Identity and state

There is one agent. Everyday requests and requests to improve its own presentation or capabilities use the same identity, conversation, and memory. Self-improvement is an ordinary task with additional artifact and release steps, not a separate assistant or mandatory development conversation.

Candidate code, build output, and validation runs are separate from the active release. This separation concerns deployment state, not agent identity.

## Update classes

| Change | Intended activation | Continuity |
| --- | --- | --- |
| Character assets, animation mappings, voice parameters, supported configuration | Validated content patch and reload at a suitable boundary | Keep the DSH session; avoid replacing audio resources during playback |
| Interpreted UI or behavior extensions, if a supported runtime is selected | Versioned package and component reload | Hot update is conditional on the chosen runtime, not assumed |
| Android services, permissions, native code, or packaged dependencies | Build and sign a compatible APK; perform an in-place Android update | Preserve app data and reconnect to DSH; client restart may interrupt listening |
| DSH plugins or engine changes | Separate backend deployment procedure, only when explicitly in scope | A client patch must not silently restart or replace DSH |

Resource packages, behavioral configuration, and app-native code must not be treated as one interchangeable plugin format. Initial work does not include a general native hot-patching framework.

## Proposed release flow

1. The agent records the requested change and the active component/base version.
2. It creates candidate edits without overwriting the active release.
3. Build and validation tools produce an artifact and actual verification results.
4. The update module checks compatibility, provenance, integrity, and activation requirements.
5. It activates the candidate according to the configured release policy and Android installation requirements.
6. It checks health, records the resulting version, and restores a working release if activation fails where supported.

The [update contract](contracts/updates.md) defines required release concepts and activation states. Final encoding and executable validators are M5 deliverables. Full versioned content packages are the initial delivery format; binary deltas are optional later optimizations.

Self-publication initially means producing and activating a local release on the user's device. It does not imply publishing to a public repository or app store.

## Recovery and signing

- Keep user conversations, credentials, and preferences outside replaceable release content.
- Maintain a working recovery path when a candidate cannot load.
- Do not assume all data migrations can be reversed; validate backup/restore or forward-repair requirements.
- Content rollback and Android APK rollback are different mechanisms. Android may reject a lower version code; recovery may require a newly versioned APK containing the previous implementation.
- APK upgrades require a compatible app identity and signing lineage. Do not assume a locally signed fork can replace an upstream-signed APK.
- Choose a signing model before shipping self-build functionality; do not distribute a shared production private key to users or expose keys to model prompts.
- Preserve the current DSH process across client updates; backend changes are a separate release target.

Reference: https://developer.android.com/studio/publish/app-signing

## First validation scope

Phone-local DSH only. Validate one real content patch, failed-patch recovery, and an in-place client APK upgrade while retaining the same DSH session. Remote orchestration and public distribution are deferred.

## Revised milestone dependency

M5 begins only after [M4.5 client completion](m4.5-completion.md). Its supported
release targets include the completed presentation/preset settings and existing
appearance/voice profiles. Extract applicable local receipt/preflight helpers from
the pinned Android reference; add durable recovery beyond its exception-only backup
restore. M6 extends the same evidence chain to source/DEX/APK verification, installation
and higher-version repair. M7 backend companion changes remain a distinct deployment
boundary; neither content activation nor native upgrade silently modifies DSH.
