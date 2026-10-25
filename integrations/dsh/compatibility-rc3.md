# DSH 0.2.1-rc.3 on Android 10

Verified on 2026-10-25 against the official npm packages with the narrow Android
adaptations in [the upgrade tooling](../../tooling/dsh-upgrade/README.md).
This is the sole supported deployment. The old rc.7 runtime and its profile
dependencies have been removed at the user's request.

## Deployment

- Runtime: `~/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime`.
- Home and original workspace: `~/.dsh`, `~/agent-workspace`.
- Service: `127.0.0.1:3080`, tmux session `dsh`, launcher `agent-start`.
- CLI: `dsh --version` reports `0.2.1-rc.3`.
- Client: `local.sliverwolf.assistant`, `0.7.41-dsh-rc3`, versionCode 41.
- Model credentials remain in the original phone file. DSH's official credential
  provider migrated its layout in place; no model key was exported to the client,
  host or project. The client receives a separate DSH browser cookie through its
  already authenticated device bridge.

## Inspected and exercised contract

| Operation | rc.3 mapping | Evidence |
| --- | --- | --- |
| Authentication | Launch URL exchange, owner browser cookie on HTTP and WebSocket | Anonymous root 401, authenticated calls succeed; production restart succeeds |
| Session listing | `session/list`, `payload.args._request` | Original two IDs retained and readable after migration/restart |
| Creation | `session/create`, `payload.args.request` | Explicit controlled test conversations only |
| Submission | `session/prompt`, explicit `requestId`, queue mode | Real model completes; repeated same ID produces one observed human message |
| History | `session/follow` snapshot; `session/page` with `throughSeq` | Real production Java adapter reads snapshots and pages |
| Live text | `assistant-stream` start/chunk/end, independent revision/index | Real model streaming; no invented durable sequence IDs |
| Host events | `remote.mux` stream `$events` | Status and actual decision delivery |
| Approvals | `$events/result`, `payload.args={clientId,eventId,outcome}` | One actual `bash` escalation allowed once and executed |
| Questions | Same event-result channel; structured answers | Actual `ask_user_question` receives one answer and model completes |
| Control projections | `session/control`, durable `userQuestions` projection | Stream accepted during real Java question check; parser checks retain watermark |
| Late questions | `userQuestions/answer`, direct `args.agentId/callId/answer` | Source mapped and absent-question false response exercised; timed-mode end-to-end acceptance remains unverified |
| Device tools | Existing MCP bridge SDK 1.30.0, new DSH MCP client 2.2.0 | Real model calls `mcp__sliver_wolf__assistant_get_status` and receives installed client status |

The default upstream question plugin remains in its `legacy` blocking mode. The
adapter also understands continued-question projections; this does not claim that
timed questions were enabled or fully accepted. The attempted timed probe found
the legacy default and was cancelled; the subsequent ordinary-question check passed.

The command sandbox is a material Android limitation: default `workspace-write`
commands are refused because this phone has no usable backend for the requested
confinement. A narrowly scoped `danger-full-access` request works through the normal
one-time approval path. No global sandbox or approval policy was disabled. APK builds
remain independent explicitly authorized tooling.

## Evidence and limits

`Rc3LiveChecks` runs the production Java HTTP/WebSocket adapter on the real phone,
against the actual model and MCP service. It is not a simulated server. The Android
application was separately installed and its connected habitat inspected.

A [10-second silent handset recording](../../artifacts/demos/20261025-040209-dsh-rc3-live-client-746021bd.mp4)
shows the connected character screen. Sampled frames were reviewed; this clip does
not show a completed chat exchange and is not evidence for the RPC checks below.
The original selected conversation was restored after recording.

- S01: current authentication, RPC, stream and Android runtime discovery exercised.
- S02/S03: original IDs, migration, restart, real prompt, history and streaming exercised.
- S04: actual single-use command approval and ordinary question answer exercised.
- S05: same-ID deduplication and reconnect/startup observed. Receipt-loss injection,
  very large history, interrupted filesystem publication and every cancel race are
  still unverified; earlier milestone waivers are not new passing results.
- D01: actual DSH-to-MCP-to-Android status invocation exercised.
- B01: phone build, signature verification and in-place installation completed.

Pure parser checks passed. Existing `ConversationChecks` passed 10 assertions.
The subsequent `HabitatChecks` process ended without completing that upgrade run.
During M7, Android crash evidence identified a stale assertion expecting removed
placeholder phrases. The approved zero-default expectation and an explicit legacy
fixture corrected it; the consolidated M7 HabitatChecks run passed. No new acoustic or
background lifecycle acceptance is claimed by this upgrade.

Final APK SHA-256:
`426d70228d4e00d6c87a6fe31fea89b6f1371f411d0ec3e6a0f9d6426b673d85`.
The installed signing certificate remains
`4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.

Session data and old profile configuration were retained in a phone-private
`~/.local/state/sliver-wolf-backend/migration-data-*` backup. Its old executable
dependencies were removed. Recovery now means repairing/reinstalling the pinned
new runtime and restoring data as appropriate, not selecting an old runtime.
The isolated service on port 3082 was stopped and its connection cookie removed.
