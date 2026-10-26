# M9.1 supervisor

`supervise.mjs` is the Termux-side watchdog for the DSH launcher. It is a
plain Node process outside DSH: it starts the launcher, probes the stable HTTP
port and restarts the launcher with capped backoff when it crashes or stops
answering. It never reads sessions or credentials.

## Run

```bash
node integrations/supervisor/supervise.mjs --port 3080 -- \
  node /path/to/runtime/serve.mjs /path/to/runtime "$HOME/.dsh" "$HOME/agent-workspace" 3080
```

Any HTTP answer from the probe (including `401`) counts as healthy; connection
refusal and timeouts do not.

## State

- `~/.local/state/sliver-wolf-supervisor/supervisor.pid` — exclusive lock
- `~/.local/state/sliver-wolf-supervisor/status.json` — atomic status record
- `~/.local/state/sliver-wolf-supervisor/supervisor.log` — restart/health lines

## Test

```bash
node tests/supervise-check.mjs
```

The test uses mock children on a scratch port and a temporary state directory;
it never restarts the live DSH launcher.
