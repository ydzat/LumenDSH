# Connection diagnostics and DSH control

Android 0.8.1 separates the two directions in **Connection and system**:

| Direction | Implementation and evidence |
| --- | --- |
| App → Termux → DSH | Official Termux `RUN_COMMAND` executes the existing `agent-start` or a fixed read-only status command. Permission and command results are shown separately from DSH connectivity. |
| App → DSH session | Authenticated RPC first, then host and session event subscriptions. The page shows live readiness, check time, retry countdown and the last sanitized fault. |
| DSH → Agent → App | Existing authenticated MCP device tools. The page reports the actual bridge state, authenticated peer count and last successful tool request. It does not infer an Agent connection from an open listening port. |

## Setup and recovery

1. Grant LumenDSH the **Run commands in Termux environment** permission.
2. Set `allow-external-apps=true` in `~/.termux/termux.properties`, then run
   `termux-reload-settings`. This enables the official interface; callers still
   need Android permission. The current phone is configured and the user granted
   that permission.
3. Keep the existing `agent-env.sh` and `agent-start` installed in Termux.
4. Use **Start DSH** to start the existing deployment. Its launcher is idempotent:
   an existing `dsh` tmux session is retained. No runtime upgrade, task cancellation
   or new conversation is part of this action.
5. Use **Check DSH process** for an actual Termux tmux/port query, and **Check
   connection** for a read-only authenticated API request. A returned launch
   command is not proof that DSH has finished starting; RPC and subscriptions
   subsequently establish readiness.

Reconnection preserves the selected conversation and uncertain delivery record.
It never resends a prompt automatically. **Disconnect client** stops only that
client connection. LLM and speech provider requests are independent services;
this page does not send billable prompts to test them.

## Fault interpretation

- `SERVICE_UNREACHABLE`: no connection to the configured loopback port. Start
  DSH or inspect Termux; the error alone cannot determine why a process exited.
- `HTTP_401` / `HTTP_403`: a server responds but the client credential is rejected.
  Check backend pairing, not the reverse MCP credential.
- `TIMEOUT`: service response timed out; check device load and Termux state.
- `WS_CLOSE_*` / `STREAM_*`: event transport or subscription failed. A 20-second
  subscription deadline prevents indefinite "connecting" after socket acceptance.
- Protocol or RPC rejection: inspect the installed DSH compatibility and selected
  conversation. The client does not create a replacement conversation.

The page updates existing text views rather than rebuilding the drawer, so state
changes do not require reopening settings or reset scroll position. Fault records
contain categories and timestamps, not credentials, message text or raw server errors.

## Termux API boundary

Reuse the [official RUN_COMMAND interface](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
and the [0.118.3 constants](https://github.com/termux/termux-app/blob/v0.118.3/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java).
No new library or generic shell execution tool is exposed. An explicit, one-shot
PendingIntent delivers a correlated result to a non-exported receiver. Only fixed
status markers and exit codes are retained. A missing callback times out without
automatically repeating the command.

Android owns application process lifetime; see [Android process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle).
The drawer links to Termux system settings. Background restrictions are a possible
cause of exits, not a diagnosis inferred from an unreachable port.

## Incident and verification

See [0.8.1 device evidence](../tests/connection-evidence.md) for the observed outage,
its unresolved termination cause, and the real recovery tests.
