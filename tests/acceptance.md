# Acceptance Scenarios

C26 model configuration: see [native model selection, automatic refresh and
output-budget evidence](../docs/model-configuration.md). This addition does not
upgrade previously waived or unexecuted scenarios.

Current execution scope: Android 0.7.45, DSH 0.2.1-rc.3. M4.5 is closed by the explicit waiver below; M5/M6 have completed their recorded gates. M7 follow-up results are in [C20-C25](../docs/m7-behavior-policy.md). Earlier unexecuted status text below describes its historical baseline and does not override those later results.

S01 and B01 passed their M0 discovery/build scope on 2026-10-24; see
[execution evidence and limitations](m0-evidence.md). S02-S05 passed the exercised M1
paths on the same date; see [M1 evidence](m1-evidence.md) for their precise scope.
S03 receipt-loss fault injection remains NOT RUN. D01-D04 passed the exercised M2
tool/authentication, speech recovery, and artifact handoff scopes; see [M2 evidence](m2-evidence.md).
V01 passed the user-confirmed foreground two-request scope; see
[M3 preview evidence](m3-preview-evidence.md). V02 passed the tap-interruption/local-stop
scope; V04's TTS audio-focus subset passed, with remaining V04 paths unverified; see
[interruption evidence](m3-interruption-evidence.md). V03 has failed initial spoken wake trials and remains open; U01-U05 and B02 remain NOT RUN. P01-P03 passed their exercised M4 scopes (see [stage evidence](m4-stage-evidence.md)); UI01 visual fidelity remains open despite functional/layout checks. M4.5 F03/F04 are PARTIAL (see [card evidence](m45-card-evidence.md)); F02/F05/F06/F07 are PARTIAL within [state/settings evidence](m45-state-settings-evidence.md); other F cases are NOT RUN.
Earlier browser/DSH tests do not count as client acceptance.

For each execution record: case ID, date, source revision, APK/build hash, DSH version, device/OS, preconditions, procedure, expected/actual result, and pass/fail/blocked. Store private raw artifacts under ignored `artifacts/`; commit only sanitized evidence that is necessary and authorized.

| ID | Scenario | Pass condition |
| --- | --- | --- |
| S01 | Inspect actual DSH capability/auth surface | Version and operations documented from deployed code/live observation, with unsupported features explicit |
| S02 | Send a real prompt in an existing test session | One accepted user message, streamed response, correct terminal state and retained session ID |
| S03 | Disconnect after submission and reconnect | No duplicate task; history reconciles; uncertain delivery is surfaced when unsupported |
| S04 | Backend approval/question and cancellation | Correct decision identity/answer; unsupported paths explicit; no silently bypassed approval |
| S05 | Restart/close client during a DSH task | DSH remains alive; same task/session recover; no shutdown request |
| D01 | MCP discovery and one actual phone action | Only implemented tools advertised; result reflects real Android outcome |
| D02 | Missing/invalid authentication or Android permission | Explicit refusal; no side effect or leaked secret |
| D03 | Retry operation or restart app mid-operation | No duplicate activation/playback; completion vs acceptance correctly distinguished |
| D04 | Transfer a test artifact from Termux to App | Bounded authenticated handoff works without assuming shared private directories |
| V01 | Speak two consecutive requests | Auto-send works as configured; both use the same conversation; answer spoken once |
| V02 | Speak during playback and explicitly stop listening | Selected interruption policy works; stop works without DSH/model availability |
| V03 | Background and locked-screen wake | Real wake works after 5 and 30 minutes; report 10 spaced wake trials per state, observed misses/latency and battery delta; product battery target remains to be set |
| V04 | Incoming call/audio conflict and backend loss | Assistant yields microphone/audio and reports disconnected state; recovery is explicit and does not duplicate queued speech |
| P01 | Import a pinned compatible pet pack | Supported states render; missing state uses documented fallback; same conversation persists |
| P02 | Invalid/oversized/traversing resource archive | Reject before activation; no files escape staging and old appearance still works |
| P03 | Switch combined appearance/voice profile | All referenced components validate; partial failure keeps previous profile; no voice/model identity surprise |
| UI01 | Product UI on the target phone | User-reviewed visual design; readable message hierarchy and status; usable composer/navigation with keyboard open; approvals/questions remain legible and actionable; character presentation does not cover task controls |
| U01 | Same agent changes its voice parameter and publishes a patch | Real candidate generated, validated, activated, and observable in the same conversation |
| U02 | Stale base, modified payload, or incompatible release | Explicit rejection; active version unchanged |
| U03 | App dies during staging or activation | Next start recovers a consistent working version and accurate operation state |
| U04 | New content fails health check | Last working content restored; user data and DSH session retained |
| U05 | Real in-place APK update | Compatible signature; app data/session retained; DSH task survives; actual voice interruption documented |
| B01 | Minimal APK built on phone | Source/toolchain revisions and build command recorded; signed artifact launches independently of upstream app runtime |
| B02 | Agent-driven native build | Same agent invokes independent build tools; signed artifact verified; private key never enters prompts/logs |

Mocks can test pure parsing/state logic later, but cannot satisfy these integration/device cases. A displayed button, successful package download, or HTTP 200 alone is not end-to-end proof.

## M4.5 completion gate

F03/F04 are **PARTIAL** within [card evidence](m45-card-evidence.md); F02/F05/F06/F07 are also **PARTIAL** within [state/settings evidence](m45-state-settings-evidence.md); other cases below are **NOT RUN**. Scope and reuse sources are defined in
[work packages C01-C13](../docs/m4.5-completion.md). Pure state/parser tests may
support them but cannot replace specified phone/backend observations.

| ID | Scenario | Pass condition |
| --- | --- | --- |
| F01 | Installed event/capability mapping | Actual rc.7 source and controlled live events establish deltas, terminal reasons and supported submission paths; absent signals explicitly stay unknown; no backend changes incidental to discovery |
| F02 | Real state progression and reconciliation | Exercise actual work/output/completion, failure/cancel and approval; visible state precedence and completion hold match policy; reconnect does not duplicate content or completion effects; no fake reasoning |
| F03 | Conversation strip and expansion | Current near/far scale and target orientations keep the strip below the entire character; idle fade, tap-to-reveal and icon expansion into the same editable/copyable conversation work on phone |
| F04 | Streaming while reading | Actual growing reply follows tail only when already at bottom and not touched; scrolling up/pinning keeps reading position; listening/approval preempts correctly; speech and completed text are not duplicated |
| F05 | Presentation settings and migration | Change name/style/size/pacing/idle-hide interval, preserve legacy preset fields, restart, switch imported/default profiles; exact settings restored with voice/session unchanged; invalid/oversized settings retain working version; existing profile migration verified |
| F06 | Character lifecycle | Tap expression/feedback, one explicit foreground voice control, sparse idle and visual rest/wake work; hidden/disabled/reduced-motion state stops appropriate animation; listening/reading/decisions suppress competing gestures; backend task continues during visual rest |
| F07 | Source-aware content and occupancy | Distinguish actual conversation, status and notification details; chronological transcript and occupied-space rules preserve reading under arrivals; no fabricated memory/thought content; contextual actions invoke only supported operations |
| F08 | Automatic completion notification and history | Real completed task yields correct final excerpt and selected-session return; short-turn/cooldown/foreground suppression work; history bounded and persistent; reconnect/restart no duplicate; test background delivery for the declared listener lifetime |
| F09 | Android share intake | Share controlled text and file, review then send into same session; real authenticated attachment transfer if needed; cancel sends nothing; oversized/unreadable URI and interrupted staging preserve old data and report outcome |
| F10 | Same-session local schedule | Create/enable/pause/cancel and restart; actual Android-triggered delivery uses selected existing session; background/process-loss behavior matches declared policy; offline/missing session and ambiguous acceptance never silently create or duplicate a task |
| F11 | Unified voice ownership | Habitat and overlay controls use one recognizer/TTS owner; existing opt-in honored; actual two-request continuity and stop/interruption work; draft does not auto-submit; affected audio-focus/callback paths verified |
| F12 | Overlay lifecycle | Denied/revoked permission handled; enable/drag/dock/hide/recall/disable work on phone; Activity foreground avoids duplicate UI; keyboard/rotation safe; stop remains available; disabled service releases resources; no unsupported lock-screen/wake claims |
| F13 | Reviewed visual and accessibility closure | Real phone comparison to approved habitat design, both compositions, large font, keyboard, long content and real decisions; controls accessible; user visual review closes known mismatch; protocol success alone insufficient |
| F14 | Integrated upgrade and provenance | Each work package has source/pin/license/adaptation record; consolidated phone build and in-place upgrade preserve session, profile, staged data and schedules; scoped regressions recorded; inspected 5-10 second controlled demo linked |

M4.5 cannot close with F cases merely documented or pending. V03 and previously
deferred V04 wake/call/background paths remain separately open; F11/F12 do not
substitute for them. Existing case IDs are retained; no prior pass is broadened.

## Downstream extensions

M7 C14-C19 scenarios and current statuses are specified in
[the companion integration gate](../docs/m7-companion-integration.md#consolidated-gate).
They cover native-engine loading, write/readback/recall, existing-session persona,
local activity pause/recovery, real memory UI and grounded correction/forgetting.

- M5 U01 additionally exercises one M4.5 presentation/preset-content release,
  alongside the original voice-setting change, in the same agent conversation.
  U02-U04 include schema compatibility, durable recovery and preservation of shared
  settings/schedules. Hash receipts do not replace authenticated provenance.
- M6 B02/U05 link source/dependencies/resources, DEX, signed APK and executed checks;
  verify interrupted install/restart status and a compatible higher-version repair.
- M7 companion acceptance must be specified before implementation: actual persona
  application, memory write/readback/recall, scheduled same-agent activity and pause,
  with real evidence. Outstanding V03/V04 retain their original device requirements.

M4.5 C07/C08 increment (2026-10-25): F08 is PARTIAL with actual background
completion/history evidence in [notification checks](m45-notification-evidence.md).
F09 is PARTIAL with real text share/review/private persistence evidence in
[share checks](m45-share-evidence.md); file delivery remains unimplemented.

F10 is PARTIAL: actual Android background one-shot submission reached the original
conversation and returned the controlled reply; store replay/cancellation checks
passed. See [schedule evidence](m45-schedule-evidence.md) for untested lifecycle cases.

## Owner-bound voice gate (M4.5)

| ID | Scenario | Required outcome |
| --- | --- | --- |
| V05 | Owner enrollment, rejection, lifecycle and privacy | No automatic wake before enrollment; device authentication required to enroll/replace/delete; cancellation preserves old record; actual owner wake trials and a consenting non-owner's same-phrase trials report accepted/rejected counts, noise/distance and latency; non-owner rejection makes no cue or submission; name/skin changes preserve owner; no saved/uploaded raw audio; corrupt/missing voiceprint refuses standby |

V05 owner wake trials have failed (speaker mismatch); non-owner human acceptance
is NOT RUN. Short-phrase calibration is implemented but awaits actual owner
enrollment and retest. Native models, public-fixture
comparisons and encrypted-storage checks are separate lower-level evidence. Replay
and synthetic-voice liveness protection is not implemented and must not be claimed.


### F14: unified habitat gestures and icon actions

Verify body-region single taps submit one real user message to the existing session;
while busy, the original task completes before the touch is consumed. Pinch changes
scale without submission; double tap, outside taps and cancellation submit nothing.
Menu/expand/voice/back/send icons retain readable accessibility names and usable
targets. Expand opens the same conversation with text entry and copy selection.
Retired preset and bottom-navigation actions must not remain as parallel paths.
Scoped evidence: [interaction revision](m45-interaction-evidence.md). F13 visual
acceptance remains a separate user review.


## M4.5 closure override (2026-10-25)

The user accepted current visuals and explicitly closed M4.5 without the remaining
tests. Remaining M4.5 cases are WAIVED FOR CLOSURE / NOT VERIFIED; earlier scoped
passes remain valid. This overrides old blocking status text, not empirical results.
Do not repeat these waived tests as a prerequisite for M5. U01-U04 are separate
M5 release-integrity and recovery checks and remain in scope.

## M5/M6 closure evidence (2026-10-25)

U01-U04 are complete within [the M5 evidence](../docs/m5-content-releases.md): real same-agent voice candidate/activation/rollback, actual Android validation rejections, genuine client process termination during verification, and actual invalid-TTS-engine health failure/recovery. Injected journal/renderer checks remain labeled separately.

B02/U05 are complete within [the M6 evidence](../docs/m6-native-releases.md): the existing agent invoked the independent phone build; the real PackageInstaller path updated 36 -> 38 -> 39 with matching signing identity and installed APK hashes. A DSH command task survived the first update, and selected session/settings/schedules/owner decryption survived both. Voice was idle before replacement; the client process/audio services were interrupted until reopening, not acoustically continuous. The higher-version repair was built from verified retained source and installed, without deliberately installing broken native code.

M4.5 closure waivers remain unchanged. No new acoustic owner/wake/call acceptance is implied.

## Mandatory semantic memory follow-up

The keyword-only M7 baseline is superseded. See
[real semantic retrieval evidence](../docs/m7-semantic-memory.md): C15/C18 now include
local embeddings, existing-record backfill, real same-session smart recall and visible
coverage; C19 includes deletion from semantic results and explicit unavailable-service
failure. The four-case controlled ranking suite does not close broader quality gates.

The full-requirements follow-up uses [C20-C25](../docs/m7-behavior-policy.md#consolidated-gate)
for behavior recovery/authentication, same-session activity, hard execution budgets,
identity synchronization, sourced memory presentation and phone inference lifetime.
Keep native-hook observation separate from a successful model/memory operation.

## M8 speech providers

S01-S08 are defined in [the M8 milestone](../docs/m8-voice-providers.md).
See [executed evidence and resource limitations](m8-evidence.md). The local
GPT-SoVITS resource-rejection result is not a successful-playback result.
Previous waived acoustic tests retain their prior status.
