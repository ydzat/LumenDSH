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

## Phone-width UI: the status drawer

The composer dock carries the official context meter and any plugin pills (Magic
Context included). In portrait they do not fit, and the official meter is placed
in the dock corner, so an in-flow pill lands on top of it. The shell plugin makes
the whole bar a drawer instead of patching one plugin's element:

- collapsed: the dock is hidden and a single `状态 ▲` handle sits above the
  composer;
- open (`body.lumendsh-dock-open`): the dock itself becomes a fixed, wrapped,
  scrollable sheet above the composer, so every current and future dock child
  (rounds, context, cache, Magic Context) shows in the drawer;
- no gesture is involved — the handle is a button, which matters because the
  bottom-of-screen swipe is the system close gesture on this phone.

Tapping `状态 ▼` collapses it again.

## Rollback

Remove the two patch rows above, `dsh plugin --profile web remove
dsh-plugin-magic-context`, restart the slot. The plugin's stores live under
`~/.local/share/cortexkit/magic-context-dsh-poc/` and are left untouched.
