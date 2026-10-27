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

## M10 is the foundation, not the destination

The end state is a nearly fully autonomous companion: the user mostly talks by
voice, the habitat is the default surface, and the official WebUI is a precision
console. M10 does not build that product. It builds the ground the agent needs to
build it autonomously:

| Pillar | Status | Remaining for M10 |
| --- | --- | --- |
| Autonomy loop | policy, candidates, experiences, situation implemented; paused | resume safely; it currently cannot read the web |
| Phone control | local adb over 127.0.0.1:5555 (until reboot); a11y/MediaProjection fallback | reboot recovery; one-tap a11y enable |
| Console | official WebUI embedded and usable | composer injection plugin; mobile shell |
| Self-built plugins | community plugins installable; no own scaffold yet | `@lumendsh/webui` scaffold + build/load path |
| Self-upgrade | slots, watchdog, silent install | an end-to-end autonomous build → install → restart run |
| Research | web tools exist but autonomous runs deny them | bounded read-only network allowlist with audit |
| Observability | experience stream, runs, pause switch, restart nudge | activity timeline and failure visibility |
| Docs/tests | design docs + 30 tests | acceptance cases for the pillars above |

Completion of M10 means: the agent can wake on its own, operate the phone, write
and install a plugin, build/install/restart itself, read documentation, and do all
of it visibly, pausably and reversibly.

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


## Community plugin evaluation (2026-10-26)

| Plugin | Result |
|---|---|
| `dsh-voice-input` / official experimental voice bundle | Installed and enabled in the plugins page; WebView now grants `RESOURCE_AUDIO_CAPTURE` after `RECORD_AUDIO`. Its local SenseVoice service reports `Local speech is unavailable for android-arm64`, so the client falls back to Android's `SpeechRecognizer` and sends the transcript as a session prompt. |
| `dsh-schedule-panel` (GitHub) | Installed and its `schedule` tool worked (add/list), but its client panel fails to mount on 0.2.1-rc.3 (`Remote 挂载失败: typert …`) and renders broken, so it was removed. Scheduling stays on the official 自动化任务 page. |
| `dsh-mobile-drawer` | Rejected: requires Node >= 26.3.1; the device runs 24.22. |
| Official settings / plugins page | Used as-is; no custom settings screens are built. |


## Startup nudge validation (2026-10-26)

First attempt never fired: the handler read `state.policy.sessionId`, but the
selected conversation lives in the companion `PolicyStore` (`policy.sessionId`),
so the lookup was always empty and the flag stayed behind. Fixed in d496539,
with a second path: `restart-host.sh` now posts the nudge itself and clears the
flag when the host path did not consume it. A deliberate restart was validated:
the flag disappeared and the continuation prompt arrived without a user message.


## Day wrap-up 2026-10-26

Delivered and verified on device:

- Official DSH Web UI embedded in the Web client (`local.sliverwolf.web`,
  0.10.17 / 73) with cookie handoff, reconnect, crash capture and a home surface.
- Home shows the tide plugin's exact 「梁文峰 / 梁文谷」 phase and countdown.
- Peak/valley gating in the companion policy (`developDue` suppressed at peak).
- ADB control plane over the USB tether, exposed as `phone_*` tools; silent APK
  installs verified. Accessibility + MediaProjection kept as fallback.
- Remote CPUFast TTS reconnected to the new client (`gpt-sovits-v2pro`,
  `silver-wolf`) and verified end-to-end (`assistant_speak` → succeeded).
- Community plugins evaluated: official experimental voice input (local model
  unavailable on android-arm64 → native SpeechRecognizer fallback),
  `dsh-schedule-panel` (tool works, panel incompatible → removed), official
  settings/automation pages used instead of custom screens.
- Automatic continuation after deliberate restarts: flag + host consumption,
  with an external nudge fallback; validated.
- Tests: 30 passing, 0 failing (companion policy, self-iteration, situation,
  self-notes, develop-situation).

Current autonomous state at wrap-up: policy `paused=true`, `enabled=true`,
period `valley`. No background jobs or pending nudge flags. The old
`local.sliverwolf.assistant` client is uninstalled; the device bridge points at
the new client on port 3084.
