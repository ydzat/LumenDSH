#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
source_root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${ANDROID_JAR:?Set ANDROID_JAR to the verified Android 29 platform jar}"
: "${SIGNING_DIR:?Set SIGNING_DIR to a private persistent directory outside the source tree}"
: "${DEPS_DIR:?Set DEPS_DIR to the pinned dependency jar directory}"
bash "$source_root/tooling/android-build/prepare-deps.sh"
bash "$source_root/tooling/android-build/prepare-default-pet.sh"
bash "$source_root/tooling/android-build/prepare-wake.sh"
bash "$source_root/tooling/android-build/prepare-owner-backends.sh"
dependencies=("$DEPS_DIR/Java-WebSocket-1.6.0.jar" "$DEPS_DIR/slf4j-api-2.0.13.jar" "$DEPS_DIR/wake-1.13.8/java.jar" "$DEPS_DIR/liblinear-2.44.jar" "$DEPS_DIR/libsvm-3.35.jar")
dependency_classpath="${dependencies[0]}:${dependencies[1]}:${dependencies[2]}:${dependencies[3]}:${dependencies[4]}"
for tool in aapt javac jar d8 apksigner keytool; do command -v "$tool" >/dev/null; done
test -f "$ANDROID_JAR"
printf '%s  %s\n' d4ce0daae1695abc7497009c61237f774f52c6ffdc222784e03f60effb917532 "$ANDROID_JAR" | sha256sum -c -
for spec in aapt=16.0.0.4-4 d8=37.0.0 apksigner=37.0.0 openjdk-21=21.0.13; do
    actual="$(dpkg-query -W -f='${Version}' "${spec%%=*}")"
    [ "$actual" = "${spec#*=}" ] || { echo "Toolchain version mismatch: $spec" >&2; exit 1; }
done
mkdir -p "$SIGNING_DIR"
case "$(cd "$SIGNING_DIR" && pwd)/" in
    "$source_root/"*) echo 'Signing directory must be outside the source tree.' >&2; exit 1 ;;
esac
if [ ! -f "$SIGNING_DIR/developer.p12" ]; then
    if [ -e "$SIGNING_DIR/password" ]; then
        echo 'Incomplete signing identity; inspect manually before continuing.' >&2
        exit 1
    fi
    head -c 48 /dev/urandom | base64 > "$SIGNING_DIR/password"
    keytool -genkeypair -keystore "$SIGNING_DIR/developer.p12" -storetype PKCS12 \
        -storepass:file "$SIGNING_DIR/password" -alias developer -keyalg RSA \
        -keysize 3072 -validity 10000 -dname 'CN=Sliver Wolf Local Developer' >/dev/null
fi
test -f "$SIGNING_DIR/password"
build_dir="$(mktemp -d "$source_root/artifacts-build-XXXXXXXX")"
find "$source_root/apps/android" -type f \( -name '*.java' -o -name '*.xml' -o -name '*.txt' -o -name '*.png' \) -print0 \
    | sort -z | xargs -0 sha256sum > "$build_dir/source-sha256.txt"
sha256sum "$source_root/apps/android/assets/default-pet/"* "$source_root/apps/android/assets/wake/"* >> "$build_dir/source-sha256.txt"
sha256sum "$ANDROID_JAR" "${dependencies[@]}" "$DEPS_DIR/wake-1.13.8/jni/arm64-v8a/"*.so > "$build_dir/dependencies-sha256.txt"
mkdir -p "$build_dir/classes" "$build_dir/generated" "$build_dir/dex"
aapt package -f -M "$source_root/apps/android/AndroidManifest.xml" \
    -S "$source_root/apps/android/res" -A "$source_root/apps/android/assets" -I "$ANDROID_JAR" \
    -J "$build_dir/generated" -F "$build_dir/unsigned.apk"
find "$source_root/apps/android/src" "$build_dir/generated" -name '*.java' > "$build_dir/sources.txt"
javac --release 8 -classpath "$ANDROID_JAR:$dependency_classpath" \
    -d "$build_dir/classes" @"$build_dir/sources.txt"
jar cf "$build_dir/classes.jar" -C "$build_dir/classes" .
d8 --min-api 29 --lib "$ANDROID_JAR" --output "$build_dir/dex" "$build_dir/classes.jar" "${dependencies[@]}"
(cd "$build_dir/dex" && aapt add "$build_dir/unsigned.apk" classes.dex)
mkdir -p "$build_dir/native/lib/arm64-v8a"
cp "$DEPS_DIR/wake-1.13.8/jni/arm64-v8a/"*.so "$build_dir/native/lib/arm64-v8a/"
(cd "$build_dir/native" && aapt add "$build_dir/unsigned.apk" lib/arm64-v8a/*.so)
apksigner sign --ks "$SIGNING_DIR/developer.p12" --ks-key-alias developer \
    --ks-pass "file:$SIGNING_DIR/password" --out "$build_dir/sliver-wolf.apk" "$build_dir/unsigned.apk"
apksigner verify --verbose --print-certs "$build_dir/sliver-wolf.apk"
sha256sum "$build_dir/sliver-wolf.apk"
printf 'APK=%s\n' "$build_dir/sliver-wolf.apk"
