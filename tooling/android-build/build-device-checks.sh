#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${ANDROID_JAR:?}" "${SIGNING_DIR:?}"
output="$(mktemp -d "$root/artifacts-build-checks-XXXXXXXX")"
mkdir -p "$output/classes" "$output/dex"
asset_args=()
if [ -n "${OWNER_FIXTURE_DIR:-}" ]; then asset_args=(-A "$OWNER_FIXTURE_DIR"); fi
aapt package "${asset_args[@]}" -f -M "$root/tests/android/AndroidManifest.xml" -I "$ANDROID_JAR" -F "$output/unsigned.apk"
javac --release 8 -classpath "$ANDROID_JAR" -d "$output/classes" "$root/tests/android/BackgroundVoiceChecks.java" "$root/tests/android/VoiceProviderChecks.java" "$root/tests/android/VoiceReplyChecks.java" "$root/tests/android/SettingsChecks.java" "$root/tests/android/TextInput.java" "$root/tests/android/ShareFixtureProvider.java" "$root/tests/android/OverlaySurface.java" "$root/tests/android/WakeChecks.java" "$root/tests/android/SpeakWakeFixture.java" "$root/tests/android/OwnerChecks.java" "$root/tests/android/OverlayChecks.java" "$root/tests/android/HabitatInteractionChecks.java" "$root/tests/android/ReleaseChecks.java" "$root/tests/android/ReleaseCrash.java"
jar cf "$output/classes.jar" -C "$output/classes" .
d8 --min-api 29 --lib "$ANDROID_JAR" --output "$output/dex" "$output/classes.jar"
(cd "$output/dex" && aapt add "$output/unsigned.apk" classes.dex)
apksigner sign --ks "$SIGNING_DIR/developer.p12" --ks-key-alias developer --ks-pass "file:$SIGNING_DIR/password" \
    --out "$output/device-checks.apk" "$output/unsigned.apk"
apksigner verify "$output/device-checks.apk"
printf 'APK=%s\n' "$output/device-checks.apk"
