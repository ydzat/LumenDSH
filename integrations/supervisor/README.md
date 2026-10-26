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

Slots listen on the **internal loopback host** `127.0.0.2` (default
`--slot-host` in `stack.mjs`, per-slot `--host` in `slots.mjs`). Only the relay
listens on `127.0.0.1:3080`; clients must never connect to a slot port directly,
because two writers would contend for the same session log (`session/writer-held`).

```bash
node integrations/supervisor/slots.mjs start --name B --host 127.0.0.2 --port 3083 \
  --runtime "$RUNTIME" --home "$HOME/.dsh" --workspace "$HOME/agent-workspace" \
  --stable-port 3080 --state-dir "$HOME/.local/state/sliver-wolf-slots"
node integrations/supervisor/slots.mjs stop --name B --port 3083 \
  --state-dir "$HOME/.local/state/sliver-wolf-slots"
```

Verified behaviour: the cookie authority follows the request `Host` header, and
instances sharing a `DSH_HOME` share the cookie signing key — a live client
cookie from the stable port was accepted by the slot with a `Host` override.
A cold-boot slot needs about 36 seconds to print its token URL, so `slots start`
waits up to 120 seconds and `stack.mjs` up to 180 seconds.

## Upgrade orchestration (M9.3)

`upgrade.mjs` switches `--from` to `--to`, observes the target and writes a
durable receipt. With `--rollback-on-fail` it returns the relay to
`--rollback-to` (defaults to the source slot), which is how the third slot acts
as the last-known-good position.

```bash
node integrations/supervisor/upgrade.mjs --from A --to B \
  --state-dir "$HOME/.local/state/sliver-wolf-slots" \
  --relay-dir "$HOME/.local/state/sliver-wolf-supervisor" \
  --observe-ms 3000 --rollback-on-fail --rollback-to C
```

## Production cutover (M9.2, user-triggered)

`migrate.mjs` replaces a direct launcher on the stable port with the relay plus a
slot. Run it detached because the cutover drops the current client connection;
on failure it restores the launcher with `--restore-command`.

```bash
setsid nohup node integrations/supervisor/migrate.mjs \
  --port 3080 --slot-name A --slot-port 3082 \
  --runtime "$RUNTIME" --home "$HOME/.dsh" --workspace "$HOME/agent-workspace" \
  --stop-command "tmux kill-session -t dsh" \
  --restore-command "tmux new-session -d -s dsh ..." \
  --state-dir "$HOME/.local/state/sliver-wolf-slots" \
  --relay-dir "$HOME/.local/state/sliver-wolf-supervisor" \
  >> "$HOME/.local/state/sliver-wolf-slots/migrate.log" 2>&1
```

## Stack supervision

`stack.mjs` ensures the slot is running, writes the relay's `active.json`, then
runs the relay in the foreground. Wrap it with the watchdog so either the relay
or the slot failing restarts the whole entrance:

```bash
node integrations/supervisor/supervise.mjs --port 3080 \
  --interval-ms 5000 --timeout-ms 3000 --fail-threshold 3 --startup-grace-ms 180000 \
  --state-dir "$HOME/.local/state/sliver-wolf-stack" -- \
  node integrations/supervisor/stack.mjs --port 3080 --slot-name A --slot-port 3082 \
  --slot-host 127.0.0.2 \
  --runtime "$RUNTIME" --home "$HOME/.dsh" --workspace "$HOME/agent-workspace" \
  --state-dir "$HOME/.local/state/sliver-wolf-slots" \
  --relay-dir "$HOME/.local/state/sliver-wolf-supervisor"
```

`--startup-grace-ms` skips health probes while a fresh slot boots; without it the
watchdog would kill the stack mid-start. The value must cover a **cold-boot**
slot start (measured ~36 s on the phone; 180 s leaves margin for a cold page
cache and a slow first plugin load). The stack follows `active.json`: after an
A→B switch a watchdog restart keeps B instead of rewriting the relay back to A.

## Boot and host upgrades in the relay architecture

- `boot-stack.sh` starts the supervised stack unless the entrance already
  answers or a supervisor is alive; it waits up to 150 s for the entrance so a
  cold start is reported accurately. `agent-start` prefers it and exits when
  port 3080 already responds, so the client-driven startup no longer races the
  relay for the port.
- `~/.termux/boot/lumendsh-stack.sh` calls the same script from Termux:Boot.
  Verified on the installed HUAWEI/EMUI device: reboot → Termux:Boot →
  `boot-stack.sh` → stack; `3080` is ready without opening any app. Termux and
  its add-ons must come from the **same channel**: the installed GitHub build
  (0.118.3, `TERMUX_APK_RELEASE=GITHUB`) shares its certificate with the GitHub
  Termux:Boot/API debug APKs; F-Droid builds are signed with a different key and
  cannot be mixed (`INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`).
- `integrations/companion/activate-host-slot.mjs` upgrades the host through the
  **other slot**: it installs the revision, starts the target slot, verifies
  entrance, companion status, identical sessions and the generated self document,
  switches the relay, soaks, and then **stops the previous slot by default**: a
  stale client still attached to the old slot would otherwise write to the same
  session while the new slot holds it (`session/writer-held`). Use
  `--keep-source` only when a hot rollback point is required for the soak. The watchdog is held (a hold file with
  the installer pid) so it cannot race the installer for the slot port. A failed
  activation restores the profile, switches the relay back and stops the target;
  receipts go to `host-activation-slot.json` and `slots/receipts/`.
  `activate-host.mjs` remains for the old direct-launcher layout.
