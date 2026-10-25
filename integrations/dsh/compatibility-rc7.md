# Installed DSH Compatibility: M0

Inspected on 2026-10-24 on YAL-L21 / Android 10. This record maps the internal
[session contract](../../docs/contracts/session.md); it does not redefine the upstream wire API.

## Identity and evidence

Deployment: `dsh-termux` `0.1.0-rc.7-termux.1`, with the inspected API packages at
`0.1.0-rc.7` (MIT). The live `host.describe` reports `version: "0.0.1"`.
That field alone cannot select a compatible adapter. The vendored build's Git revision
is not established; installed-file SHA-256 pins below identify the actual inspected code.
Source root: `/data/data/com.termux/files/usr/opt/dsh-0.1.0-rc.7-termux.1/node_modules/@deepseek-ai/`.
Local untracked inspection copies are under `.local/dsh-source/`.

| File relative to source root | SHA-256 of original phone bytes |
| --- | --- |
| `dsh-host-apiproxy/lib/index.js` | `66ba09b82e7db23bfdce7701e4111eac3170022c9256932f656e5e69e41419c0` |
| `dsh-client-connection/lib/index.js` | `3edb26abada71be27a28582ffd28c233efcd1d253f2e81c2207a67c5532ae0cf` |
| `dsh-api-gateway/lib/index.js` | `266fd319e360e9237382be7c180f83b60e1706740b652df768189bae8fc87798` |
| `dsh-api-remotes/lib/index.js` | `50bc9b8766681957eea6def093ded0d0cdb6529cee88d728aa0880a5e67738e7` |

## Capability matrix

| Concern | Installed-source finding | Live evidence / limitation |
| --- | --- | --- |
| Authentication | No credential authentication layer. Connection checks Host/Origin and Fetch Metadata; this is a reachability/trust fence. | No credentials supplied: describe/list work. Foreign Origin and raw HTTP Host return 403. No authenticated-access claim. |
| Unary framing | POST `/api/<method>`, JSON `client-request`, `rpcId`, `method`, `payload`; `server-response` echoes `rpcId`, wraps `result: {ok,value}` or `{ok:false,error}`. HTTP success alone is insufficient. | `host.describe` and `session.list` pass envelope and result checks in Termux and the APK. |
| Sessions | `session.list`, explicit `session.create`, `session.history`, `session.prompt`, `session.cancel`; Typert interceptors coexist with API Proxy fallback. | List returns one existing session. No test conversation created; no existing content printed. |
| History | `history(request)` uses `historySourceFor`, paginates events by `beforeSeq`/`maxMessages`; tail may include projections. | Missing-session request yields `session-not-found`. Actual history/reconnect reconciliation belongs to S02/S03. The connection README's statement that every history read resumes a session conflicts with API Proxy code; do not rely on that README statement. |
| Submission | `session.prompt` admits content with source `rpcId`, calls `followup` or `steer`, returns `{accepted:true}`. Receipt does not mean completion. | No prompt submitted during M0. |
| Deduplication | Inspected prompt path creates a message for each admission; no deduplication check by `rpcId` found there. | Treat retry deduplication as unsupported until demonstrated. Persist request ID; on ambiguous delivery expose `delivery-unknown`, reconcile, never auto-resubmit. No destructive duplicate-task experiment performed. |
| Events | Network `/api/events.mux` and `/api/events.host` are downlink WebSockets. GET SSE exists only for the in-process carrier. | Both network sockets open; ordinary GET returns 426. No task was active, and no frames arrived during the bounded probe; streaming response delivery is NOT yet verified. |
| Recovery | Mux source emits `session/subscribed` with `lastSeq`, pending question/approval entries, and current queues. Session events carry per-session `seq`. No durable stream replay cursor demonstrated. | Reconcile snapshots/history and buffer events; do not treat event `rpcId` as a durable cursor. Actual pending-decision replay remains S03/S04. |
| Approvals | `/api/respond` accepts `client-response` with original `rpcId`. Approval value matches `sessionId`, `approvalId`, and `outcome` (`allowed-once` or `rejected`). Settling removes the pending entry. | Unknown decision ID is rejected with `{accepted:false,reason:"not-pending"}`. No real approval triggered or bypassed. |
| Questions | Same respond carrier; value carries session ID and `answer.answers` (`id`, `selected`, optional `custom`). Pending question validation precedes claiming. | Source discovery only; real question UI/answer remains S04. |
| Cancellation | `session.cancel` calls `agent.cancel({kind:"user"},{keepInbox:true})`; queued work is retained. | Missing session returns `session-not-found`; cancellation of active work remains S04. Never kill DSH to emulate it. |

The original API Proxy bundled file contains identifiable source regions: schemas around
lines 398–700 and 3841–4152, `prompt` 2774–2826, `cancel` 2902–2917,
`events.mux` 3564 onward, and `respond` 3767–3816. Connection's README and
`api-request-trust` / WebSocket sections of its bundled code establish the carrier fence.
These line references apply to the pinned installed files, not latest upstream.

## Re-run

Run `node probe.mjs` inside the existing Termux environment with DSH already running.
The probe reads describe/list, tests rejected requests against random nonexistent IDs,
and briefly opens both event channels. It does not create sessions, submit model work,
read credentials, or print session IDs/content. Node used: `v24.22.1`.

M1 must choose/select a designated test conversation explicitly and exercise actual
prompt/event/recovery/decision behavior. The M0 APK is a read-only diagnostic client,
not a complete implementation of the session contract.

## M4.5 source review

The installed inspection copy session-types.d.ts declares assistant/chunk with
turn, step and StreamChunk (lines 264 onward), and turn/end with TurnEndReason.
The existing Conversation adapter already consumes text-delta and reconciles by seq.
M4.5 uses the first public-text chunk sequence as a presentation identity through
assistant/message completion; reasoning chunks are excluded. Live card verification
is recorded separately in tests/m45-card-evidence.md. This does not establish
server-side notification hooks or scheduling support, which remain C01 work.

## M4.5 app-owned notification and scheduling path

Completion notification uses the already inspected turn/start, assistant/message
and turn/end events in an optional Android observer. Scheduling is Android-owned;
it does not assume any installed DSH scheduler/hook support. session.list returns
items with sessionId; session.prompt receives that existing identifier, queue mode
and ordinary text content. A real Android alarm delivered a controlled prompt and
received acceptance plus the requested reply in that same conversation. See
[notification evidence](../../tests/m45-notification-evidence.md) and
[schedule evidence](../../tests/m45-schedule-evidence.md). Server-side plugin hooks
and retry deduplication remain unestablished; unknown delivery is never auto-retried.
