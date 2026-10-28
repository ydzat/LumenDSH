# Next capabilities: what to build, borrow or install

Status: proposal, 2026-10-28. Written after M10 closed, from self-assessment plus a
survey of the DSH plugin ecosystem, current agent research and industry practice.

## 1. Where we are

M10 delivered the foundation: an official-WebUI console, a voice-first habitat
(wake word, continuous conversation, TTS), a controlled autonomy loop
(consolidate / reflect / develop / browse, with budgets and an audit trail), a
self-built plugin pipeline, a device bridge over adb, a phone build/install path
and REA for reverse engineering.

What still limits the end state ("idle: play with the phone, browse, develop
itself, chat; voice by default"):

1. **Phone competence is action-level, not task-level.** We can tap/text/screenshot,
   but there is no observe → act → verify loop with stale-element protection,
   semantic UI parsing or success criteria. Community `dsh-mobile-gui-agent`
   shows what that loop looks like.
2. **No behavioural evaluation.** We test policy code (36 tests) but never measure
   "can the agent complete task X on the phone at all". Regression in real
   capability goes unnoticed; AndroidWorld-style task suites are the academic
   answer.
3. **Observability is coarse.** Run status and failure codes exist, but there is no
   turn/tool trace, no cost/latency dashboard, no way to replay a bad run.
4. **Memory exists but retrieval quality is unmeasured.** Mnemon is installed
   (hot memory + documents + recall); nothing measures whether recall actually
   surfaces the right evidence.
5. **Discipline is implicit.** The anti-loop rules live in prose; community skill
   packs (review/plan/test-first) encode similar discipline as enforceable skills.
6. **Autonomy safety is bespoke.** Our policy budget/guard is custom; industry
   practice is declarative permission rules plus telemetry.

## 2. Academic, industry and community view

- **Mobile GUI agents (academia)**: AndroidWorld (ICLR 2025) is the reference
  dynamic benchmark for Android agents; Mobile-Agent-v3 and recent work emphasise
  perceive → plan → act with verification, and even question whether the screen is
  always needed (accessibility-tree-only agents). Takeaway: build a small,
  repeatable task suite and a verify step, not just more taps.
  <https://proceedings.iclr.cc/paper_files/paper/2025/file/01a83bc2f2732a58e6aa731e659e7101-Paper-Conference.pdf>
  <https://ar5iv.labs.arxiv.org/html/2508.15144>
  <https://export.arxiv.org/abs/2606.19388>
- **Observability (industry)**: OpenTelemetry's GenAI conventions and agent-tracing
  tooling (Langfuse et al.) converge on spans for turns, model calls and tool
  calls with inputs/outputs. Takeaway: emit a local trace per run and keep it
  inspectable; don't invent a proprietary log format.
  <https://www.morphllm.com/agent-tracing>
  <https://github.com/rohitg00/ai-engineering-from-scratch/blob/35cab093e93518cf2780efbb4de174d78491edc4/phases/13-tools-and-protocols/20-opentelemetry-genai/docs/en.md>
- **DSH community**: 353+ indexed plugins. Directly relevant:
  `dsh-mobile-gui-agent` (observe/act/verify, stale protection, approvals),
  `dsh-vision-android` (vision + adb tools), `modlens` (vision bridge for
  text-only models), `dsh-web-ui` (mobile remote), `dsh-trace` (telemetry),
  `dsh-dashboard` (usage/quality), `dsh-cost-meter` (cost), `dsh-review-skills`
  and `dsh-superpowers` (engineering discipline skills), `dsh-tool-policy` /
  `dsh-permission-rules` (declarative tool policy), `dsh-evolve` (hot-mount
  plugins on itself).
  <https://github.com/kejixiaoliang/awesome-dsh-plugins>
  <https://dshbase.com/zh/plugins/dsh-mobile-gui-agent/>
  <https://dshbase.com/zh/plugins/dsh-vision-android/>

## 2b. Embodied intelligence / robotics view

The user's framing is right: the phone is the **body**, and this agent is its
**brain**. That changes what "infrastructure" means.

- **Sense–plan–act loop**: the phone needs proprioception (battery, network,
  foreground app, screen state, sensor availability), exteroception (screenshot,
  UI hierarchy, notifications) and motor control (tap, text, keys, intents). Today
  we have motor control and raw sensors, but no body-state model to reason from.
- **Affordances, not coordinates**: robotics grounds actions in what the
  environment affords. UIAutomator hierarchy + semantic element ids are our
  affordances; raw pixel taps are the fallback, exactly the way VLA work combines
  language, vision and action grounding (RT-2, Mobile-Agent-v3).
- **Safe states and a reflexive layer**: a robot must not command an action that
  removes its own sensorimotor loop. On this body that means: **never disable
  Wi-Fi, never enable airplane mode, never revoke adb/USB debugging, never kill
  the host process**. This belongs in the policy layer as hard invariants, not in
  a prompt reminder.
- **Evaluation like robot benchmarks**: task suites with deterministic success
  predicates (AndroidWorld-style) are the sim-to-real analogue: a fixed set of
  embodied tasks whose success rate we track over time.
- **Recovery**: robots have a safe-home routine; ours is "restore connectivity,
  return to the habitat, report what happened" after a failed physical task.

## 2c. Embodied safety invariants (hard rules)

1. Never turn off Wi-Fi, mobile data or Bluetooth in a way that could strand the
   agent; never enable airplane mode.
2. Never revoke developer options, USB debugging or this app's own permissions.
3. Never kill the DSH host, the relay or the adb server as part of a task.
4. Any task that changes connectivity must go through the user, even when other
   approvals are disabled.
5. A failed task ends in the safe-home routine above, not in an unbounded retry.

## 2d. Motor-bus findings (2026-10-28)

`tooling/body-check.mjs` probes the body independently of adb. After the reboot:

- **adb**: disconnected (`adb devices` has no `127.0.0.1:5555`); `adb connect` alone
  does not restore it, and `winpc` is unreachable, so the USB path is unavailable.
- **bridge 3084**: open; **host 3080**: reachable; battery readable through
  `termux-battery-status` (99%).
- **`phone_*` tools**: still time out, i.e. they route through the adb path rather
  than through the app bridge. The body therefore has *two* channels on paper but
  only one motor path in practice.
- **App bridge (direct probe)**: `tooling/bridge-probe.mjs` reaches
  `ws://127.0.0.1:3084/device-tools` and the client exposes
  `assistant_screen_status`, `assistant_screen_dump`, `assistant_screen_capture`,
  `assistant_screen_click/tap/type` and `assistant_screen_global`. But
  `assistant_screen_status` returns `{"enabled":false}` and a dump reports
  "no active window": the accessibility/MediaProjection channel is installed but
  not enabled. Enabling it once is the cheapest adb-independent motor path.

Consequence: the immediate work item is to give the app bridge a real
adb-independent motor path (accessibility taps / MediaProjection capture already
exist in the client), and make `phone-eval` able to run over either backend. Until
then a reboot strands motor control, which is exactly the embodied failure the
safety invariants are meant to prevent.

## 3. Gap → action

| Gap | Existing option | Decision |
| --- | --- | --- |
| Task-level phone control with verification | `dsh-mobile-gui-agent` (ADB + UIAutomator, observe→act→verify, stale checks, approvals) | **Reference, then extend our bridge** with semantic hierarchy capture, post-action verification and stuck detection. Do not install it as-is: it assumes a desktop adb host and a separate Web panel, while our device bridge already owns the phone. |
| Vision for image-only UI | `dsh-vision-android`, `modlens` | Install nothing yet; our model accepts images, so screenshots already work. Revisit if we hit canvas/game UI. |
| Mobile console shell | `dsh-web-ui` (mobile remote) | Uses a different architecture (desktop web). Our WebView shell stays; borrow layout ideas only. |
| Behavioural evaluation | AndroidWorld (academic), no DSH plugin | **Build**: a small task suite + runner (`tooling/phone-eval.mjs`) with 5–8 deterministic tasks and success checks. |
| Run/tool tracing | `dsh-trace`, OpenTelemetry GenAI conventions | **Build a minimal local JSONL trace** now; consider `dsh-trace`/Langfuse export later. |
| Cost/latency visibility | `dsh-dashboard`, `dsh-cost-meter` | Optional install after tracing; not core. |
| Engineering discipline | `dsh-review-skills`, `dsh-superpowers` | Try `dsh-superpowers`/`dsh-review-skills` as skills; keep our own anti-loop rules authoritative. |
| Declarative tool policy | `dsh-tool-policy`, `dsh-permission-rules` | Keep our policy; borrow the "default deny + explicit allow" framing in docs. |
| Self-modifying plugins | `dsh-evolve` | Reference only; too risky for the current trust level. |

## 4. Recommended order

1. **Phone evaluation harness (P0)** — 5–8 tasks with deterministic success
   predicates (open Settings/Wi-Fi, toggle Bluetooth, open a URL, send a message
   draft, screenshot-and-verify a text). Run before/after changes; store results
   as evidence. This turns "phone ability" into something measurable.
2. **Observe/act/verify upgrade for the device bridge (P0)** — semantic UI
   hierarchy with element ids, stale-target rejection, post-action screen
   stabilisation, stuck detection, bounded retries. Reference:
   `dsh-mobile-gui-agent`'s provider contract.
3. **Local run trace (P1)** — one JSONL per autonomous run: turn boundaries, tool
   calls with arguments/结果摘要, model usage; a `tooling/trace-report.mjs` view.
   Aligns with OpenTelemetry GenAI naming.
4. **Memory retrieval probe (P1)** — a small set of questions with known answers,
   run against Mnemon recall; records precision/recall over time.
5. **Skill discipline (P2)** — install one community skill pack and compare
   behaviour on a review/plan task; keep only if it improves outcomes.
6. **Mobile polish (P2)** — remaining touch-target/safe-area work.

## 5. What we will not build

- A second mobile-control framework parallel to the device bridge.
- A desktop-style console or a new chat UI.
- A custom telemetry backend before there is one local trace to export.

## 6. First concrete step

`tooling/phone-eval.mjs`:
- task list in `tooling/phone-eval.tasks.json` (id, goal, steps, success check),
- driven through the existing `phone_*` bridge tools,
- writes `behavior/phone-eval-<date>.jsonl` with pass/fail and evidence ids,
- run manually or from a develop turn (read-only on the phone, no purchases or
  messages to real contacts).
