# M9 — self-iteration infrastructure

Status: in progress. This milestone builds the local machinery that lets the
same agent rebuild, restart and upgrade the stack it runs on without losing the
conversation, and then use that machinery inside a bounded autonomous activity.

The pieces are deliberately ordered: a watchdog first, a stable entrance and
slot switching second, a rollback slot and scheduled self-development third.

## Why

The phone runs everything in one place: the Android client, the DSH web backend
and the agent session. An agent edit that restarts DSH naively can remove its
own transport, so self-iteration needs a way to restart the stack while a
healthy instance keeps serving. The existing launcher already anticipates two
slots (`serve.mjs` accepts ports 3080 and 3082) and the client already has
connection recovery and backend pairing.

## M9.1 — watchdog and automatic restart

`integrations/supervisor/supervise.mjs` runs *outside* DSH (Termux tmux/boot
loop) and supervises the launcher process:

- exclusive PID lock so two supervisors never fight;
- HTTP health probe on the stable port (any HTTP answer, including 401, counts
  as alive; connection refusal and timeouts do not);
- after a failure threshold the child is terminated and restarted with capped
  exponential backoff;
- restart and health counters are persisted to an atomic status file;
- SIGINT/SIGTERM stop the child and release the lock.

Acceptance: `tests/supervise-check.mjs` proves crash-restart, crash-while-idle
restart, unhealthy-child restart and lock behaviour with a mock child. The live
DSH launcher is never restarted by these tests.

## M9.2 — stable entrance and A/B slot switching

A relay owns the stable address the client already knows. Slots are independent
DSH instances over the same `DSH_HOME`, so they resume the same sessions and
memory.

- A serves; B is started with the candidate version and must pass health +
  session-resume checks before the relay switches;
- switching is idle-gated: an in-flight turn is allowed to finish (or is
  explicitly abandoned and recorded, never silently replayed);
- the previous slot stays alive as the rollback target; an unhealthy candidate
  switches back without touching the client;
- the launcher's pairing cookie is re-established for the active slot.

Acceptance: a controlled cycle A→B→A with the same session visible afterwards,
and a forced B failure that returns to A.

Verified (2026-10-26): `tests/upgrade-check.mjs` runs two real slots over a
copied `DSH_HOME` plus a relay and a third rollback slot: A→B switch with a
durable receipt, B→A cycle, and a killed target whose relay returns to slot C.
The live cutover from the direct 3080 launcher to the relay architecture is the
remaining step.

Verified so far (2026-10-26): the relay follows `active.json` and drops existing
connections on a switch. A slot started over a copied `DSH_HOME` served a cookie
whose authority follows the request `Host` header, and a live client cookie from
the stable port was accepted by that slot with a `Host` override — instances
sharing a `DSH_HOME` share the signing key, so a slot switch does not require
re-pairing. `tests/relay-check.mjs` and `tests/slots-check.mjs` cover the
mechanics; the live A/B cycle over the production home is still pending.

## M9.3 — rollback slot and upgrade orchestration

A third slot holds the last known-good version while an upgrade is evaluated.
`integrations/supervisor/upgrade.mjs` orchestrates: build/install candidate into
an idle slot, verify, switch, observe, and either keep or roll back. The
orchestrator records a durable receipt (versions, hashes, switch times, head of
the session log) so a crash mid-switch is recoverable on boot.

## `develop` autonomous activity (implemented, activation gated)

Implemented in `integrations/companion/policy.mjs` and `host.mjs`, with a third
toggle in the client companion settings:

- activity whitelist accepts `develop` next to `consolidate` and `reflect`;
- `TurnBudget` gets an activity-scoped tool surface: repository reads
  (`read/glob/grep`), writes (`write/edit`) and `bash` restricted to an
  allowlisted command shape (git status/diff/log/add/commit/push/switch/checkout,
  node/bash under tooling or tests, ls/cat/mkdir, bounded artifact removal);
- explicit refusals: pushing `main`, force-push, network/upload/privilege
  commands, any other tool;
- writes and publications share the write budget, all tools share the tool-call
  budget, and seconds/steps keep their existing caps;
- `activityPrompt('develop')` states the branch, the red lines and the expected
  evidence.

Unit tests cover the whitelist, refusals, budget accounting and prompt content
(`tests/companion-policy.test.mjs`). A client build with the third toggle
compiles. Activating the expanded activity requires installing the new host
version (and the rebuilt APK for the toggle) — an explicit user decision, not a
side effect of the code landing.

## Boundaries

- `main` is read-only for the agent; `agent/dev` is the working branch.
- DSH may only be restarted through the supervisor/relay path.
- APK installation and policy expansion require explicit user consent.
- Every autonomous run leaves a factual outcome and evidence path; failures stop
  the loop instead of retrying unboundedly.

## Acceptance summary

| Item | Evidence |
| --- | --- |
| M9.1 watchdog | `tests/supervise-check.mjs` output, status file |
| M9.2 A/B switch | controlled switch log, same-session check, forced rollback |
| M9.3 rollback slot | upgrade receipt and cycle evidence |
| develop activity | unit tests for guard/prompt; activation pending user decision |
