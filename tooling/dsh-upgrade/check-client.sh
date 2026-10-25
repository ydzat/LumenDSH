#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${ANDROID_JAR:?}" "${DEPS_DIR:?}"
output="$(mktemp -d "$root/artifacts-build-rc3-XXXXXXXX")"
mkdir -p "$output/classes" "$output/dex"
ws="$DEPS_DIR/Java-WebSocket-1.6.0.jar"
log="$DEPS_DIR/slf4j-api-2.0.13.jar"
javac --release 8 -classpath "$ANDROID_JAR:$ws:$log" -d "$output/classes" \
  "$root/apps/android/src/local/sliverwolf/assistant/DshTransport.java" \
  "$root/apps/android/src/local/sliverwolf/assistant/Rc3Protocol.java" \
  "$root/apps/android/src/local/sliverwolf/assistant/Conversation.java" \
  "$root/tests/android/Rc3ProtocolChecks.java" "$root/tests/android/Rc3LiveChecks.java"
jar cf "$output/classes.jar" -C "$output/classes" .
d8 --min-api 29 --lib "$ANDROID_JAR" --output "$output/dex" "$output/classes.jar" "$ws" "$log"
CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.Rc3ProtocolChecks
if [ "${1:-}" = "--live" ]; then
  CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.Rc3LiveChecks "$2" "$3" "${4:-}"
fi
