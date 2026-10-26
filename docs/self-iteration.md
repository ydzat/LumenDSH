# Self-development loop

Status: implemented in `agent/dev`. The development activity is no longer a timer
that always wakes into a task list. It is a loop over real experience:

```
work → experience stream → candidates → quiet or wake → situation → action → growth
```

The reference points are [dsh-fish](https://github.com/ssamurias/dsh-fish) (event
catalogue, generator scoring, quiet as a valid outcome, identity as a document)
and [self-iteration-forge](https://github.com/csiroqa/self-iteration-forge)
(capability gap → scaffold → implement → verify → commit).

## Data (append-only, under the companion directory)

| File | Owner | Meaning |
| --- | --- | --- |
| `experience.jsonl` | host + agent | what actually happened: failures, corrections, friction, results, observations, capability gaps, extensions, reviews |
| `self-notes.jsonl` | agent | needs, curiosity, ideas and friction the agent records itself |
| `behavior.json` | user | policy (windows, cadence, activities) |
| `state.json` | host | runs, growth, plans |
| `self-doc.md` | host | derived identity document (also injected live) |
| `extensions/registry.json` | host | scaffolded modules and their status |

`integrations/companion/experience.mjs` validates and appends records; readers are
tolerant of malformed lines and report a `skipped` count. Nothing is rewritten in
place.

## Candidates

`integrations/companion/candidates.mjs` derives candidates from experience:

- unresolved `failure` / `correction` (14 days, not referenced by a later result)
- `capability-gap` (30 days)
- repeated `friction` with the same tag (a pattern, not one annoyance)
- open self-notes
- a completed development run with no follow-up record

Each candidate gets a score from kind, freshness, severity, repetition and a penalty
for recent attempts. `pickCandidate()` skips candidates attempted in the last three
runs. **If no candidate qualifies the host records `lastQuietAt` and does not
wake** — quiet is a correct result, not a failure.

## Wake

`host.mjs` builds a bounded situation (open notes, recent run outcomes, last error)
and appends the selected candidate's situation. `activityPrompt('develop', record,
situation)` asks the agent to decide what, if anything, is worth doing; it does not
prescribe a task. The run record keeps `candidateId` and `candidateKind`.

## Agent tools

Registered directly on the host tool registry with plain JSON Schema (no
`@deepseek-ai/dsh-tools` import):

| Tool | Effect |
| --- | --- |
| `self_experience` | append one experience record |
| `self_note` / `self_close` | append or close a self-note |
| `self_stance` | from stage 3, record a stance backed by evidence ids |
| `extend_self` | scaffold a new module (README, entry point, contract test, registry entry) |

The autonomous tool guard admits these alongside repository reads/writes and the
allowlisted command shape; each counts against the write budget.

## Growth

`integrations/companion/growth.mjs` tracks day, stage and stances:

| Stage | Name | Bottleneck |
| --- | --- | --- |
| 1 | 观察 | record an observed friction or gap |
| 2 | 自修 | complete a development run against a failure or correction |
| 3 | 自造 | create and verify a new module/tool |
| 4 | 自审 | review and evaluate one of your own changes |

Stances are capped at 12, need evidence, and only appear from stage 3. Closed notes
sharing a repeated tag also promote a stance. Growth is recomputed on every
experience write and on startup.

## Identity document

`integrations/companion/self-doc.mjs` builds a bounded document from facts only:
identity, hard rules, day/stage/bottleneck, stances, open needs, recent experience,
available tools. It is injected as the `sliver-wolf-self-doc` system-prompt section
for the selected conversation, so the wake message does not have to repeat rules
or personality.

## Tests

`tests/self-iteration.test.mjs` covers the experience stream, candidate derivation
and quiet mode, attempt skipping, growth bottlenecks, stance rules, self-document
bounds and extension scaffolding. `tests/companion-policy.test.mjs`,
`tests/develop-situation.test.mjs`, `tests/self-notes.test.mjs` and
`tests/situation.test.mjs` cover the guard, prompt and ledger behaviour.
