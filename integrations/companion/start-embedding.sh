#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
root="$HOME/sliver-wolf-companion"
model="$root/models/Qwen3-Embedding-0.6B-Q8_0.gguf"
if curl -fsS --max-time 2 http://127.0.0.1:3083/health >/dev/null 2>&1; then exit 0; fi
exec 9>"$root/embedding-start.lock"
flock -w 60 9
if ! tmux has-session -t =sw-embedding 2>/dev/null; then
  test -f "$model"
  tmux new-session -d -s sw-embedding "exec llama-server -m '$model' --alias sw-qwen3-embedding-0.6b-q8 --embedding --pooling last --host 127.0.0.1 --port 3083 -c 2048 -b 2048 -ub 2048 -np 1 -t 4 --sleep-idle-seconds 60 --log-disable"
fi
for i in $(seq 1 60); do
  if curl -fsS --max-time 2 http://127.0.0.1:3083/health >/dev/null 2>&1; then exit 0; fi
  sleep 1
done
printf '%s\n' 'Local semantic embedding service failed to become ready' >&2
exit 1
