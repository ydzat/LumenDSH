# Session Contract v0.1

Status: accepted internal contract, mapped to the installed rc.3 build in
[current compatibility](../../integrations/dsh/compatibility-rc3.md). Historical M1 implementation and
tested limits are recorded in [device evidence](../../tests/m1-evidence.md).

## Reference and topology

Use the existing DSH Web Host session/control surface via an adapter. The app attaches to a running service; it must not start a second agent runtime or shut down DSH on disconnect.

References: [Session Controller](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/api/session-controller/README.md), [API Gateway](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/api/gateway/README.md), [SDK lifecycle](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/sdk/client/README.md).

Latest upstream documents are reference material. The installed `0.2.1-rc.3` build has its own inspected authentication, RPC, event and approval mapping. The SDK subprocess ownership model is not the default connection model.

## Internal adapter operations

Names below are internal semantics, not asserted DSH wire methods.

| Operation | Required behavior |
| --- | --- |
| `connect` | Authenticate and report backend identity/version plus verified capabilities |
| `listSessions` / `selectSession` | Read or choose an existing conversation without creating one implicitly |
| `createSession` | Explicit new-conversation action; never a recovery fallback |
| `submit` | Accept session ID, client request ID, and ordered content; return an acceptance receipt |
| `observe` | Obtain authoritative history/state, then follow changes with a recoverable position if supported |
| `cancel` | Cancel the specified operation only if supported; never emulate cancellation by killing DSH |
| `answerApproval` / `answerQuestion` | Match the original request ID and preserve backend semantics |
| `disconnect` | Release client resources only |

Capabilities include history, event following, submission deduplication, cancellation, approvals, and user questions. Unsupported capabilities must be visible and must not be fabricated by the adapter.

## Ownership and identifiers

DSH owns conversations, task outcomes, and durable history. The client owns selected session ID, pending-submission records, display caches, and connection state. Appearance changes and self-improvement requests do not create a second agent or mandatory second conversation.

Persist a `clientRequestId` before submitting. Keep it distinct from any server message ID, turn ID, or tool-call ID. A receipt means accepted, not completed.

If the backend supports deduplication, retransmit only with the same request ID. If a timeout leaves delivery uncertain and deduplication is unavailable, mark it `delivery-unknown`, reconcile against authoritative state, and do not blindly resubmit. Do not promise exactly-once execution from a client-side ID alone.

## Event and recovery semantics

The adapter produces message, task-state, tool-state, approval, question, and connection events while retaining server identifiers and available ordering information. Do not replace a current snapshot with an older cached event. When replay/cursors are unavailable, reload authoritative state and reconcile; do not invent a durable sequence.

Distinguish `disconnected`, `running`, `waiting-for-approval`, `waiting-for-user`, `idle`, `failed`, and `unknown`. A lost connection is not task failure. Text generation ending is not proof that tools or the task have completed.

Model metadata follows the current session's effective native selection, including
pending changes. Reconnect/selection events invalidate client metadata; unknown
limits remain unknown. DSH owns input fitting and compaction. Autonomous budgets
apply only to attributed autonomous requests, never ordinary user requests. See
[model configuration](../model-configuration.md) for the exact rc.3 mapping.

Reconnect must preserve the selected session, recover pending decisions, and suppress duplicate display entries. Client upgrades must not send DSH shutdown. Credentials must not appear in logs or package manifests.

## M0 evidence required

Record exact deployed package versions/revisions, transport/framing, authentication, method shapes, event ordering/history behavior, deduplication support, and approval flow. Capture sanitized fixtures from designated test conversations only. See S01-S05 in [acceptance](../../tests/acceptance.md).

## Character touch submission (0.4.26)

Touches are explicit user messages to the selected session through existing
`session.prompt` queue mode. The inspected rc.7 API Proxy routes this to
`agent.followup`, not `agent.steer`. No cancel is sent. Existing pending-delivery
fences and unknown-delivery handling apply; rejected touches are not announced as
sent and are not automatically retried. No session is implicitly created.

## Connection control follow-up

App-driven lifecycle operations are explicit user actions through the official
Termux RUN_COMMAND interface. The fixed launcher starts the independent existing
DSH deployment; a reconnect itself still never launches, upgrades or stops DSH.
Authenticated RPC precedes event subscription. Categorical transport diagnostics
and subscription deadlines preserve conversation and uncertain-delivery semantics.
See [connection diagnostics](../connection-diagnostics.md).

## Independent model controls

DSH owns the model catalog and selected routes. Default-model changes affect new
sessions; explicit session changes affect only the addressed existing session and
subsequent requests. The rc.3 adapter must avoid the native selector's coupled
save-default side effect. A missing model remains visible as unavailable rather
than silently becoming another route. Authentication headers are not model state.
