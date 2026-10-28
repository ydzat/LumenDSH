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
bottom-right corner (the Windows taskbar analogue) lifts the **same dock** — real
child nodes, no clones or text copies — into a floating panel above the composer
(`body.lumendsh-dock-open [data-composer-dock] { position: fixed; … }`). Inside
the panel the children wrap and scroll, so the plugin pill sits next to the
official meter instead of on top of it; collapsed, plugin pill surfaces
(`data-mc-surface`) are hidden. The button toggles the panel; no gesture is used
because bottom swipes close apps on this phone.

## Rollback

Remove the two patch rows above, `dsh plugin --profile web remove
dsh-plugin-magic-context`, restart the slot. The plugin's stores live under
`~/.local/share/cortexkit/magic-context-dsh-poc/` and are left untouched.
