#!/data/data/com.termux/files/usr/bin/bash
# Restore the on-device adb loopback after a reboot, when the phone is tethered
# to the PC. Booting resets adbd to USB mode, so the local client cannot connect
# until a host re-enables TCP mode once.
set -uo pipefail
if adb -s 127.0.0.1:5555 get-state 2>/dev/null | grep -q '^device$'; then
  echo "local adb already connected"
  exit 0
fi
if ! ssh -o BatchMode=yes -o ConnectTimeout=6 winpc "adb devices" >/dev/null 2>&1; then
  echo "no adb host reachable; skip"
  exit 1
fi
ssh -o BatchMode=yes winpc "adb tcpip 5555" >/dev/null 2>&1 || true
sleep 2
adb connect 127.0.0.1:5555 >/dev/null 2>&1 || true
for i in $(seq 1 15); do
  if adb -s 127.0.0.1:5555 get-state 2>/dev/null | grep -q '^device$'; then
    echo "local adb restored"
    exit 0
  fi
  sleep 2
done
echo "local adb still unavailable"
exit 1
