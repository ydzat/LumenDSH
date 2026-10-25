#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${ANDROID_JAR:?}" "${PET_FIXTURES:?}"
output="$(mktemp -d "$root/artifacts-build-pets-XXXXXXXX")"
mkdir -p "$output/classes" "$output/dex"
javac --release 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$output/classes" \
    "$root/apps/android/src/local/sliverwolf/assistant/PetStore.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/AssistantProfile.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/AttentionQueue.java" \
    "$root/apps/android/src/local/sliverwolf/assistant/PetSprite.java" \
    "$root/tests/android/PetImportChecks.java" \
    "$root/tests/android/ProfileChecks.java"
jar cf "$output/classes.jar" -C "$output/classes" .
d8 --min-api 29 --lib "$ANDROID_JAR" --output "$output/dex" "$output/classes.jar"
CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.PetImportChecks "$PET_FIXTURES" "$output"
CLASSPATH="$output/dex/classes.dex" /system/bin/app_process / local.sliverwolf.assistant.ProfileChecks "$PET_FIXTURES" "$output"
