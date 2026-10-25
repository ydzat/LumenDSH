#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
root="$HOME/sliver-wolf-companion"
scripts="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$root/models" "$root/downloads" "$root/bin"
apt-get install -y --no-install-recommends llama-cpp=0.7.0 golang=3:1.27.3
model="$root/models/Qwen3-Embedding-0.6B-Q8_0.gguf"
if [ ! -f "$model" ]; then
  curl -fL --retry 2 -o "$model.part" https://huggingface.co/Qwen/Qwen3-Embedding-0.6B-GGUF/resolve/370f27d7550e0def9b39c1f16d3fbaa13aa67728/Qwen3-Embedding-0.6B-Q8_0.gguf
  printf '%s  %s\n' 06507c7b42688469c4e7298b0a1e16deff06caf291cf0a5b278c308249c3e439 "$model.part" | sha256sum -c -
  mv "$model.part" "$model"
fi
printf '%s  %s\n' 06507c7b42688469c4e7298b0a1e16deff06caf291cf0a5b278c308249c3e439 "$model" | sha256sum -c -
archive="$root/downloads/mnemon-v0.2.12-source.tar.gz"
if [ ! -f "$archive" ]; then curl -fL --retry 2 -o "$archive" https://codeload.github.com/mnemon-dev/mnemon/tar.gz/refs/tags/v0.2.12; fi
printf '%s  %s\n' da28cd49afb5c967414576485f689a0ba0127b267bb0379dcd90dd06d325b628 "$archive" | sha256sum -c -
source="$(mktemp -d "$root/semantic-source-XXXXXXXX")"
tar -xzf "$archive" -C "$source" --strip-components=1
node "$scripts/patch-mnemon-semantic.mjs" "$source"
cd "$source"
unset LD_PRELOAD
export CGO_ENABLED=0 GOOS=linux GOARCH=arm64 GOMAXPROCS=4
/system/bin/sh -c 'exec "$PREFIX/bin/go" build -p 4 -trimpath -ldflags "-X github.com/mnemon-dev/mnemon/cmd.version=0.2.12-sw-semantic.1" -o "$HOME/sliver-wolf-companion/bin/mnemon-semantic-candidate" .'
bash "$scripts/start-embedding.sh"
node "$scripts/semantic-check.mjs"
cp "$root/bin/mnemon-semantic-candidate" "$root/bin/mnemon-semantic"
cp "$scripts/start-embedding.sh" "$root/bin/start-embedding.sh"
cp "$scripts/mnemon-termux" "$root/bin/mnemon-termux"
chmod 700 "$root/bin/mnemon-termux" "$root/bin/start-embedding.sh"
"$root/bin/mnemon-termux" --version
