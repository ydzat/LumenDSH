# Full requirements and reuse audit

Status: audited implementation installed in client 0.7.45 and host 0.2.0, 2026-10-25. This document reconciles the
latest user decisions with code and evidence; it does not reopen waived acceptance
or treat old prototype text as an additional conflicting feature requirement.

## Authoritative order

1. Latest user decisions (same Agent, no preset touch dialogue, icon actions, pinch,
   foreground transcript strip, local-only operation, full semantic memory).
2. `development-plan.md`, current M4.5 closure override, M5/M6 evidence, and contracts.
3. The final sections of `design/m4-attention.md`; older UI/habitat/depth prototypes
   explain intent but do not restore removed buttons, preset producers or bubbles.
4. `companion-behavior.md` and `design/m4-semantic-space.md` for real memory,
   authored expression, activity windows, budgets, pause and source provenance.

## Required capability map

| ID | Requirement and source | Current owner / state | Completion work and reuse |
| --- | --- | --- | --- |
| R01 | Independent phone-local DSH, one identity/session/memory; architecture and session contract | rc.3 adapter, independent Termux runtime; implemented | Preserve these invariants in every new module; never install a second conversation-owning scheduler |
| R02 | Authenticated sessions, approvals/questions, no duplicate uncertain delivery | DshTransport/SessionController; implemented with scoped evidence | New companion routes reuse DSH admission; no homemade auth or host process ownership in APK |
| R03 | MCP native tools and authenticated artifact/share boundaries | DeviceTools/ShareInbox/MCP adapter; implemented | Preserve operation receipts and explicit share review; old statements that file shares are absent are historical |
| R04 | Voice-first habitat, both compositions, motion and attention/occupancy | HabitatView/AttentionQueue, accepted latest design | Preserve gestures, auto-hiding foreground strip and icon controls; do not reintroduce removed presets or duplicate voice actions |
| R05 | One validated Codex pet format and independent voice/appearance settings | PetStore/PetSprite/AssistantProfile; implemented | No universal pet-format, mouth animation or custom Hanser voice claim |
| R06 | Versioned behavior configuration separate from skin/voice | Versioned host policy and Android controls installed; authenticated restore verified | Implemented with the existing settings/immutable-version patterns; same-session application, readback and recovery verified |
| R07 | Agent name remains consistent across UI, wake phrase and backend identity | Existing DeviceBridge synchronizes name/session; backend prompt inspected | Synchronize current configured identity without replacing conversations, permissions or user instructions |
| R08 | Owner-gated background voice, conservative wake-sample learning | Existing wake/speaker pipeline; implementation and scoped user success | Preserve it; waived human acoustic/lifecycle tests stay unverified, not falsely absent or passed |
| R09 | Notifications, share intake, schedules, overlay | Existing adapted native modules; implemented | Reuse instead of adding a parallel Android scheduler or audio owner |
| R10 | Same-agent content and native self-upgrade with recovery | M5/M6 delivered within recorded gates | New config must participate in validation/recovery; no public publishing or bundled toolchain |
| R11 | Semantic long-term memory | Mnemon + Qwen local model, real smart recall; implemented | Keep genuine vectors, backfill, deletion and explicit failure; extend realistic retrieval/correction cases |
| R12 | Capture, consolidate, selectively recall, preserve historical evidence | Mnemon layers/auto-capture and DSH compaction exist; same-session automatic consolidation completed with a real Document commit | Reuse native memory tools, revision/provenance and lifecycle. Add same-session periodic maintenance with persisted progress and real results; no new memory database |
| R13 | Memory types: shared experiences, knowledge, attributed observations/reflections | Native memory source plus sourced Documents; behavior instructions distinguish experiences, knowledge and attributed observations | Reuse Whale's separation of experience/knowledge/observations through Mnemon spaces/documents and source metadata, retaining one identity |
| R14 | Autonomous local activities, activity windows and inactivity/context awareness | Whale planner integrated with selected-session native delivery; real run completed | Reuse Whale night-plan planner and current DSH session controller; bind the user's selected existing session, local activities only |
| R15 | Pause and resource budgets enforced by code | Scoped budgets, persisted reservations, actual pause/step/time exits and human handover verified | Native next-turn handover preserves exact user request identity and fresh memory quotas; interrupted reservations do not replay |
| R16 | Real memory/association/diary fragments in habitat | Native activity excerpts and source IDs projected; source document readback verified | Reuse Mnemon activity metadata and native AttentionQueue source handling; show actual excerpts/reference/time, never hidden reasoning or fabricated memories |
| R17 | Context compression distinct from durable personal memory | DSH native compaction retained; actual compact/semantic source expansion passed | Reuse the current native engine; no older threshold adapter is needed. Do not install Magic Context's incompatible host adapters or claim identical behavior |
| R18 | Phone memory/runtime lifetime | Local model works; 2048-token configuration with native idle unload; actual sleep/wake measured | Reuse llama.cpp's native context/idle-unload facilities, measure recovery; do not build another inference engine |
| R19 | Inspectable user controls and outcomes | Installed policy UI, actual history and document body/provenance controls | Implemented through existing settings/secondary pages; actual Android source readback and policy save verified |
| R20 | Complete reproducibility and evidence | Current install scripts, pins and demos exist | Consolidated batch build, real selected-session tests, corruption/restart/budget scenarios and reviewed silent demo; no simulated integration passes |

## Upstream source inspected for this batch

- Android reference `a5af7b105b7fdb006e3fc3be2a2179d83907c2cb`: existing Java
  scheduling, presentation and release extractions remain authoritative. Reuse its
  authenticated auto-summary status and native-engine policy pattern, not bundled DSH.
- `Alterssss/dsh-bili-whale` `8b0009f8ce2ebdc421afcb2e727b19ac18912ec6`, MIT:
  `tools/lib/night-plan.mjs` implements persisted randomized windows, dated counts,
  midnight/DST handling, skip choice and minimum gap. Extract it with its notice.
  `lib/scheduler.js` demonstrates host lifecycle; `session-delivery.js` creates/rotates
  dedicated sessions and is incompatible with our selected-session requirement.
  Do not import Bilibili login, messages, social writes or network polling.
- `linsport/dsh-whale-persona` `4f884899df629b88c331e96151225dc825c15c96`:
  memory-space organization and semantic capture/recall design; keep original project
  persona text, not copied character biography or a new dedicated persona session.
- `cortexkit/magic-context` `8dd1bd1c7988bc55c98525db1db3dfc9f05bc8eb`, MIT:
  current README and architecture separate capture, consolidation and recall. Its
  historian/dreamer adapters target OpenCode/Pi/OMP and disable host compaction.
  Do not install those into DSH or promise its marketing claims; implement the
  relevant lifecycle using DSH/Mnemon's existing contracts, without a second manager.
- dsh-mnemon `0.5.34`: `activity.ts`, `activity-presentation.ts`, source-memory-spaces,
  source-documents, auto-capture and light-context strategies, and guarded review
  hooks. Existing native storage/merge/recall is reused. Default idle review spawns
  child work; our autonomous execution remains in the selected conversation.
- Installed DSH rc.3 type/source snapshots: sessionController resolve/list/prompt,
  agent pre-step/request/turn-stopping and scoped tools guards. Verify on phone before
  treating a declaration as a working integration.

## Explicit exclusions and corrections

Calendar event integration was labeled unconnected in the prototype, not included in
the agreed first-phase capability set. Time/date is already local. Social accounts,
external posting, remote DSH, broad phone automation, voice cloning and additional
pet formats remain separate scope. Full implementation does not authorize them.
A new external composition importer is distinct from the existing local composition
editor; do not misrepresent the editor as a universal import implementation.

The earlier gap list overstated absence of upstream consolidation capabilities and
implied a full Magic Context port was mandatory. The actual gap is a working,
source-backed lifecycle in this DSH integration. Prefer connecting and verifying
those capabilities over adding another classifier, database or background Agent.

## Batch order

1. Reuse planner and native lifecycle for versioned companion policy, budget/pause,
   selected-session identity and local maintenance; expose user controls.
2. Connect real memory metadata and source navigation; complete memory organization
   and current-agent maintenance/retrieval behavior.
3. Apply native embedding lifetime/context facilities and focused quality cases.
4. Consolidated build/install, real integration evidence and demo; retain open items
   explicitly until they are actually complete. No stage closure from this audit alone.


## Latest evidence and remaining verification limits

The 0.7.45 client and host 0.2.0 increment are installed. See
[m7-behavior-policy.md](m7-behavior-policy.md) for actual build, API, scheduling,
semantic Documents, storage-preservation and silent-demo evidence. Native compaction
continuity, reservation crash recovery, step/time budgets, exact-once human handover,
unrelated-query rejection and pinned-grant/passages checks have passed. External
composition import is installed and its real system-picker preview was inspected.
A 3223-token document was retrieved using the retained 2048-token model context.

The actual phone habitat now also has reviewed live memory-excerpt/source-ID
evidence. M7 is complete within R01-R20's audited phone-local scope.
Remaining evidence limits are large-library throughput and long-duration personality
quality. Previously waived human acoustic/lifecycle checks remain
waived and unverified. These are not missing implementations or passing tests;
M7's acceptance record retains these limits rather than silently upgrading them.
