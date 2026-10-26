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

## Slots (M9.2)

`slots.mjs` starts an independent `dsh web` instance over a shared `DSH_HOME`
and mints a cookie whose authority is the stable entrance port, so the client's
existing cookie stays valid across a switch.

```bash
node integrations/supervisor/slots.mjs start --name B --port 3082 \
  --runtime "$RUNTIME" --home "$HOME/.dsh" --workspace "$HOME/agent-workspace" \
  --stable-port 3080 --state-dir "$HOME/.local/state/sliver-wolf-slots"
node integrations/supervisor/slots.mjs switch --name B \
  --state-dir "$HOME/.local/state/sliver-wolf-slots" \
  --relay-dir "$HOME/.local/state/sliver-wolf-supervisor"
node integrations/supervisor/slots.mjs stop --name B \
  --state-dir "$HOME/.local/state/sliver-wolf-slots"
```

Verified behaviour: the cookie authority follows the request `Host` header, and
instances sharing a `DSH_HOME` share the cookie signing key — a live client
cookie from the stable port was accepted by the slot with a `Host` override.
