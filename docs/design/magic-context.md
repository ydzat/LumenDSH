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

## Rollback

Remove the two patch rows above, `dsh plugin --profile web remove
dsh-plugin-magic-context`, restart the slot. The plugin's stores live under
`~/.local/share/cortexkit/magic-context-dsh-poc/` and are left untouched.
