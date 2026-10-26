# Connection diagnostics and control evidence

Date: 2026-10-25. Huawei nova 5T, Android 10, Termux 0.118.3,
DSH 0.2.1-rc.3. Client 0.8.1 / versionCode 53.

## Reported incident

Read-only inspection before recovery established:

- `127.0.0.1:3080` refused the connection (curl status 000).
- No tmux server/session remained for the Termux UID.
- `pidof com.termux` returned no process; the app package was installed and not
  marked force-stopped. Phone uptime was about 3.77 hours.
- Available ActivityManager/event logs contained no matching termination record.

Thus the immediate cause was absent backend runtime, not merely an App label or
LLM provider outage. The evidence does **not** identify who or what terminated
Termux/DSH. Memory pressure, vendor background cleanup and manual exit cannot be
distinguished with the retained records. No crash or OOM cause is asserted.

The existing `agent-start` restored DSH without replacing its runtime, credentials
or user data. Two client defects were established by source inspection: HTTP and
WebSocket errors collapsed into generic messages, and the settings page displayed
a one-time connection string. Both are corrected.

## Executed verification

| ID | Evidence |
| --- | --- |
| C27 | `ConnectionChecks` authenticated against actual DSH, followed both real streams and retained the selected session. A separate test preference store received HTTP 401 using invalid credentials, then connection refusal on a closed port, then recovered with the original credentials. Production credentials were not replaced. |
| C28 | The visible drawer changed to manually disconnected without reopening, then reconnected to the same session. It exposes check timestamps, event readiness, categorized failure and retry state. |
| C29 | User granted the official Termux permission. The fixed start command returned a real PendingIntent result with the existing tmux session present. DSH was not restarted for this idempotency check. |
| C30 | After verifying all actual DSH sessions idle, a controlled graceful stop made the port unreachable (000). `ConnectionChecks --mode cold-start` used App RUN_COMMAND to start DSH and regained authenticated subscriptions. No model/runtime upgrade or conversation replacement occurred. |

The first stop attempt addressed an invalid tmux pane and did not stop DSH;
its subsequent readiness result is excluded from C30. The corrected test observed
the unreachable port before invoking the App startup path.

Build and signature verification passed on the phone, followed by successful
in-place installation. APK SHA-256:
`08624d148d7826601c561230b891c826efa3fb046e8675c3855c6b0006136aec`.
Existing Android signing identity was retained. `git diff --check` passed.

The tests do not establish immunity to future Android process eviction or
exhaustively test every timeout and vendor battery policy. No new acoustic test
is claimed. The runtime termination cause remains unresolved as described above.

## Reverse Agent integration

C31: `ConnectionChecks` in `agent` mode created a controlled test conversation
using a separate test selection store. The actual DSH Agent invoked
`mcp__sliver_wolf__assistant_get_status`; its successful tool result contained
`appVersion`. Only that read-only tool could receive a one-time approval.
The user's selected conversation remained unchanged.

An earlier standalone app_process harness was killed and did not complete; it is
not counted as a passing integration. The successful check kept the actual Android
App running throughout the Agent request. A direct authenticated bridge status
request also succeeded after recovery.

Final UI verification: opening the system page starts at its top, command receipts
carry timestamps, and live DSH readiness is separate from earlier startup output.
The final APK was installed successfully without changing signing identity.

Silent, inspected 10-second real-device demo (ignored):
`artifacts/demos/20261025-223731-connection-control-995c163b.mp4`.
The status bar and habitat edge were cropped to exclude unrelated content.
