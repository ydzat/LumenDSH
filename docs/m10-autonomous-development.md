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
