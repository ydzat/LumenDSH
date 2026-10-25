# M4 Semantic Habitat Review 03

Status: proposed interaction model and browser prototype, not deployed functionality.
The subsequent [depth and occupancy review](m4-depth-scheduling.md) replaces the
single floating-text treatment with separate renderers and measured placement.
Open [the interactive preview](m4-habitat-semantic-preview.html). Review 02 is preserved.

## Information belongs to the space

The user clarified that most information should appear directly in the background.
Cards are one presentation form, primarily for deliberate interaction. Select form by
content meaning and importance, not by wrapping every message in the same container.

| Content | Proposed presentation | Lifetime / ownership |
| --- | --- | --- |
| Ambient facts: time, calendar | Quiet, anchored typography | Clock is local; calendar needs a real integration |
| Agent speech | Clear foreground text, unboxed and synchronized to speech | Current utterance, with transcript recall |
| Expressive aside | Light, peripheral text emerging through blur/opacity | Explicit user-facing persona expression |
| Retrieved memory | Dated/source-linked fragment, slower appearance | Real retrieval with provenance; not decorative invented history |
| Associations / reasoning summary | Brief connective phrases or related fragments | User-facing explanation, not a claim to complete private reasoning |
| Search and code activity | Queries, source titles, short code/diff excerpts | Actual tool events and results, bounded and selectable |
| Approval / warning requiring action | Stable high-contrast card | Remains until handled; never automatically fades |

The current prototype includes unboxed asides, speech, illustrative memories and code,
a local clock, and an explicit decision card. Calendar remains labeled unconnected.
All agent text and task states are illustrative. Pinning applies to ephemeral text;
real pinned objects will keep their source identity instead of becoming new memories.

## Atmosphere follows state

Proposed scenes: quiet presence, listening, focused work, searching, memory recall,
warning, and model-provider disconnection. Motion, light, accent color and information
density vary together. Actual implementation should combine task, attention, connection
and persona-expression states rather than force them into one mutually exclusive enum.

Warnings derive from known operational conditions or explicit task policy, not a
guess from emotionally charged text. Distinguish phone connectivity, client-to-DSH,
and DSH-to-provider failure. Provider unreachability does not prove the phone is offline.
Unknown state must remain unknown. An expression preset cannot fabricate a task outcome.
Keep words/symbols alongside color and honor reduced motion.

An expressive aside may resemble an inner monologue, but its data source is an
explicitly authored display utterance. Show search provenance and concise reasoning
summaries where available; do not invent a chain of thought to fill the background or
promise access to every model's internal reasoning. Preset phrases remain distinct from
fresh model output, factual memories, and actual task telemetry.

## Plugin connection

The space presents the same DSH agent's personality, memory and proactive activities.
It does not run a second identity. See [companion research and plan](../companion-behavior.md).
Future display events should carry source/type, session and event identity, text or
artifact reference, priority, persistence, timestamp and provenance. DSH APIs supply
real task history; explicit phone presentation requests may use the MCP boundary.
This is proposed vocabulary, not an implemented new endpoint or tool contract.

## Review evidence

Headless Edge executed 14 assertions across seven scene states, four unboxed content
types, shared pinning/layout switching, decision-card presence and decision feedback.
The search-state composition was rendered and inspected. These checks validate only
the local design prototype. No DSH plugin was installed and no social account was used.
