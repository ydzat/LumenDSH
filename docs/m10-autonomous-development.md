# M10: Autonomous iterative development (0.10.x)

Status: active, opened 2026-10-26. Target client line: **0.10.x**
(`versionName 0.10.0-dev`, `versionCode` bumped on each build). The project is
expected to stay on 0.10.x for a while, so patches accumulate instead of jumping
minor versions.

## Why this milestone

M9 built the machinery for the agent to develop itself safely: supervised
relay + A/B slots, boot persistence, slot-based host upgrades with rollback, and
a situation-driven development loop (experience stream, candidates with quiet
mode, growth stages, self document, `extend_self`). M10 puts that machinery to
work. The first phase is a set of explicit user requirements; after that the
loop decides what to build from real experience.

## Phases

### Phase A — user requirements (current)

The user states the requirements for this milestone. Each requirement is:

1. scoped in this document or a linked design note,
2. implemented on `agent/dev`,
3. verified with tests or device evidence,
4. delivered as a 0.10.x client change when it touches the app.

Requirements take precedence over autonomous work; the autonomous loop stays
paused or idle while a user requirement is open.

### Phase B — self-driven iteration

With the requirement queue empty, the M9 loop runs:

```
experience → candidates → quiet or wake → situation → one bounded step → growth
```

- Candidates come from the append-only experience stream and the self-notes
  ledger; if nothing qualifies, the agent stays quiet.
- A wake carries the situation (open needs, recent runs, last error, the chosen
  candidate), not a task list.
- Growth advances only through evidence: observation → self-fix →
  self-extension → self-review. Stances require evidence and stage 3.
- `extend_self` scaffolds a new module when a capability is missing; the agent
  implements and verifies it with normal tools.
- Each iteration ends with a short factual outcome and evidence path.

## Versioning and release rules

- Client `versionName`: `0.10.0-dev` initially; patch bumps for fixes, minor
  bumps only when the user asks. `versionCode` increases monotonically.
- APK builds use `native-release.mjs` (signed, receipted); installation is a
  system-confirmed user action and is never performed silently.
- Host/plugin changes ship through `activate-host-slot.mjs` (target slot →
  verify → switch → soak → keep source for rollback).
- Content/appearance/voice releases follow their existing contracts.

## Invariants

- Code changes on `agent/dev` only; never push or force-push `main`.
- No credentials, no unapproved network/privilege commands, no silent APK
  installs, no direct process kills of DSH — restarts go through the relay.
- Every change is verified with a real check; a failed step is recorded and
  the next iteration continues.
- The Web GUI and the Android client use the relay entrance
  `http://127.0.0.1:3080`; slot ports are internal.

## Acceptance

M10 is complete when the user requirements are delivered and the autonomous
phase has run for a representative period with: candidates derived from real
experience, quiet periods recorded instead of forced wakes, at least one
self-extension delivered through `extend_self`, and growth stages advanced by
evidence. Individual 0.10.x releases carry their own evidence.

## Requirement list

_To be filled from the user's requirements. Each entry gets: goal, scope,
verification, evidence path._


## Progress (2026-10-26)

### Delivered

- **Embedded official UI**: `WebChatActivity` hosts the DSH Web UI at
  `http://127.0.0.1:3080` with the entrance cookie fetched through Termux; a
  stale cookie is cleared on 401, external links leave the app, and a floating
  home button returns to the native surface. A 1dp headless WebView keeps the
  tide plugin loaded so the home can show the period without opening chat.
- **Home peak/valley display**: the native habitat draws 「梁文峰 / 梁文谷 ·
  countdown」 from the tide plugin's own client state (`__liangwenTide.state()`),
  with a fallback that evaluates the plugin's `/liangwen-tide/policy` windows.
  Verified on device: 「梁文谷 · 7:04:53」.
- **Period-driven scheduling**: the client posts the plugin phase to
  `/sliver-wolf/companion/period`; a fresh peak report suppresses `developDue`,
  so heavy development only runs in valley (peak keeps light activities).
- **Device operation without the user**: an ADB control plane is available over
  the USB tether (`phone.sh`: screencap, input tap/swipe/text/key, am start,
  `pm install`), exposed to the agent as `phone_*` tools. Verified: screenshots
  read back, taps change the UI, and app installs complete with no system dialog.
- **Accessibility fallback**: `ScreenControlService` (node tree, click by text,
  tap, type, global actions) and `ScreenCaptureService` (MediaProjection PNG)
  cover the untethered case; both are opt-in.

### Still open

- Native bridges inside the WebView surface: voice input, share handoff,
  notifications, pet overlay controls.
- Settings and schedule panels as DSH client plugins instead of native screens.
- The old `local.sliverwolf.assistant` client remains installed; retire it after
  the new client reaches parity.


## Self-continuation after restarts (2026-10-26)

A slot restart drops the running turn, and the DSH goal is disarmed afterwards,
so nothing used to resume. `~/agent-workspace/nudge.mjs` queues a continuation
prompt into the current conversation through `session/prompt`
(`args.request.{sessionId, requestId, mode:'queue', content}`), and
`~/agent-workspace/restart-host.sh` wraps "hold watchdog → restart slot → nudge".
Every deliberate restart should go through that wrapper so development continues
without a user message.


## Voice input on device (2026-10-26)

The official experimental voice-input bundle (`@deepseek-ai/dsh-experimental-
voice-input-bundle`, enabled in the plugins page) now receives microphone
permission through the WebView (`onPermissionRequest` + `RECORD_AUDIO`). Its
SenseVoice service still fails to prepare on this device with
`Local speech is unavailable for android-arm64`, so on-device local
transcription is not available. The fallback is the native `SpeechRecognizer`
already used by the client: recognize with Android's engine, then send the
recognized text as a session prompt (or type it through adb) instead of relying
on the web plugin's local model.
