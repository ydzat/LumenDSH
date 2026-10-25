# Architecture Baseline

Current checkpoint: usable Android 0.7.46 with DSH 0.2.1-rc.3; feature development
is paused. See [current status](project-status.md). The dated baseline below
describes responsibility boundaries and does not supersede later evidence.

Status: accepted responsibility baseline; independent phone build and M1 native
conversation client implemented. MCP status/notification/speech tools, operation
recovery, and artifact handoff are verified. M4 native habitat, pinned v2 appearances
and local profiles are implemented; background voice is a preview with open acoustic
acceptance, and M5/M6 content/native release loops are implemented with scoped evidence.
See [development entry](README.md) for contracts and verification gates.

## Process boundaries

The phone hosts three independently managed parts:

1. **Agent service:** the existing DSH deployment in Termux, responsible for agent execution and durable conversation state.
2. **Assistant client:** an Android application responsible for human interaction and Android lifecycle integration.
3. **Build environment:** an on-demand phone-side toolchain that produces the client APK without being packaged into it.

Model inference currently uses a cloud API. Local agent execution does not imply offline model inference.

## One agent, independent components

Daily assistance and self-improvement are capabilities of the same agent. They share identity, conversation, and memory. Build tasks can execute asynchronously and candidate artifacts can be staged separately without creating a second assistant or requiring a separate conversation.

The Android application supplies the interaction and device runtime. Appearance/voice/behavior packages are independently versioned content. A deployment/update module applies those packages, reports actual outcomes, and provides recovery. Build tooling produces artifacts without becoming part of the assistant APK. These are logical modules, not additional mandatory processes.

First-stage connectivity is phone-local only. Remote DSH and automatic remote-assisted onboarding remain future work.

## Responsibility allocation

| Concern | Proposed owner | Boundary |
| --- | --- | --- |
| Models, tools, workspace, task execution | DSH | Client submits requests and displays events; it does not recreate the agent loop |
| Conversations and task history | DSH | Client retains identifiers and reconstructs its view after reconnecting |
| Protocol, authentication, event translation | DSH adapter | Attach through the deployed session API; contain version-specific behavior |
| Character, animation, chat, task status | Android client | Render meaningful server and voice states |
| Wake word, microphone, speech recognition, speech synthesis | Android voice layer | Own microphone/audio focus and explicit listening controls |
| Background service, notifications, overlays, lock-screen interaction | Android platform layer | Operate within granted permissions and actual device capabilities |
| Tool approvals | DSH policy plus client approval UI | Preserve backend approval requirements; speech is not blanket authorization |
| Android actions requested by the agent | MCP device tool provider | Initial bounded status/show/speak/update capabilities; no unrestricted device control |
| Assistant packages | Client package loader | Appearance, voice settings, and behavior configuration have distinct compatibility contracts |
| Patch staging and activation | Update module | Verify base version and integrity; keep active and candidate artifacts distinct |
| Self-improvement planning and edits | The same DSH agent | Use normal conversation and tools; report verification results before claiming success |
| APK compilation and signing | Build tooling | Independent of client runtime and DSH lifecycle; signing material stays private |

## Connection and recovery

Initial target: the existing service at `http://127.0.0.1:3080` on the phone. This is a deployment detail, not a hard-coded protocol contract. Authentication and available DSH endpoints must be inspected before implementation.

Client restart or upgrade should not terminate the agent service. The client must recover the selected session and current task state. Request identifiers, retry behavior, cancellation, and event recovery require protocol verification before any delivery guarantees can be claimed.

The implemented M1 adapter uses HTTP RPC plus two WebSocket streams for host and
session events. It merges history by server sequence, persists session selection and
pending request identity before sending, and never automatically resubmits uncertain
delivery. Disconnect releases client resources only. See [M1 evidence](../tests/m1-evidence.md)
for tested recovery paths and remaining failure cases.

## Voice lifecycle

Proposed flow: disabled -> wake-word standby -> listening -> finalizing -> submitting -> awaiting reply -> speaking -> standby.

Continuous conversation after wake-up, interruption while speaking, and return-to-standby timing remain design decisions. Calls and competing microphone users require audio-focus handling. User-requested stop must stop listening. App termination is not equivalent to a user merely hiding its UI.

Foreground-service operation does not mean the application UI must remain visible. Neither an active DSH process nor a shell command automatically grants Android background-launch privileges.

## First integration milestone

Before character animation or always-on voice, validate one client conversation with the existing DSH:

- Open or select an existing session.
- Submit one message and receive the real response and task status.
- Display an approval request if the backend requires one.
- Disconnect and reconnect without losing the session or repeating the task.
- Restart the client while the DSH process remains running.

## Out of scope for the first milestone

- Migrating or replacing the existing DSH deployment.
- General autonomous phone control, remote-machine control, or virtual displays.
- Voice cloning and custom character asset production.
- Unattended self-installation of generated APKs.

## Self-improvement and releases

See [update lifecycle](updates.md). A patch is a change/distribution format, not a guarantee of live activation. Content may be reloadable; Android native changes require a compatible APK update. Preserve the agent session through client updates. A client restart can briefly interrupt voice even when DSH continues running.

## Contract boundaries

- [Session](contracts/session.md): app-to-DSH conversations and recovery.
- [Device tools](contracts/device-tools.md): DSH-to-app MCP capabilities, operation results, and artifact transfer.
- [Assistant packages](contracts/assistant-packages.md): composable content and external appearance import.
- [Updates](contracts/updates.md): local release records, activation, recovery, and APK identity.

## Local data ownership

DSH owns durable agent history and model/tool configuration. The app owns UI selection/cache, Android grants, voice lifecycle, installed content, pairing credentials, and update operation state. Build tools own candidate artifacts and private signing operations. Shared agent identity does not erase Android sandbox boundaries; use an explicit transfer channel for artifacts.

The app keeps device-tool pairing credentials separate from model credentials. Runtime secrets never enter assistant packages, public source, or conversation prompts. Voice recordings are not retained by default; any diagnostic capture is explicit and stored outside release content.

## M4.5 completion listener increment

The optional CompletionService owns a read-only subscription to the selected existing
session while a foreground-service notification is visible. It never submits prompts
or creates sessions. Activity voice and interactive session ownership are unchanged.
This separate observer is a bounded delivery implementation, not a second agent.
It suppresses initial historical completions, short turns, foreground notices and
repeat terminal sequences. Persist-before-post favors no duplicate notification over
guaranteed delivery after a crash; force-stop ends observation until the app is reopened (if enabled).
Notification history is app-private and retains the original session reference.
This does not implement background microphone, wake word, schedules or notification mining.

## M4.5 inbound shares

ShareInbox receives explicit Android share intents into a bounded private FIFO.
It never copies into public storage or automatically submits a prompt. Users review
text and file metadata, move text into the existing draft, or explicitly delete the
batch. Files remain inert until explicit confirmation releases a session-bound batch.
The authenticated MCP adapter copies bytes into private Termux storage and verifies
size/hash receipts before exposing generated paths to that same conversation.
The application uses a single Activity task so incoming shares reuse the interactive
session/voice owner rather than opening a second client screen.

## M4.5 local schedules

User-created schedules persist their existing DSH session identity and use Android
AlarmManager plus a short foreground submission service. The atomically consumed
occurrence is the replay fence; the per-task result distinguishes acceptance from
unknown delivery. No backend process management or implicit conversation creation
is allowed. Reopen/boot restores future alarms and records missed occurrences
without catch-up prompts. See [schedule evidence](../tests/m45-schedule-evidence.md)
for the declared lifetime and outstanding device checks.

## M4.5 hands-free preview ownership

`SharedDeviceBridge` gives the Activity and `WakeVoiceService` leases on one MCP/TTS
owner. Both modes reuse `VoiceReply` for server-RPC-to-completed-turn correlation.
The background service owns its recognizer only while the Activity is hidden; the
Activity pauses that service before starting manual recognition. The keyword worker
releases AudioRecord before invoking the recognition callback. Native inference is
serial and disposable; name changes rebuild the keyword engine because upstream
per-stream additional keywords merge with default keywords rather than replacing them.

`SessionController` synchronizes the shared pending-request record before sends and
reconciliation, and does not remove another controller's newer pending record. Wake
recognition never creates a session. Service process death retains the journal and
never causes a voice submission or playback retry. This preview does not restart the
microphone automatically after process death. Overlay management and audio lifetime
remain separate; hiding a character does not silently turn off opted-in standby.

## Owner voice identity

`OwnerVoiceStore` holds encrypted speaker embeddings independently of Agent name,
appearance and DSH identity. `OwnerVoiceActivity` protects enroll/replace/delete with
system device-credential confirmation. `OwnerEnrollment` captures bounded foreground
samples in memory; `OwnerSpeaker` delegates VAD, embeddings and comparisons to the
pinned upstream runtime/models. `WakeDetector` checks each candidate's bounded PCM
window before releasing the microphone to instruction recognition. Rejected candidates
produce no cue or task. The bridge reports counters only, not voiceprint data.

## Companion memory and reflection

DSH dsh-mnemon owns runtime entries and workspace-local durable memory. Android reads
through the authenticated gateway, resolving both session and workspace explicitly;
it does not duplicate the database. Original persona lives in workspace instructions.
The existing local scheduler submits bounded reflection to that same session; there
is no independent idle-review agent. Actual Mnemon events drive habitat indicators.
See [M7 deployment and limits](m7-companion-integration.md).
