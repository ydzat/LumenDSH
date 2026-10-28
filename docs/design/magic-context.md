# Magic Context on DSH (context management)

Status: installed and taking over compaction, 2026-10-28.

## What it is

[wk544863858/dsh-plugin-magic-context](https://github.com/wk544863858/dsh-plugin-magic-context)
v0.9.0 — a DSH port of opencode's
[magic-context](https://github.com/cortexkit/magic-context). It reclaims tool
outputs reversibly (`ctx_reduce` / `ctx_expand`), archives older history as
compartment summaries through an out-of-band historian, and keeps project
memories in its own SQLite store (`ctx_memory`).

## Install (phone, from source)

`npm run build` fails here because the `tsc` shim starts with `/usr/bin/env`
(Android has no `/usr/bin/env`). Compile directly instead:

```bash
git clone --depth 1 https://github.com/wk544863858/dsh-plugin-magic-context ~/magic-context
cd ~/magic-context
npm install --no-audit --no-fund
node node_modules/typescript/bin/tsc -p tsconfig.build.json
node scripts/copy-client.mjs
dsh plugin --profile web add ~/magic-context --ignore-scripts
```

The bundle defaults to `registerCompactionEngine: false` (conservative). To let
it own compaction, append to `~/.dsh/profiles/web/cordis.patch.yml`:

```yaml
- insert:
    - id: magic-context-dsh
      name: 'dsh-plugin-magic-context'
      config:
        registerCompactionEngine: true
        memory: {}
- insert:
    - id: compaction-basic
      name: '@deepseek-ai/dsh-compaction-basic'
      disabled: true
```

Restart the slot to load it. Verification: the activity log
`~/.local/share/cortexkit/magic-context-dsh-poc/activity.log` must contain
`CompactionEngine mounted on ctx.compaction`; the host's own `compaction-basic`
checkpoints stop appearing.

## Phone-width UI: taskbar-style expansion

The dock keeps its place and the official figures. A chevron pinned to the
bottom-right corner expands **the dock itself in place** — real child nodes, no
clones, no floating overlay: the dock wraps and scrolls inside the composer card,
so the page content above moves up instead of being covered. Collapsed, plugin
pill surfaces (`data-mc-surface`) are hidden so they cannot overlap the official
meter; expanded, they appear next to it. The chevron is the only control, because
bottom swipes close apps on this phone.

## Historian fix (empty system messages)

The historian replays a prefix of the session as an LLM request. Older sessions
contain `system/message` nodes with empty content (v2→v3 conversion artifacts);
the DeepSeek adapter rejects those once they sit in history
(`DeepSeek Messages cannot represent empty in-history system update`), so every
historian run failed at chunk 1/4 and the error count grew.

Patch in the vendored checkout (`~/magic-context/src/dsh/seams.ts`,
`spanRequestMessages`): drop `system/message` events whose text is empty from the
replay. Rebuild with `node node_modules/typescript/bin/tsc -p tsconfig.build.json`
and restart the slot.

Verified after the restart: `historian chunk 1/4 digested (importance 82)` and no
new `ERROR` rows since.

## Doctor follow-ups (2026-10-28)

`scripts/dsh-doctor.mjs` is the read-only health report MC ships; run it after any
配置 or upgrade. Verdict before the follow-ups: engine mounted, memory lane 12 rows,
historian healthy — with two open items.

1. **Vector lane could never bootstrap** (doctor §7, WARN). `resolveSharedSpace`
   closed the gate whenever the corpus had no vectors (`no dominant embedding
   space`), while `planBackfill` refused without a dominant space — for a fresh
   single-writer corpus that is a deadlock, and the 12 memories stayed at 0%
   coverage. Patched `src/dsh/memory-lane.ts` to record the first writer
   (`first-writer`) as the embedder's own identity on an empty store, and
   `src/core/vector-lane.ts` `planBackfill` accepts that `modelId` fallback.
   Parity then passes trivially (the same embedder wrote and reads), and later
   spaces still need the 0.99 cosine proof.
2. **Profile hygiene** (doctor §8, blocking). `@deepseek-ai/cosmokit` and
   `@deepseek-ai/schemastery` had been hoisted into
   `~/.dsh/profiles/web/node_modules/@deepseek-ai`. DSH prefers profile-local
   same-name packages, so a host upgrade would shadow its own copies and fail
   boot with "1 required plugin did not activate". Moved them to
   `~/agent-workspace/backup-profile-deepseek-ai-20261028`; host installation
   scope provides the same versions.

Not acted on: global domains are all disabled until the user approves members
(`/ctx-global enable`), which is by design.

## Rollback

Remove the two patch rows above, `dsh plugin --profile web remove
dsh-plugin-magic-context`, restart the slot. The plugin's stores live under
`~/.local/share/cortexkit/magic-context-dsh-poc/` and are left untouched.
