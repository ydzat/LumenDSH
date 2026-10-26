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
- SIGINT/SIGTERM stop the child and release the lock;
- a startup grace (`--startup-grace-ms`) skips probes while a fresh slot boots;
- `stack.mjs` ties slot + relay together so the watchdog restarts the whole
  entrance when either side fails (verified by `tests/stack-check.mjs`).

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
The cutover itself is covered by `tests/migrate-check.mjs`: a failed slot start
leaves the direct launcher untouched, and a successful migration moves the
stable port to relay + slot with a receipt. The production cutover on 3080 is a
user-triggered operation (it drops the live client connection for a few seconds)
and is not performed by M9 code automatically.

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
(`tests/companion-policy.test.mjs`). The client was rebuilt as 0.8.3-dev (55)
with the third toggle and installed in place; the host was upgraded through the
slot-aware activation.

Development has its own cadence, separate from companionship: it starts as soon
as the assistant is idle, with no gap, no idle floor and no daily cap by default
(`develop: {gapMin: 0, idleMinutes: 0, dailyRuns: 0}` means unlimited). Per-run
ceilings are raised to 1000 steps, 5000 tool calls, 500 writes, 36000 seconds
and 65536 output tokens. A failed run stops and is recorded; the next run simply
continues. Restarts never replay an interrupted run: a recovery wake starts a
fresh turn shortly after the next allowed moment.

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
| develop activity | guard tests, installed host revision, first real run completed |
| boot persistence | `boot-stack.sh`, Termux:Boot entry, agent-start guard |
| orphan slot | `tests/stack-adopt-check.mjs` |
| unlimited cadence | `tests/companion-policy.test.mjs` (gap/cap/idle zero cases) |


## Live state (2026-10-26)

- Entrance `127.0.0.1:3080` is served by the relay; slot A listens on 3082 over
  the shared `DSH_HOME`; the watchdog restarts either side.
- Installed host revision `01292183…` with unlimited develop cadence; policy
  window is back to the companionship default 09:00–22:00 for consolidate and
  reflect.
- One real develop run completed before the unlimited deploy, changing
  `stack.mjs` so an orphaned slot listener is adopted instead of restart-looping
  (`tests/stack-adopt-check.mjs`).
- Client 0.8.3-dev (55) is installed with the develop toggle.


## Host upgrades through slots (2026-10-26)

Companion host upgrades no longer stop the live slot. `activate-host-slot.mjs`
installs the new immutable revision, starts the *other* slot on it, verifies the
target (entrance, companion status, identical session set, generated self
document), then points the relay at it. The previous slot keeps serving through
the whole flow as the rollback point; after a soak window it is retained by
default (`--stop-source` opts into stopping it).

The stack now follows `active.json`: a watchdog restart after an A→B switch keeps
B instead of rewriting the relay back to A (`stack-active-check.mjs`). A failed
activation restores the profile backup, points the relay back and stops the
target; the receipt records `rolled-back` with the error. Receipts are written to
`host-activation-slot.json` and `slots/receipts/`.

Remaining limitation: the relay closes existing connections when `active.json`
changes, so a switch still produces one short reconnect; the previous slot stays
warm so rollback is another switch, not a restart.


## Single client entrance (2026-10-26)

Slots now bind to the internal loopback host `127.0.0.2`; only the relay listens on
`127.0.0.1:3080`, which is the sole client entrance. Direct access to a slot port
is therefore impossible from the addresses clients use, which removes the
`session/writer-held` conflict caused by one client writing through a slot while
another wrote through the relay. `slots.mjs` takes `--host`, the stack starts
slots with `--slot-host` (default `127.0.0.2`), and `active.json` carries the host
the relay must dial. The Android client refuses to pair any port other than 3080.


## Closure (2026-10-26)

M9 is delivered and verified on the phone:

- **Boot**: Termux:Boot runs `~/.termux/boot/lumendsh-stack.sh`, which calls
  `boot-stack.sh`; after a reboot the stack reached `entrance ready` without any
  app being opened (`boot.log`, 13:19). A cold slot needs about 36 s, so the
  slot deadline is 120 s, the stack wait 180 s and the watchdog startup grace
  180 s (the previous 30 s values caused a restart loop after boot).
- **Single entrance**: slots bind the internal loopback `127.0.0.2` (3082/3083);
  only the relay listens on `127.0.0.1:3080`. Clients must use 3080 — a direct
  slot connection contends for the session writer and is refused with
  `session/writer-held`. The Android client pairs only 3080.
- **Host upgrades through slots**: `activate-host-slot.mjs` installs the
  revision, starts the target slot, verifies entrance/companion status/sessions/
  self document, switches the relay, soaks, and keeps the source slot for
  rollback; the watchdog is held during installation. An A→B activation completed
  with a durable receipt.
- **Termux channel**: the installed Termux is the GitHub release
  (`TERMUX_APK_RELEASE=GITHUB`, 0.118.3). Add-ons must come from the same channel;
  GitHub Termux:Boot 0.8.1 and Termux:API 0.53.0 share its certificate and are
  installed. `termux-api` 0.60.0 provides the device commands (battery status
  verified). F-Droid builds use a different key and cannot be mixed.
- **Self-development loop**: experience stream, candidate derivation with quiet
  mode, growth stages/stances, self document and `extend_self` are implemented
  and deployed. The loop is **paused by the user**; resuming it is a policy
  change, not a code change.

Remaining follow-ups (not blocking M9): re-run a live A→B switch after the
single-entrance change, with both clients on 3080; keep the Web GUI at
`http://127.0.0.1:3080` because opening a slot URL bypasses the relay.
