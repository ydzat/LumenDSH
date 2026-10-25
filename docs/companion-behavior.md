# Personality, Memory and Proactive Behavior

Status: M7 companion implementation installed with dsh-mnemon 0.5.34 and
Mnemon CLI 0.2.12. Deferred acoustic acceptance remains open.
See [current implementation, provenance and gates](m7-companion-integration.md).
The earlier baseline mentions shared identity/memory and behavior profiles, but did
not specify a companion behavior implementation track. This document fills that gap.

## Intended experience

One agent retains a coherent persona, remembers supported shared experiences, initiates
appropriate activities, and exposes meaningful states in its habitat. Skin, voice,
personality and scene composition remain independently configurable. Daily conversation,
background activities and self-improvement share identity and memory; plugin defaults
that create a separate persona/session need evaluation rather than blind adoption.

## Inspected community references (2026-10-24)

README inspection establishes author-described capabilities only. This initial
research used rc.7; the phone now runs rc.3. Current plugin compatibility is tracked
in the M7 integration record, not inferred from these historical descriptions.

- [dsh-bili-whale](https://github.com/Alterssss/dsh-bili-whale), research revision
  `3e11454a5961300aa7094df0f08e630bd39999dd`: its README describes scheduled activity
  windows, video exploration, diaries, proactive sharing and persona configuration.
  It supplies activity mechanisms rather than a built-in personality. It is a reference
  for proactive companion behavior, not an approved installation or social posting setup.
- [dsh-whale-persona](https://github.com/linsport/dsh-whale-persona), research revision
  `3cf4ed81f22eca160bab7ce3343bce24b62c1ff0`: persona preset and documented integration
  with separate Mnemon memory components. A persona declaration alone does not implement
  memory storage, recall or autonomous scheduling. Its desktop embedding-service recipe
  is not a validated Android route.
- [dsh-dafeiyu](https://github.com/QCYTSN/dsh-dafeiyu), research revision
  `43542f8fc9567f45ce98534d407abca2ff4ff74e`: desktop companion presentation of actual
  session/task events. This is a distinct layer from persona and proactive scheduling.

A Bilibili tutorial was located at `https://www.bilibili.com/video/BV16KnN6KEwo`,
with the title about raising a DeepSeek fish that browses and replies on a schedule.
Its indexed description points to dsh-bili-whale. The video page was inaccessible
through the research tool; its full tutorial/video was not viewed. The capability
summary above comes from directly opened repository READMEs, not the video's title.
No code, persona text, assets or model configuration were imported.

## Development track

1. M4: define expressive content classes and source-aware scene behavior. Use clearly
   labeled design examples until actual plugin events exist.
2. Before backend integration: inspect candidate manifests, code, licenses and installed
   API compatibility; prefer existing suitable plugins. Resolve identity/session and
   memory ownership against the actual phone deployment.
3. Introduce persona and memory integration with observable capture/retrieval results.
   Separate factual history, attributed impressions, presets and fictional expression.
4. Add explicit proactive triggers, activity windows, resource budgets and a local
   pause control. Begin with local activities such as note/knowledge organization;
   social posting/account integration is a separate user-authorized scope.
5. Verify a real trigger, activity, memory write/recall and source-backed display in
   the same agent, including pause/restart behavior. Animation alone is not evidence
   of autonomous action or successful memory organization.

The precise plugin choice and acceptance gates remain pending compatibility discovery.
This track does not authorize replacing, upgrading or restarting the existing DSH
deployment as a side effect of UI work.

## Milestone ownership after the reuse audit

M4.5 completes source-aware client presentation, local schedules and notification
transport before M5. It does not install a persona/memory backend. M5/M6 cover local
content and native releases. M7 owns compatibility-tested backend persona, memory,
compaction-policy and proactive integration, reusing M4.5 client primitives; see
[the revised plan](development-plan.md) and [audit disposition](m4.5-completion.md).
The dsh-memory-sync reference names a mem.js absent from its checkout; this is not
an available complete memory service. Auto-summary configures context compaction,
not long-term autobiographical memory. Backend changes require their own deployment
scope and must preserve the existing agent/session.
