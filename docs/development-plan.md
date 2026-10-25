# Development Plan

Post-baseline approved work: 0.7.49 implements the [settings drawer and interaction
policy](design/settings-drawer.md). It does not reopen M0–M7 or imply new acoustic
acceptance. See [current status](project-status.md) for the installed version.


2026-10-25 handoff: development stops at the usable 0.7.46 baseline by user decision.
The user subsequently authorized [M8: local and remote voice providers](m8-voice-providers.md).
M8 is delivered with the resource limits recorded in its closure evidence.
See [current status](project-status.md). Historical milestone closure means delivery
of its recorded scope, not completion of every aspect of the product.

Current installation: Android `0.7.46-model-config`, DSH `0.2.1-rc.3`,
dsh-mnemon `0.5.34` and Mnemon CLI `0.2.12`. M0-M6 implementation and the M7
companion implementation are delivered. The old DSH runtime and compatibility branch
remain removed. See [M7 evidence and remaining acceptance](m7-companion-integration.md).
M7 is complete within [R01-R20](full-requirements-audit.md) and the recorded [C20-C25](m7-behavior-policy.md) scope. Local semantic retrieval is implemented and verified. Previously waived M4.5 human checks remain waived for closure, not passed. Historical milestone descriptions below retain their original evidence boundaries.

Status: M0 completed within the S01/B01 discovery gates on 2026-10-24.
See [M0 evidence](../tests/m0-evidence.md). M1 conversation baseline is implemented
and verified within the S02-S05 scopes in [M1 evidence](../tests/m1-evidence.md).
Receipt-loss fault injection and large-history recovery remain unverified. M2 passed
the exercised D01-D04 gates: authenticated MCP tools, explicit speech, operation
recovery, and bounded artifact handoff; see [M2 evidence](../tests/m2-evidence.md).
M3 foreground voice preview is built and installed; the user confirmed V01's
two-request recognition, auto-send, and audible-reply path.
See [voice policy](voice.md) and [preview evidence](../tests/m3-preview-evidence.md).
M3 background/wake lifecycle is implemented as an M4.5 preview with partial real-device evidence; see tests/m45-backend-evidence.md. At the user's direction,
remaining wake/call/background validation is deferred and no longer blocks M4.
M4 UI design and appearance work may proceed; M3 is not declared complete.
The original M0 backend lacked credential authentication. The explicitly authorized
2026-10-25 upgrade now uses authenticated DSH `0.2.1-rc.3`; see the
[new compatibility evidence](../integrations/dsh/compatibility-rc3.md).

## Ordered milestones

| Milestone | Deliverables | Exit gate |
| --- | --- | --- |
| M0: compatibility and build discovery | Deployed DSH capability matrix; pinned upstream candidates; build strategy decision; phone-side minimal APK build/install evidence | S01 and B01; no engine migration required |
| M1: conversation client | Select/resume session, submit text, events, approval/question UI, reconnect | S02-S05 against the actual phone DSH |
| M2: native capability bridge | Authenticated local MCP path or justified thin adapter; status/show/speak tools; operation state; bounded artifact handoff | D01-D04 |
| M3: voice lifecycle | Wake/STT/TTS selection and explicit stop; background/call handling; reconnect behavior | V01-V04 with real audio/device evidence |
| M4: product UI, appearance and profiles | Conversation/decision UI visual design; versioned manifests/schema; one Codex-compatible importer; renderer and state mapping | P01-P03 and UI01; second format only after first works |
| M4.5: reuse-based client completion | Complete audited M0-M4 omissions: habitat/config/state, notifications/history, share intake, same-session schedules, voice/overlay integration and visual closure | C01-C13 and F01-F14; affected S/D/V/P/UI regressions; user visual review; mandatory before M5 |
| M5: self-improvement release loop | Release M4.5 appearance/presentation/voice settings through the same agent; staged validation, content receipts, health checks and durable recovery | M4.5 complete, then U01-U04; preserve agent/session and user data |
| M6: native self-upgrade | Agent-driven phone build; linked source/DEX/APK receipts; payload/signature checks; private signing ownership; install and higher-version repair | M5 complete, then B02/U05 and native recovery evidence |
| M7: companion backend and deferred voice completion | Verified persona/memory/proactive integration, native compaction-policy reuse where compatible, and separately tracked deferred wake/call/background completion | Real same-session trigger, memory write/recall and pause/recovery; V03/V04 outstanding paths; explicit backend deployment scope |

Stages are gates, not independent applications. M0 build discovery proves feasibility; M6 completes the agent-driven release workflow. Do not substitute fake services or simulated transcript events for acceptance evidence.

User-authorized sequencing exception (2026-10-24): proceed to M4 without waiting
for the remaining M3 wake/background/lock-screen checks. V03 remains NOT RUN and
V04 remains PARTIAL. This changes scheduling, not capability or acceptance status.

## Product UI scope

The user refined M4 toward a voice-first, character-centered habitat with switchable
space/character compositions; the transcript is secondary. See the latest
[attention choreography habitat design](design/m4-attention.md). Personality, memory and
proactive behavior need a separate [integration track](companion-behavior.md), beginning
with compatibility discovery; a visual prototype does not establish those abilities.

The M1 native interface is an engineering validation surface, not the final visual design.
M4 includes the whole client presentation: layout, typography, colors, spacing, message
hierarchy, composer, session navigation, and clear approval/question states, as well as
character rendering. Appearance profiles must not make task controls hard to find.
Review the design with the user before implementing the visual overhaul; validate it
on the target phone with the keyboard open, long conversations, and pending decisions.
Earlier milestones may fix usability defects that prevent their acceptance, without
treating those fixes as completion of the visual design.

## M0 tasks in order

1. Inspect current phone/service status. If stopped, restore it only within the implementation task's authorized scope; do not upgrade its version.
2. Inspect the installed DSH packages and official source matching that build. Record methods, authentication, events, approvals, retry and cancellation behavior.
3. Pin source revisions for reused Android modules and toolchain components. Check source completeness and license boundaries before copying.
4. Resolve the native client/build approach based on a minimal phone-side build. Compare adapting the upstream Java build scripts with a standard Android build; choose the smallest reproducible option, not a framework by habit.
5. Build a tiny independent client APK with its own application identity; verify launch and authenticated local-service access. Keep prototype resources original and minimal.
6. Record limitations and the chosen approach in decisions/environment. Only then implement the full M1 adapter and UI.

## Implementation structure

`apps/android/` owns platform code, UI, voice, tool provider, package loading, and update activation. Keep these as logical modules initially; do not create many Gradle modules without a concrete benefit.

`integrations/dsh/` owns wire research and adapter contracts/fixtures. Runtime code may be an Android module or a small Termux-side adapter, depending on verified transport needs.

`tooling/android-build/` owns toolchain setup and reproducible commands. It must not depend on an installed third-party app's private directory.

`tests/` owns cross-component acceptance and device evidence conventions. Place unit tests near their implementation once the build system is chosen.

## Completion criteria for each change

- Cite the specific upstream contract/revision when relying on platform/library behavior.
- State which internal contract changed; preserve compatibility or record a version transition.
- Run the smallest relevant checks and affected device scenarios.
- Report real results, remaining gaps, and expected behavior after restart.
- Keep credentials, signing material, personal sessions, and raw audio out of commits and fixtures.
- After a substantial stage passes its scoped gate, run the milestone demo hook in
  `AGENTS.md`: capture a 5-10 second real-device clip into `artifacts/demos/` and link
  it in the delivery. Android `screenrecord` clips are silent; do not present them
  as acoustic evidence or upload them. Review the visible content and resulting clip.

Remote onboarding, generalized phone automation, full native hot-fixing, public package distribution, and voice cloning remain outside the first release.

## Current milestone status

M4 functional checks passed within P01-P03 and the exercised target-phone UI01 scope,
but visual fidelity is reopened after user review: 0.4.3 omitted approved ambient
motion and substantially changed control weights and composition. Functional tests
were insufficient to declare the visual milestone complete.
Version 0.4.4 (code 11) restores ambient motion and lighter visual hierarchy.
See [fidelity correction and real-motion evidence](../tests/m4-fidelity-evidence.md).
The prior functional baseline is recorded in
[consolidated stage evidence](../tests/m4-stage-evidence.md), including the corrected
large-font landscape layouts, actual approvals/questions, resource protection and
silent real-device demo. Companion plugins and extended asset formats are separate
follow-ups, not implemented capabilities of this baseline.

At the user's request, implement each major stage as a batch before consolidated
acceptance, phone build/install and demonstration. Necessary compiler checks may run
during implementation; do not deploy after each small edit. Stage-test failures are
fixed and the affected checks rerun without pausing for routine confirmation.

Current: M4.5 is complete by explicit user acceptance on 2026-10-25 (0.4.27). The latest visual direction is accepted; remaining M4.5 tests are waived for closure, not passed. M5 and M6 are now complete within their recorded scopes. Historical evidence retains its original limits.
The user inserted **M4.5 as a mandatory gate before M5**. Follow
[the complete work packages and audit disposition](m4.5-completion.md), rather than
the earlier three-item UI shortlist. The original gate required all C01-C13 deliverables and scoped verification. The user has now waived the remaining F01-F14 checks for closure; recorded partial/unverified evidence is unchanged. Audit documentation and a
successful build alone cannot satisfy the gate. Prior M0-M4 evidence stays valid
within its recorded scope and must not be rewritten as a pass for new behavior.

## Revised downstream scope

### M5: content release loop after M4.5

Use the completed presentation/source schemas and appearance/voice composition as
real release targets. Extract pure content-receipt and preflight helpers from
`release-receipts.mjs` and `BackupValidator.java`; replace fixed paths. Implement
release authenticity, stale-base rejection, durable operation state, safe activation,
health verification, retention and process-death recovery. Upstream BackupRestore
only handles ordinary errors and is not sufficient for U03. Exercise one same-agent
voice-setting release and one M4.5 presentation configuration release, plus
U02-U04 failures. Preserve selected appearance, conversation, schedules and user data.
Do not call manual profile creation or conversation fork/rewind release rollback.

### M6: native build, installation and repair

Build on M5 receipts and the existing verified independent Termux toolchain. Reuse
local APK payload checks and link source/dependency/resource hashes to DEX, signed
APK and verification evidence. Resolve private per-install signing ownership; never
place the key in agent prompts or APK content. Stage/install with Android's required
user action, then restore session/voice/overlay/schedule state and verify DSH survived.
Provide a higher-version repair path where Android disallows downgrade; test retained
data and operation status. No upstream publisher, public upload, base-APK/private-path
assumption or bundled build runtime is carried over.

### M7: companion backend and deferred voice work

User steering on 2026-10-25: first investigate current upstream DSH on the phone,
fix concrete Android incompatibilities in an isolated candidate, and assess a
recoverable migration plus client protocol adaptation. Then choose current memory
plugins. The upgrade to rc.3 is now complete and the old runtime was removed.
See [upgrade investigation](dsh-upgrade-investigation.md).

Follow [the companion track](companion-behavior.md) and
[M7 implementation and gates](m7-companion-integration.md): inspect actual rc.3 APIs and
complete dependencies before adapting persona/memory, auto-summary or proactive
plugins. Keep native context compaction distinct from long-term personal memory.
Reuse M4.5 schedules, notification transport and source-aware presentation; extend
them with real same-agent local activities, memory capture/recall, budgets and pause.
Do not create a second development agent/session. Backend installation/restart is
separately scoped, never a side effect of client work. The subsequent hands-free overlay request moves wake implementation into M4.5;
track V03 and remaining V04 evidence there before claiming that capability. In M7,
close any explicitly deferred validation when the user resumes it;
do not infer low-power wake from SpeechRecognizer polling. M7 cannot close while
those claimed product capabilities remain unverified.

Remote access, finance/clipboard monitoring, broad phone automation, optional rewind,
additional asset formats and public distribution are not silently added to these gates.


### M4.5 interaction gate update (0.4.26)

Apply the latest user-approved conversation-strip/icon/gesture direction from
`docs/design/m4-attention.md`, with F14 checks in `tests/acceptance.md`. The old
preset/card and near/far button requirements are superseded, not additional UI
implementations to maintain. M5 and later milestones preserve this single gesture
and conversation presentation path through updates and restoration.

User closure decision (2026-10-25): M4.5 is COMPLETE; M5/M6 subsequently completed their scoped gates. Remaining M4.5
tests are explicitly waived, not converted into passing evidence or new downstream
blocking requirements. M5 retains its own U01-U04 release and recovery checks.

M5 first increment (2026-10-25): presentation-only authenticated stage/activate/rollback is implemented in code 35; see [scope, reuse and evidence](m5-content-releases.md). U01-U04 are not yet complete.

## M5/M6 closure (2026-10-25)

M5 is COMPLETE within U01-U04: same-agent voice release, presentation/appearance profile release, validation rejection, actual client process-death recovery and runtime-health rollback. See [M5 evidence](m5-content-releases.md).

M6 is COMPLETE within B02/U05 and the explicit higher-version repair path: real same-agent phone build, source/dependency/DEX/APK receipts, private existing signing identity, authenticated APK transfer, system-confirmed upgrades 36 -> 38 -> 39, preserved client data/owner decryption and a DSH task that survived client replacement. See [M6 evidence](m6-native-releases.md). Installed client is code 39. All publication remained local.

M7 is the next milestone. No persona/memory backend plugin installation, DSH upgrade/restart, remote onboarding or previously waived wake acceptance was included in this closure.

## Full implementation requirement (2026-10-25)

The user rejected reduced implementations as milestone delivery. Semantic search is
mandatory for long-term recall, not an optional enhancement. Complete ingestion,
index backfill, same-session tool retrieval, deletion, service recovery, explicit
failure and visible coverage are part of this capability. See
[semantic evidence](m7-semantic-memory.md). Do not close M7 based only on a working
subset: proactive scheduling policy and broader memory quality still require their
full acceptance evidence. Previously waived human tests remain waived, not passed.

Use [R01-R20](full-requirements-audit.md) as the reconciled completion checklist.
The next batch reuses Whale's pure activity planner and native Mnemon tools in the
selected DSH session. Its host policy owns autonomous windows, inactivity, pause
and execution budgets; Android's existing scheduler remains for explicit reminders.
See [C20-C25](m7-behavior-policy.md) for the integration gate. Retired UI controls,
removed preset producers and the unconnected calendar prototype are not restored.

## M7 closure (2026-10-25)

Subsequent user-requested model adaptation is recorded in
[model configuration and C26](model-configuration.md): follow native session model
selection, refresh capabilities, and resolve autonomous output against each actual
request without duplicating native context management.

Client 0.7.45 and companion host 0.2.0 complete the reconciled phone-local scope:
versioned behavior controls, reused Whale activity planning, bounded same-Agent
maintenance, semantic native memory/Documents, source-backed presentation and
composition import. C20-C25 passed their recorded real integration and visual
checks. Native compaction preserved durable documents; crash recovery did not
replay reservations; human handover retained the exact request and fresh per-turn
memory quotas. The original selected session was restored after verification.

This closure does not assert large-library throughput, weeks of personality
consistency or previously waived acoustic/lifecycle tests. The measured long-input
suite and native source timeout are recorded in the evidence. Autonomous activity
remains disabled until configured through the installed controls. No remote access,
social posting, custom voice cloning or publication is included.

## M8 closure (2026-10-25)

Android 0.8.0 and the independent CPUFast provider implement the local/remote
speech milestone. [S01-S08 evidence](../tests/m8-evidence.md) records actual
catalogs, playback, foreground/background reply ownership, cancellation and errors.
System TTS and local hoyoTTS played successfully. Local GPT-SoVITS selection and
memory rejection were verified; its playback did not pass under current available
RAM. CPU server throughput can cause gaps between long-reply segments. These
resource limits remain explicit. No further milestone is scheduled.
