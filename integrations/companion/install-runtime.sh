#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
root="$HOME/sliver-wolf-companion"
mkdir -p "$root/downloads" "$root/bin"
archive="$root/downloads/mnemon_0.2.12_linux_arm64.tar.gz"
if [ ! -f "$archive" ]; then
  curl -fL --retry 2 -o "$archive" https://github.com/mnemon-dev/mnemon/releases/download/v0.2.12/mnemon_0.2.12_linux_arm64.tar.gz
fi
printf '%s  %s\n' f0324a637ab47394f8fcd5bdb3a4f36adcaa40db7b89b496877337928ef1db31 "$archive" | sha256sum -c -
tar -xzf "$archive" -C "$root/bin" mnemon LICENSE
bash "$(dirname "$0")/install-semantic.sh"
# Profile changes and service restart are explicit operations in activate.mjs.
mkdir -p "$root/probe-home/profiles/web" "$root/probe-workspace"
if [ ! -f "$root/probe-home/profiles/web/package.json" ]; then
  cp "$HOME/.dsh/profiles/web/package.json" "$root/probe-home/profiles/web/package.json"
  cp "$HOME/.dsh/profiles/web/cordis.yml" "$root/probe-home/profiles/web/cordis.yml"
fi
DSH_HOME="$root/probe-home" dsh plugin --profile web add dsh-mnemon@0.5.34 --ignore-scripts --save-exact
