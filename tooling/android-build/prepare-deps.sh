#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
: "${DEPS_DIR:?Set DEPS_DIR outside the source tree}"
mkdir -p "$DEPS_DIR"
fetch() {
    local path="$1" hash="$2" file="$DEPS_DIR/${1##*/}"
    if [ ! -f "$file" ]; then curl -fL --max-time 120 -o "$file" "https://repo.maven.apache.org/maven2/$path"; fi
    printf '%s  %s\n' "$hash" "$file" | sha256sum -c -
}
fetch org/java-websocket/Java-WebSocket/1.6.0/Java-WebSocket-1.6.0.jar eae29213e4f16515639c28957200f011b3967fffcada1962cf0255d24919c22f
fetch org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar e7c2a48e8515ba1f49fa637d57b4e2f590b3f5bd97407ac699c3aa5efb1204a9
