#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
: "${DEPS_DIR:?}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
mkdir -p "$DEPS_DIR"
fetch() {
    local name="$1" hash="$2" url="$3"
    if [ ! -f "$DEPS_DIR/$name" ]; then curl -fL --retry 2 "$url" -o "$DEPS_DIR/$name"; fi
    printf '%s  %s\n' "$hash" "$DEPS_DIR/$name" | sha256sum -c -
}
fetch liblinear-2.44.jar b962746d8be60894ca8adbbe2fe556e40471e6e1d59941285901e0ceaa00eb3f https://repo.maven.apache.org/maven2/de/bwaldvogel/liblinear/2.44/liblinear-2.44.jar
fetch libsvm-3.35.jar 66793be8d30bc4a51396bd323ad9e59e37ed19a80dfa0b23eaf71dcaed398df0 https://repo.maven.apache.org/maven2/tw/edu/ntu/csie/libsvm/3.35/libsvm-3.35.jar
OWNER_FIXTURE_DIR="$root/apps/android/assets/wake" bash "$root/tooling/android-build/prepare-owner-fixtures.sh"
