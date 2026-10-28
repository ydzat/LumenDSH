#!/data/data/com.termux/files/usr/bin/bash
# Start the supervised relay + slot stack unless the entrance already answers or
# a supervisor is already alive. Safe to call from agent-start or Termux:Boot.
set -uo pipefail
PORT="${LUMENDSH_PORT:-3080}"
STACK_STATE="${LUMENDSH_STACK_STATE:-$HOME/.local/state/sliver-wolf-stack}"
SLOTS_DIR="${LUMENDSH_SLOTS_DIR:-$HOME/.local/state/sliver-wolf-slots}"
RELAY_DIR="${LUMENDSH_RELAY_DIR:-$HOME/.local/state/sliver-wolf-supervisor}"
SLOT_PORT="${LUMENDSH_SLOT_PORT:-3082}"
DEV="${LUMENDSH_DEV:-$HOME/sliver-wolf-dev}"
RUNTIME="${LUMENDSH_RUNTIME:-/data/data/com.termux/files/home/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime}"
LOG="$STACK_STATE/boot.log"
mkdir -p "$STACK_STATE"
exec >> "$LOG" 2>&1
echo "=== boot-stack $(date -Is) port=$PORT ==="

if command -v curl >/dev/null 2>&1 && curl -s -o /dev/null -m 3 "http://127.0.0.1:$PORT/"; then
  echo "entrance already answers"; exit 0
fi
PIDFILE="$STACK_STATE/supervisor.pid"
if [ -f "$PIDFILE" ]; then
  PID=$(cat "$PIDFILE" 2>/dev/null || true)
  if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then echo "supervisor $PID already alive"; exit 0; fi
fi
# A legacy direct launcher would fight for the stable port.
if command -v tmux >/dev/null 2>&1 && tmux has-session -t =dsh 2>/dev/null; then
  tmux kill-session -t dsh
fi

setsid nohup node "$DEV/integrations/supervisor/supervise.mjs" --port "$PORT" \
  --interval-ms 5000 --timeout-ms 8000 --fail-threshold 3 --startup-grace-ms 180000 \
  --state-dir "$STACK_STATE" -- \
  node "$DEV/integrations/supervisor/stack.mjs" --port "$PORT" --slot-name A --slot-port "$SLOT_PORT" \
  --runtime "$RUNTIME" --home "$HOME/.dsh" --workspace "$HOME/agent-workspace" \
  --state-dir "$SLOTS_DIR" --relay-dir "$RELAY_DIR" >/dev/null 2>&1 &
echo "supervisor scheduled pid=$!"
for i in $(seq 1 150); do
  if command -v curl >/dev/null 2>&1 && curl -s -o /dev/null -m 2 "http://127.0.0.1:$PORT/"; then
    echo "entrance ready"; exit 0
  fi
  sleep 1
done
echo "entrance not ready (supervisor may still be starting)"; exit 0
