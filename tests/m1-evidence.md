# M1 Conversation Client Evidence

Date: 2026-10-24, approximately 20:18–20:35 CEST. Baseline: `669e685` plus the local
M1 commit containing this record. Device: Huawei nova 5T / YAL-L21, Android 10,
Termux 0.118.3; explicit wireless ADB endpoint `<PHONE_IP>:5555` reverified.
DSH remained `0.1.0-rc.7-termux.1`, reporting API version `0.0.1`.

## Build and provenance

All APK compilation/signing happened in the independent Termux build directory.
Installed app: `local.sliverwolf.assistant`, version `0.1.0-m1`, code 2, min/target SDK 29.
Final APK SHA-256: `9f296ae2373cba6a868c0f54f6e5bfd0be7f3d3e233d484647a30ef2322c0eec`.
Developer certificate SHA-256 remains
`4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.
`apksigner verify` passed; `adb install -r` succeeded; final cold launch took 390 ms.

Integration checks initially used APK `df01e9be7df52fe9c3814cd896fbf7729611566129f38551df8751ea0eb63fb5`.
The final artifact retains the same transport/controller, with corrected context labels,
tool-result presentation, scrolling, button layout, and license text. Cancellation and
recovery were also exercised after the presentation update; streaming was directly
observed on the final APK. No backend restart, configuration change, or upgrade occurred.

## Acceptance observations

One explicit new acceptance conversation was created through the client's New/Create
action. Its selection persisted across force-stop and replacement installs. It is a
test conversation, not a required separate development agent. Existing personal
conversations were not used for submissions or transcript inspection.

| Case | Result | Actual procedure and evidence |
| --- | --- | --- |
| S02 | PASS | Sent actual text through the client to the selected test session; received real model replies. An independent UiAutomation observer on the final APK returned `live-streaming-text-observed` while the response was incomplete. The UI subsequently displayed `idle · completed`; authoritative history contains the matching user request and terminal turn. |
| S03 | PASS for accepted-message disconnect/reconnect | Sent a prompt requiring `ask_user_question`, disconnected the client, reconnected to the same session, recovered history and the pending question, and answered it. Repeated reconnect with a pending approval also restored that decision. Server audit found no duplicate request messages. Receipt-loss fault injection was not performed; see limitations below. |
| S04 | PASS for exercised decision/cancellation paths | Answered the real single-choice question with Proceed and received `SWQUESTIONOK`. Requested an explicit harmless `printf SWAPPROVAL` escalation for approval UI testing: rejected the first request and allowed the second once. Durable `approval/decided` outcomes are `rejected`, `allowed-once`; tool results show refusal then `SWAPPROVAL`. Cancelled a later turn waiting on a question: pending decision disappeared and durable `turn/end.reason.kind` was `aborted`. No approval policy was changed. |
| S05 | PASS for a task waiting on user input | Force-stopped only the client during an outstanding question, relaunched it, recovered the same session and decision, and answered successfully. A replacement client install during another pending question also preserved the backend task; it was subsequently cancelled from the client. This does not prove survival across a DSH or phone restart. |

Final read-only audit of that explicitly designated session:

- Seven distinct user RPC IDs, seven user-origin messages, zero duplicate request messages.
- One additional plugin-origin context message, correctly distinguished from human input.
- Seven turn endings: six `completed`, one `aborted`.
- 3,877 stored `assistant/chunk` events; ten assembled assistant messages across ten steps.
- Four tool calls/results; two approval asks/decisions, matching rejection and one-time allowance.
- `hasMore: false` for the audited window. This is not a large-history pagination test.

The audit command is `node audit-session.mjs <explicit-test-session-id>` inside Termux.
It emits counts and outcomes, not transcript content, IDs, credentials, or raw tool arguments.
Private screenshots/APKs are in ignored `artifacts/m1/`.

## Focused checks

`tooling/android-build/check-conversation.sh` compiled the actual `Conversation` class
and ran ten synthetic parser assertions on Android ART using `app_process`: overlapping
snapshot/event deduplication, exact request correlation, unknown request preservation,
streamed text, plugin context labeling, replacement of partial text by final text,
cancelled terminal state, compaction-copy handling, and out-of-order history merging.
Result: `ConversationChecks: 10 assertions passed`. These are logic checks, not substitutes
for S02–S05 device evidence.

`tests/android/TextInput.java` is a separate instrumentation APK used only through
explicit ADB invocation. Huawei's IME altered `adb input text` strings, so this helper
sets the client's input using `ACTION_SET_TEXT` and verifies the exact text. It never
presses Send or answers decisions. Its observer mode inspects the real client's streaming
label without waiting for UI idleness. It is not included in the assistant APK, has no
background service, and does not change input-method settings.
Test APK SHA-256: `ec15ad1c925471a516431d38e36e4fc989c40b193a2ac863e6e8bd6a221c52fd`.
After validation, `adb uninstall local.sliverwolf.assistant.tests` returned `Success`.
The assistant package and its persisted selection were retained.

## Limits and follow-up

- No credential authentication is offered by the installed DSH. Only phone loopback is supported.
- No server submission deduplication is assumed. The client persists a pending request
  before sending and never automatically retransmits it. Recovery can correlate a user
  history event or queue entry. If unresolved, it blocks new submission until reconciliation
  or explicit uncertainty acknowledgement. Lost HTTP receipts, storage failure, and abrupt
  power loss have not been fault-injected on-device; no exactly-once guarantee is claimed.
- Automatic unexpected-socket-loss backoff is implemented; device acceptance exercised
  explicit disconnect/reconnect and process restart, not every network failure timing.
- Multi-select/custom question forms are implemented but only single-choice response was
  exercised. Concurrent decisions across multiple clients were not stress-tested.
- UI uses bounded plain-text history (50-message pages, at most 30,000 events in memory),
  not rich Markdown or arbitrary multimodal rendering. Compaction replacement records are
  labeled; original fetched transcript rows are retained, not presented as new human input.
- No M2–M6 capability or V/P/U/B02 case is claimed. In particular, no background voice,
  lock-screen wake, MCP device bridge, pet renderer, or autonomous update exists yet.
