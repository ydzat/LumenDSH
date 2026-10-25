#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
: "${BUILD_ROOT:?Set BUILD_ROOT to a dedicated Termux build directory}"
mkdir -p "$BUILD_ROOT/platform"
archive="$BUILD_ROOT/platform-29_r05.zip"
if [ ! -f "$archive" ]; then
    curl -fL --max-time 180 -o "$archive" https://dl.google.com/android/repository/platform-29_r05.zip
fi
# Google repository2-1.xml, platforms;android-29 revision 5, inspected 2026-10-24.
printf '%s  %s\n' 9d8a7e0ffa5168dbca6c60355b9129c6c7572aff "$archive" | sha1sum -c -
printf '%s  %s\n' 951da8bf175254da74626824f919bd28def64f8828f29dd3b124a535cf4049d8 "$archive" | sha256sum -c -
(cd "$BUILD_ROOT/platform" && jar xf "$archive")
sha256sum "$archive" "$BUILD_ROOT/platform/android-10/android.jar"
