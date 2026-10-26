#!/data/data/com.termux/files/usr/bin/bash
# Start the supervised relay + slot stack unless the entrance already answers or
# a supervisor is already alive. Safe to call from agent-start or Termux:Boot.
set -uo pipefail
LOG="$HOME/.local/state/sliver-wolf-stack/boot.log"
mkdir -p "$HOME/.local/state/sliver-wolf-stack"
exec >> "$LOG" 2>&1
echo "=== boot-stack $(date -Is) ==="

if command -v curl >/dev/null 2>&1 && curl -s -o /dev/null -m 3 http://127.0.0.1:3080/; then
  echo "entrance already answers"; exit 0
fi
PIDFILE="$HOME/.local/state/sliver-wolf-stack/supervisor.pid"
if [ -f "$PIDFILE" ]; then
  PID=$(cat "$PIDFILE" 2>/dev/null || true)
  if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then echo "supervisor $PID already alive"; exit 0; fi
fi
# A legacy direct launcher would fight for the stable port.
if command -v tmux >/dev/null 2>&1 && tmux has-session -t =dsh 2>/dev/null; then
  tmux kill-session -t dsh
fi

DEV="$HOME/sliver-wolf-dev"
RUNTIME=/data/data/com.termux/files/home/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime
setsid nohup node "$DEV/integrations/supervisor/supervise.mjs" --port 3080 \
  --interval-ms 5000 --timeout-ms 3000 --fail-threshold 3 --startup-grace-ms 30000 \
  --state-dir "$HOME/.local/state/sliver-wolf-stack" -- \
  node "$DEV/integrations/supervisor/stack.mjs" --port 3080 --slot-name A --slot-port 3082 \
  --runtime "$RUNTIME" --home "$HOME/.dsh" --workspace "$HOME/agent-workspace" \
  --state-dir "$HOME/.local/state/sliver-wolf-slots" \
  --relay-dir "$HOME/.local/state/sliver-wolf-supervisor" >/dev/null 2>&1 &
echo "supervisor scheduled pid=$!"
for i in $(seq 1 45); do
  if command -v curl >/dev/null 2>&1 && curl -s -o /dev/null -m 2 http://127.0.0.1:3080/; then
    echo "entrance ready"; exit 0
  fi
  sleep 1
done
echo "entrance not ready (supervisor may still be starting)"; exit 0
