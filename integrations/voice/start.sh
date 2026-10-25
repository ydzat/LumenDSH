#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
root="$HOME/lumendsh-voice"
test -f "$root/serve.py"
backend="${LUMENDSH_VOICE_BACKEND:-hoyotts}"
if [ "$backend" = hoyotts ]; then
    test -x "$root/hoyo_native"
    models="$root/hoyo-models"
elif [ "$backend" = gpt-sovits ]; then
    test -x "$root/gpt_sovits_demo"
    models="$root/models"
else
    echo 'Unknown character voice backend' >&2
    exit 1
fi
if ! tmux has-session -t lumendsh-voice 2>/dev/null; then
    tmux new-session -d -s lumendsh-voice "python '$root/serve.py' --root '$root' --models '$models' --backend '$backend'"
fi
python - "$root/token" "$backend" <<'PY'
import json, pathlib, sys, time, urllib.request
for attempt in range(20):
    try:
        token = pathlib.Path(sys.argv[1]).read_text().strip()
        request = urllib.request.Request('http://127.0.0.1:3086/health', headers={'Authorization': 'Bearer '+token})
        with urllib.request.urlopen(request, timeout=2) as response:
            data=json.load(response)
        assert data['engine']=='lumendsh.silver_wolf.local'
        assert data.get('backend') == sys.argv[2]
        print('CHARACTER_VOICE_READY')
        break
    except (OSError, AssertionError):
        if attempt == 19: raise SystemExit('Character voice service did not become ready')
        time.sleep(.25)
PY
