#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
root="$HOME/lumendsh-voice"
test -f "$root/serve.py"
test -x "$root/gpt_sovits_demo"
if ! tmux has-session -t lumendsh-voice 2>/dev/null; then
    tmux new-session -d -s lumendsh-voice "python '$root/serve.py' --root '$root' --models '$root/models'"
fi
python - "$root/token" <<'PY'
import json, pathlib, sys, time, urllib.request
for attempt in range(20):
    try:
        token = pathlib.Path(sys.argv[1]).read_text().strip()
        request = urllib.request.Request('http://127.0.0.1:3086/health', headers={'Authorization': 'Bearer '+token})
        with urllib.request.urlopen(request, timeout=2) as response:
            data=json.load(response)
        assert data['engine']=='lumendsh.silver_wolf.local'
        print('CHARACTER_VOICE_READY')
        break
    except (OSError, AssertionError):
        if attempt == 19: raise SystemExit('Character voice service did not become ready')
        time.sleep(.25)
PY
