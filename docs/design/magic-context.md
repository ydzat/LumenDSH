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

## Phone-width UI: taskbar-style overflow

The composer dock stays where it is and keeps the official figures; a chevron
pinned to the bottom-right corner (the Windows taskbar overflow analogue) opens a
small flyout listing the items that do not fit there. The rule is structural, not
per-plugin: any dock child that carries the plugin surface marker
(`data-mc-surface`, the shell contract plugin pills use) moves to the tray, so a
plugin pill can never sit on top of the official meter.

- collapsed: dock as usual plus a `⌃` button in the bottom-right corner;
- open: a compact flyout above the button with one line per hidden item;
- no gestures; the button toggles it (bottom swipes close apps on this phone).

## Rollback

Remove the two patch rows above, `dsh plugin --profile web remove
dsh-plugin-magic-context`, restart the slot. The plugin's stores live under
`~/.local/share/cortexkit/magic-context-dsh-poc/` and are left untouched.
