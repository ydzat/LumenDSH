# Decisions and Open Questions

## LumenDSH product identity (2026-10-25)

LumenDSH is the project and application name, succeeding the author's earlier
[Lumen](https://github.com/ydzat/lumen) in the overlapping personal-assistant and
companionship role. Silver Wolf / 银狼 is the replaceable default character,
with fan art based on Silver Wolf from *Honkai: Star Rail*. Users can choose their
own Agent name and appearance. The default appearance source is
[awesome-codex-pet / silver-wolf--lingxiaotian](https://github.com/legeling/awesome-codex-pet/tree/main/pets/silver-wolf--lingxiaotian).
Retain the existing package ID, signing identity, data paths and user-configured
Agent names. Update generic application labels without changing wake-name defaults
or selected character profiles. This handoff does not rebuild/install an APK,
commit, push, archive or otherwise modify the old Lumen repository.

## Usable-baseline handoff and remote authorization (2026-10-25)

Pause feature development at 0.7.46. Prepare English/Chinese README documentation
and logo concepts for selection. Connecting `origin` to `ydzat/LumenDSH` and fetching
its existing MIT LICENSE were authorized first. After reviewing the publication
files and history, the user authorized a clean-snapshot commit and push to the
existing remote `main`. Preserve its MIT-license commit and keep the original local
development graph private. This does not authorize force-pushes or APK releases.
See [project status](project-status.md).

## Single-version DSH upgrade decision (2026-10-25)

The user explicitly authorized the current-upstream upgrade and then rejected
keeping the old version to avoid confusion. Use only DSH `0.2.1-rc.3` and its
inspected client protocol. Remove the old runtime and compatibility branch after
migration verification; retain user data and a private data/configuration backup.
Do not turn this explicit upgrade into permission for incidental future upgrades.
Keep model credentials under DSH's own provider, and preserve normal one-time
approval when Android cannot enforce the requested command sandbox.
See [current compatibility](../integrations/dsh/compatibility-rc3.md).

## Confirmed constraints

- Target device: Huawei nova 5T running Android 10; existing stock phone functionality should be preserved.
- Initial backend: the phone-local DSH deployment in Termux. Client and backend must evolve independently. Remote DSH is deferred.
- Daily assistance and self-improvement are performed by the same agent, with shared identity, conversation, and memory; a separate development assistant or conversation is not required.
- Self-improvement should produce versioned patches or releases. Prefer hot updates for supported content and in-place APK upgrades for native changes; do not rely on uninstall/reinstall.
- Candidate artifacts and validation runs are isolated from the active release, without splitting the agent identity.
- Prefer reuse of existing open-source projects over building every component from scratch.
- The desired client has a visible character and voice interaction, including background/locked-screen wake-up.
- Phone-side compilation is desired; the build toolchain should be independent of the client APK.
- The repository directory is `sliver-wolf`.
- The project is local-only at present. Do not configure remotes, push, upload source/artifacts, or publish releases without a new explicit user instruction. Local Git history is still appropriate.

## Accepted technical directions

| ID | Decision | Implementation boundary |
| --- | --- | --- |
| D01 | Attach through a version-adapted DSH session API | Do not use a subprocess-owning SDK to manage the existing service |
| D02 | Prefer MCP for device tools | App provides tools; transport support must be checked against installed DSH |
| D03 | Import external pet formats into a normalized appearance model | Start with one pinned Codex-compatible format; no universal compatibility claim |
| D04 | Compose appearance, voice, and behavior independently | Skin import cannot silently change agent identity or permissions |
| D05 | Stage versioned local updates with recovery | Resources/configuration reload; native code uses an in-place APK upgrade |
| D06 | Reuse update-system designs selectively | TUF is a trust reference; Tinker and remote update infrastructure are deferred |
| D07 | One agent performs daily work and self-improvement | Candidate artifacts are separate; identity/conversation are not split |

Internal contracts are documented under [contracts](README.md). They are not claims of deployed upstream functionality.

## Remaining implementation decisions

| Topic | Decision/evidence required | Gate |
| --- | --- | --- |
| DSH compatibility | Installed authentication, methods, events, approval and retry behavior | M0 before M1 |
| Reuse and UI stack | Pin upstream modules; select native/WebView/hybrid approach after minimal build | M0 |
| Build system | Adapt upstream scripts or use a standard Android build; prove phone compilation | M0/B01 |
| MCP transport | Native loopback service, or minimal adapter if required by old DSH | M2 |
| Artifact handoff | Bounded authenticated transfer across Termux/App private storage | M2/D04 |
| Voice engines | Actual Android 10 wake/STT/TTS performance, availability, and power cost | M3 |
| Voice policy | Wake per request vs bounded conversation; interruption behavior | M3 |
| Format details | Pinned input schemas, renderer limits, manifest validator | M4 |
| Release format | Final schema/signing encoding, crash recovery, retention, migration | M5 |
| Signing ownership | Self-built APK upgrade identity without distributing a shared production key | M6 before native self-upgrade |

M0 resolved the initial UI/build decision: native Java Activity with Termux OpenJDK,
aapt, D8 and apksigner, plus verified API 29 stubs. This small original diagnostic
client needs no upstream application fork, Gradle, PRoot or base APK. It is not a
commitment that this build route already supports future NDK/AndroidX components.

Installed protocol mapping is recorded in [rc.7 compatibility](../integrations/dsh/compatibility-rc7.md).
No authentication exists in this backend; first-stage loopback access uses the existing
trust fence without changing backend deployment. Missing auth must remain visible.
Submission deduplication is treated as unsupported; ambiguous delivery is never
automatically retried. Event transport is WebSocket, with history reconciliation for
recovery. S02-S05 exercised paths and remaining limits are recorded in
[M1 evidence](../tests/m1-evidence.md).

M1 uses Java-WebSocket 1.6.0 with SLF4J API 2.0.13, explicitly approved by the user.
Pinned artifacts and MIT notices are recorded in [dependencies](../apps/android/DEPENDENCIES.md).
The adapter uses installed rc.7 HTTP RPC and WebSocket event streams. Session creation
is explicit; reconnect restores the selection and reconciles history. Pending request
identity is persisted before submission; uncertain delivery blocks automatic resend.
This is a client policy, not a server exactly-once guarantee.

M2 uses Android's system-selected installed TTS for explicit speech tools; the tested
phone selected Google TTS and produced clear Chinese audio confirmed by the user.
Wake-word/STT engines, the M3 continuous voice policy, and the appearance importer remain open.

M2 preview reuses the installed official MCP SDK through a Termux stdio adapter and
Java-WebSocket for the authenticated app boundary. This is a dependency-reuse choice,
not a DSH transport limitation. Status, notification, speech, and artifact handoff are verified; see
[adapter design](../integrations/device-tools/README.md). Its release directory is
separate from candidate source. Developer-assisted pairing works; production pairing/revocation
UX remains a productization follow-up. M2 uses bounded operation records and never replays
unfinished speech after process death. File transfers remain inert until future update validation.

M4 explicitly includes conversation/decision UI visual design, not only pet assets.
The M1/M2 engineering UI is not the final product design; UI01 requires user design
review and actual target-phone layout checks.

## Future scope

### Current-backend preference (2026-10-25)

The user requests investigation of upgrading the phone's DSH to current upstream
before choosing companion/memory plugins. Prefer current compatible plugins rather
than treating the initial rc.7 community port as a permanent constraint. Isolated
candidate installation and phone compatibility work are explicitly in scope;
preserve active data and service while establishing a concrete migration path.
See [upgrade investigation](dsh-upgrade-investigation.md). This does not change the
independent Termux runtime or local-only architecture.

Remote DSH connection, authentication, capability negotiation, and assisted phone onboarding are future work. Do not add a remote deployment service to the first milestone.

## M4 accepted visual direction (2026-10-24)

The user accepted [Attention Choreography Review 05](design/m4-attention.md)
and authorized native implementation. The primary screen is a voice-first character
habitat with persistent space/character composition selection. Transcript and technical
controls are secondary. Background work traces sit behind the character; readable
speech/reading surfaces and explicit decisions occupy foreground attention. Listening,
speaking, reading and approval handling determine when new information may surface.
Screen clearance alone is not permission to interrupt. Use real session/voice events;
do not populate the shipped UI with the prototype's fictional memories or work traces.

Implementation uses the existing native Java/API 29 stack: Canvas for the environment
and character, native widgets for controls and readable text. No WebView runtime or
new production dependency is introduced. The original prototype artwork is a temporary
built-in vector; external pet import remains a separate P01-P03 implementation gate.


The next M4 increment composes exact installed appearance versions and local voice
settings through one atomic active pointer. It pins the initialized Android TTS
engine, language, rate and pitch; it does not install/switch providers or alter DSH
identity, permissions or voice auto-send consent. See [local profiles](design/m4-profiles.md).

## M4.5 completion decision

The user inserted M4.5 before M5 to implement all applicable audited M0-M4 omissions.
Its [C01-C13 scope and F01-F14 gate](m4.5-completion.md) supersede the earlier narrow
UI shortlist, including client notification/history, share intake, same-session local
scheduling and overlay integration. Existing wake-validation deferrals stay explicit.
M5/M6 retain content/native release ownership; M7 owns backend companion integration
and the separately deferred voice completion. This is a plan change, not evidence
that those features already exist or authorization for incidental engine deployment.

## M4.5 automatic wake approval (2026-10-25)

The user approved a pinned sherpa-onnx dependency and specified the wake phrase
"嗨，<user-configured Agent name>". Current name: 银狼. This supersedes the earlier
wake-engine decision deferral. Initial integration uses the explicitly licensed
Chinese WenetSpeech model; unsupported names report an error. Wake implementation
belongs to M4.5, while actual V03/V04 evidence remains separately open. No DSH
change or second agent/session is authorized or required by this decision.

## Owner speaker verification (2026-10-25)

The user explicitly requires owner-only wake rather than generic wake sensitivity
calibration. Add local owner enrollment and speaker verification to M4.5 C10/C11.
Use the already approved sherpa-onnx runtime with separately pinned, licensed CAM++
and Silero VAD model content. Require a valid owner record before standby; reject
non-matching candidate speech silently before the audible cue or instruction capture.
Device-credential confirmation protects enrollment, replacement and deletion. Owner
voiceprints are encrypted locally and remain separate from the Agent name and skins.
Human owner/non-owner acceptance is required; generated fixtures do not establish it.

## Local native signing ownership (M6)

The local-only installation uses its existing developer-owned Termux keystore, kept outside source, artifacts and APK content. Build scripts may consume it for explicitly requested local builds; no key bytes/passwords are printed, copied to an agent prompt, or shipped. Native candidates must match this installed certificate and increase versionCode. Repair uses retained source to build a higher version. This decision does not define a shared key or public distribution model for other users.

## M7 local companion memory (2026-10-25)

The user approved dsh-mnemon as a maintained long-term dependency and its deployment.
Pin 0.5.34 with CLI 0.2.12. Reuse upstream workspace-local memory and DSH compaction;
disable Git sync and the independent idle-review agent. Keep original Silver Wolf
persona in workspace instructions, not a separate agent. Reflection reuses current
sessions and local schedules with a persisted one-submission-per-24-hours budget.
No embedding provider or external social account is configured. See
[M7 deployment and evidence](m7-companion-integration.md).

## Mandatory semantic memory (2026-10-25)

The user rejected keyword-only memory as a finished implementation and approved
Termux llama.cpp plus official Qwen3-Embedding-0.6B as production dependencies.
Keep inference on the phone. Reuse Mnemon vector/keyword/graph retrieval, backfill
existing records, and fail explicitly when semantic embedding fails. A small pinned
upstream patch adds query instructions and prevents silent fallback; it does not
replace Mnemon's retrieval engine. M7 stays open until its full requirements close.

## M8 local and remote speech (2026-10-25)

The user accepted CPUFast voice quality and authorized M8 implementation and
deployment. Reuse that pinned inference project; no additional candidate comparison
is needed. Preserve system speech and both existing local engines, while separating
provider, model and voice selection. Use OpenAI-compatible models/audio-speech
routes plus optional voice discovery and cancellation extensions. No automatic
fallback or uncertain-request replay. Remote credentials are device-owned, encrypted
and excluded from character exports. CPU throughput and phone RAM remain measured
constraints, not hidden by provider substitution.

## Explicit App control of independent DSH

The user reaffirmed App-to-DSH startup/control and DSH-Agent-to-App tools. Reuse
Termux 0.118.3 RUN_COMMAND with user-granted Android permission and its external-app
configuration. Expose fixed startup/status actions, keeping Agent device operations
on the existing MCP boundary. Session reconnect never implies service restart.
See [connection diagnostics](connection-diagnostics.md).
