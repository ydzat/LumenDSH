#!/data/data/com.termux/files/usr/bin/bash
set -eu
candidate=${1:?Pass the isolated candidate root}
case "$candidate" in
  "$HOME"/sliver-wolf-dsh-upgrade/0.2.1-rc.3) ;;
  *) echo 'Expected isolated rc.3 candidate' >&2; exit 1 ;;
esac
addon="$candidate/runtime/node_modules/@deepseek-ai/node-addon-system"
mkdir -p "$addon/bin/android-arm64"
clang -shared -fPIC -O2 -DNAPI_VERSION=8 -I "$PREFIX/include/node" \
  "$addon/src/flock.c" -o "$addon/bin/android-arm64/system.node"
sha256sum "$addon/src/flock.c" "$addon/bin/android-arm64/system.node"
