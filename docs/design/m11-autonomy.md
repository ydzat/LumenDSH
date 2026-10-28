# M11: sustainable autonomy

Status: opened 2026-10-28. Predecessor: M10 (foundation, 0.10.x). This milestone
turns "the agent can work autonomously" into "autonomy works in daily use".

## Why this milestone

M10 proved each capability separately: the voice loop, the console, the autonomy
policy, the phone runner, the trace. The gap is not another feature — it is that
runs keep yielding to queued continuation messages, background wake is off, and
nothing reviews what autonomy actually did. M11 is about closing those loops.

## Scope

| Priority | Item | Done when |
| --- | --- | --- |
| P0 | **Autonomous runs complete** | the loop no longer gets interrupted by queued nudges; runs end `completed`, not `yielded-to-user` |
| P0 | **Real phone work** | `phone_task` performs a real read-only task daily with evidence in `behavior/phone-task-*.jsonl`; add `expectGone` and `swipe/scroll` steps |
| P0 | **Voice loop reliability** | background wake (opt-in) works on the lock screen; continuous conversation and tap-to-interrupt survive a real session; console read-aloud setting behaves |
| P0 | **Presence on the habitat** | the last activities are readable and tappable; the console entry is obvious; no client-drawn buttons on the official page |
| P1 | **Review loop** | `tooling/trace-report.mjs` read weekly; a short `docs/design/m11-review-<date>.md` records what ran, what failed and the next change |

## Out of scope

- A second phone-control framework, a new desktop-style console, or another chat UI.
- Automatic purchases, calls, messages to real contacts, or account changes.
- Rewriting the official WebUI.

## Task graph

1. **Queue discipline first** (blocks everything else): make the nudge respect an
   active run and drain the queue, so a develop run reaches its own ending.
   Evidence: three consecutive `completed` runs with `start/tool/end` trace lines.
2. **Phone tasks**: extend the runner with `expectGone` and `swipe/scroll`; run one
   real read-only task per day and keep the evidence. Reference design:
   `dsh-android`'s `expect_text`/`expect_gone` (not installed).
3. **Voice**: turn on background keyword wake with the user, verify lock-screen
   wake, then tighten continuous conversation and barge-in.
4. **Presence**: habitat status line already shows autonomy; add a readable last-N
   activities view (timeline exists) and a clearer console hint. Client-only change,
   no host deploy.
5. **Review**: run `trace-report`, write a short review, adjust policy cadence.

Steps 2 and 3 are independent of 1; 4 is client-only; 5 depends on 1.

## Exit criteria

- Three consecutive autonomous runs end `completed` with trace evidence.
- At least one real `phone_task` task per day for three days, each with evidence.
- Background wake verified once (user speaks with the screen locked).
- The habitat shows the recent activities and opens the timeline.
- One written trace review exists.
- All tests pass, docs updated, pushed to `agent/dev`.

## Risks

- The queued-nudge interruption is a host/queue behaviour; fixing it may touch the
  same area that caused the earlier loop. One bounded change at a time, with trace.
- Background wake has battery and privacy cost; it stays opt-in and the user
  decides.
- Third-party apps can change their UI; a failing `phone_task` must fail safely
  (it returns home) rather than retry indefinitely.

## Progress log

- 2026-10-28: develop cadence defaults were 0/0/0, so `devTick` re-fired two seconds
  after each run and produced runs about every 15 seconds. Defaults are now
  30 min gap / 15 min idle / 3 per day, and the live policy was corrected
  (`831d9ef`).
- 2026-10-28: the nudge queued a synthetic "continue" message while autonomy was
  enabled; the next reserved run saw it as human input and ended
  `yielded-to-user` at step 0-1. The nudge now skips when a run is active and when
  autonomy is enabled (`f27fd73`).
- Verified: manual run `78c9d8c9` is executing tools (`trace-report` shows
  `develop | 78c9d8c9 | running | bash×2`) instead of yielding.
- 2026-10-28: the "automatic task" seen in the official UI was the host's own
  startup nudge (`scheduleStartupNudge`) posting a stale M10 continuation whenever
  the restart flag existed. It now clears the flag and stays silent while autonomy
  is enabled, never posts during an active run, and uses M11 wording otherwise
  (`6fa4578`).
- 2026-10-28: `phone_task` gained `swipe` and `expectGone`; both verified on device
  in real tasks: WLAN page → back → `expectGone` on the SSID → home (TASK OK), and
  Settings → swipe up → `expect` on a lower entry → home (TASK OK).
- 2026-10-28: the habitat status line now shows the live activity: 正在：<activity> <time> while a run is active, 最近：<activity> <time> otherwise. Client-only, installed as 0.10.53 (109), verified on device (`c3bd9d6`).
