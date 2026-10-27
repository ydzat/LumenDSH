#!/data/data/com.termux/files/usr/bin/bash
# Schedule a supervised restart from inside an autonomous development turn.
# Returns immediately: the host is restarted after the turn has finished, and the
# nudge flag makes the next turn continue instead of stranding the agent.
set -euo pipefail
HOME_DIR="${HOME:-/data/data/com.termux/files/home}"
FLAG="$HOME_DIR/.local/state/sliver-wolf-m9/nudge-on-start"
mkdir -p "$(dirname "$FLAG")"
touch "$FLAG"
nohup bash -c 'sleep 20; "$HOME/agent-workspace/restart-host.sh"' >/dev/null 2>&1 &
echo "restart scheduled in 20s; continuation flag written"
