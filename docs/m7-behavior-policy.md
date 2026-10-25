# Companion policy and source-backed presentation

Status: M7 complete within the audited phone-local scope, 2026-10-25. Evidence limits are
listed below; implementation does not imply every acceptance scenario passed.
Use the [full requirement audit](full-requirements-audit.md).

## Reuse and execution contract

The unchanged MIT Whale `night-plan.mjs` supplies calendar windows, random
reservations, skip decisions, minimum gaps and dated counts. Revision and license
are retained in `integrations/companion/upstream/`. The adapter uses the existing
selected DSH session, never Whale's separate-session or Bilibili account mechanisms.

The host plugin uses rc.3 sessionController, pre-step/request hooks and scoped tool
guards. Route admission reuses DSH's cookie/Host/Origin validation. It runs inside
the existing host, without another background Agent or memory database.

- Schema 1 behavior configuration separates interaction style and activity policy
  from appearance/voice. Validated immutable hash versions precede atomic pointer
  changes. Restore verifies hashes; stale edits reject.
- Android presentation settings own the display/wake name. The existing device
  bridge synchronizes name and selected session every minute while running. It
  records sync errors instead of silently declaring success. Backend preferences
  augment the native prompt; user workspace instructions and history are preserved.
- Persisted reservations precede delivery. Unknown receipts and interrupted runs
  are not automatically replayed. Window/inactivity/daily limits gate submissions.
- Step/tool/write/time limits and per-step output caps apply to the attributed
  autonomous turn. Write counts are admitted attempts, not successful-write claims.
  Arbitrary shell/social/subagent tools and generic mutation dispatch are excluded.
- Human input relinquishes the autonomous budget, and cancellation preserves its
  pending inbox. Ordinary user work keeps native tool permissions and approvals.
- Consolidation uses actual Mnemon reads and non-destructive writes. Shared
  experience, knowledge and attributed observation remain distinguishable, with
  source/date references. No useful evidence means no write.
- Android's existing explicit reminder scheduler remains; it is not duplicated.

The secondary client page edits windows, activities, style and limits, displays run
outcomes and restores versions. Habitat projection follows Mnemon's activity schema
and call/result correlation; actual excerpts carry source IDs. Unknown/unmatched
metadata and private reasoning never create memory fragments.

## Consolidated gate

| ID | Required observation | Current evidence |
| --- | --- | --- |
| C20 | Invalid/stale policy rejection, hash recovery and authentication | Four local tests and real isolated DSH auth/history checks passed |
| C21 | Existing-session scheduled work and no duplicate restart delivery | Authenticated host completed 3 steps and 3 tools in the existing controlled session; actual Document commit observed; actual isolated reservation SIGKILL/restart preserved the spent budget without replay |
| C22 | Pause and execution budgets; human work unaffected | Isolated pause and actual step/time-budget exits passed with zero writes; actual steering handover preserved the original request exactly once and both native memory tools ran without inherited quota exhaustion |
| C23 | Name/style in same-session prompt; real client policy controls | Configured-name instructions found in actual persisted system message; Android 0.7.45 installed; real settings page inspected and save completed |
| C24 | Real memory fragments and source navigation | Android projection and real document body/provenance readback passed; reviewed actual phone video shows the live recall excerpt and source ID behind the character |
| C25 | Embedding idle lifetime and long-input resource fit | Native 2048-context sleep reduced RSS from 904760 to 21432 KiB; 1024-dimension wake request took 1273 ms; bounded paragraph chunks retain the 2048 context; actual tail-passage/grant checks passed |

The 8192-context candidate reached approximately 1.85 GB RSS while two model services
were present. Original DSH/embedding tmux sessions were subsequently observed absent;
the exact exit cause was not captured. The candidate was stopped and original
services restored; the authenticated session API confirmed all 11 sessions. Further
resource tests must be sequential. A health response does not prove RAM was unloaded.
The production context remains 2048. A reviewed 5-second silent phone recording shows the installed policy page: `artifacts/demos/20261025-142630-m7-companion-policy-bbbb366a.mp4`. It documents this increment, not completion of M7.

The production launcher now uses native `--sleep-idle-seconds 60`. A sequential
20-second-threshold device check observed `/props.is_sleeping=true`, RAM release
and automatic request recovery. Health checks do not wake the model. This reuses
[llama.cpp's sleep facility](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md#sleeping-on-idle)
instead of introducing an inference supervisor.


## Semantic Documents integration

The installed dsh-mnemon-source-documents 0.5.11 search is lexical. The 0.2.0
companion module wraps its official Source SDK and retains the native controller,
revision, mutation, grants and evidence construction. It reuses the approved
Mnemon native engine and local Qwen embeddings for derived chunk retrieval.
No separate conversation or authoritative memory store is introduced.

The original `source:include:mnemon-source-documents` identity is preserved:
Mnemon binds workspace storage configuration and pinned grants to the starter
identity. A replacement Loader identity would silently select a different storage
root. Activation compares every existing session's directory, document IDs and
content hashes before accepting the new plugin.

Real isolated DSH checks passed for Chinese paraphrase ranking, repeated retrieval
and changed document content with stale revision exclusion. Production activation
preserved all 11 sessions and all documents in their existing storage scopes.
The index uses bounded UTF-8 chunks within the retained 2048 context, incremental
checkpoints and exact-key recovery after interrupted indexing. Empty queries remain
catalog listings; nonempty semantic failures are reported without lexical fallback.
Pinned grant IDs constrain candidates and evidence. Real native-controller/model checks passed for empty/restricted grants, relevance ordering, result/text budgets and a tail passage beyond the first chunk. Large-library throughput remains unmeasured.

The installed APK is `0.7.45-companion` (45), SHA-256
`68819333a67bdd9a2f49de8b753ec3a1ec11dfa0fa188cbc0d72c508270bc126`.
Phone Java checks reported `M7_JAVA_BEHAVIOR_DOCUMENT_PROVENANCE_OK` and
`M7_JAVA_STATUS_DURABLE_RESTART_FORGET_ISOLATION_OK`. These are executable API
checks, not a claim that every human/lifecycle acceptance scenario passed.


Native `/compact` was executed in the existing controlled conversation. Actual
`compaction/summary` and `compaction/end` events were observed; every prior Document
ID/content hash was preserved, and semantic retrieval plus source expansion still
worked. DSH's current native compaction is retained. The reference's earlier
threshold adapter is not installed over the current engine; no second context
manager or history/identity fork is introduced.

External composition import is now implemented in 0.7.45 using the existing parser,
resource validator and activation transaction. Real Android checks passed and the
system document picker returned a controlled JSON into the correct preview. The
preview was cancelled to preserve the current composition. Reviewed 7-second silent
demo: `artifacts/demos/20261025-150406-m7-profile-import-a7c06cfd.mp4`.


Current immutable host revision:
`b057bd8cf0912d3e5a8a646fe22c540dc44becc70543d577d03e0c293447bc75`.
The restart inactivity floor is measured from the new host start; persisted older
activity cannot cause an immediate cold-start wake. Step counts record admitted
model entries, not the final denied entry. The native runtime distinguishes queued
follow-up from steering; neither path is converted into a new conversation.

An actual steering check exposed a native per-turn Mnemon quota inherited from
autonomous work. The handover now ends that autonomous turn, retains the inbox,
and transfers the exact claimed user messages through native follow-up into a new
turn of the same Agent and session. The real check observed the original user
request exactly once, both retrieval tools executed, and no `budgetExhausted` or
`notRun` result. No user-facing task resubmission or separate Agent is introduced.

A document measured by the actual tokenizer at 3223 tokens exceeded the retained
2048 context and was retrieved through bounded chunks. The complete native
controller passage/grant/budget check took 84665 ms, including cold indexing and
multiple queries. This is not a single-query latency measurement. The native
source timeout is now 120000 ms instead of the upstream 10000 ms default;
autonomous turn time limits remain independently enforced. Large-library cold
index throughput and long-duration personality consistency remain unmeasured.

The full isolated DSH `host-check.mjs --documents --long` passed semantic
paraphrase ranking, cached lookup, revision replacement and workspace isolation
with that long input. The final production host responded HTTP 200, preserved all
11 sessions and their documents, and retained autonomous activities disabled.

The actual live `mnemon_recall` result and its source ID were inspected in the
habitat recording `artifacts/demos/20261025-152949-m7-semantic-habitat-ea8c325c.mp4`
(15 seconds, no audio). It contains only controlled test facts. Together with the
short policy/import recordings and executable source-navigation checks, this
closes C20-C25 within their recorded scope. Waived acoustic/lifecycle scenarios
remain waived, and this closure makes no long-duration reliability claim.
