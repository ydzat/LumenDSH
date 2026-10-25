#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${ANDROID_JAR:?}"
output="$(mktemp -d "$root/artifacts-build-parser-XXXXXXXX")"
mkdir -p "$output/classes" "$output/dex"
javac --release 8 -classpath "$ANDROID_JAR" -d "$output/classes" \
    "$root/apps/android/src/local/sliverwolf/assistant/Conversation.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/AttentionQueue.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/HabitatFeed.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/HabitatState.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/PresentationSettings.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/NotificationArchive.java" \
    "$root/tests/android/NotificationChecks.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/ScheduledTaskStore.java" \
    "$root/tests/android/ScheduleChecks.java" \
    "$root/tests/android/ConversationChecks.java" "$root/tests/android/HabitatChecks.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/VoiceReply.java" "$root/tests/android/VoiceReplyChecks.java"
jar cf "$output/classes.jar" -C "$output/classes" .
d8 --min-api 29 --lib "$ANDROID_JAR" --output "$output/dex" "$output/classes.jar"
CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.ConversationChecks
CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.HabitatChecks "$output"

CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.NotificationChecks "$output"

CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.ScheduleChecks "$output"

CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.VoiceReplyChecks
