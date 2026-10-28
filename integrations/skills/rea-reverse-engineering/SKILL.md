---
name: rea-reverse-engineering
description: Inspect apps, APKs, native binaries, JavaScript/Electron bundles and Android devices with REA (Reverse Engineer Anything). Use for "how does this app work", APK signature/manifest checks, decompilation, or device/package inspection.
whenToUse: The user asks to reverse engineer, decompile, inspect an APK/manifest/signature, understand a third-party app's behavior, analyze a native binary or a JS bundle, or inspect an Android package over adb.
---

# REA on this setup

REA is installed twice:

- **Server (full version, preferred)**: `ssh server rea <command>`
  Debian 12 x86_64, REA 6.8.0, JADX headless 0.7.1 (`REA_JADX_MCP_JAR`), Ghidra
  12.1.4 (`GHIDRA_INSTALL_DIR`), Temurin JDK 21 (`JAVA_HOME`). APK decoding and
  native decompilation work here.
- **Phone (limited)**: `~/agent-workspace/rea.sh <command>` — only static APK
  signature reading and adb inspection work on android-arm64; JADX and native
  engines are unsupported there.

## Moving files

- APK on the phone: `adb -s 127.0.0.1:5555 pull /data/app/.../base.apk ~/tmp/app.apk`
  (or use an APK already under `/storage/emulated/0/Download/`).
- To the server: `scp -q ~/tmp/app.apk server:/tmp/app.apk`.
- Native binary or JS bundle: same pattern (`adb pull`, `scp`).

## Common commands (run on the server unless noted)

- Signature: `rea inspect-apk-signature /tmp/app.apk`
- Declarations + manifest: `rea inspect-android-package /tmp/app.apk`
- One class: `rea inspect-android-class /tmp/app.apk --class com.example.Foo`
- One method: `rea inspect-android-method /tmp/app.apk --class com.example.Foo --method bar`
- App overview: `rea analyze /tmp/app.apk`
- Native binary: `rea analyze /tmp/binary` then `rea decompile /tmp/binary --procedure main`
- JS/Electron: `rea analyze-javascript-application /path/to/app`
- Web bundle: `rea analyze-web-bundle <url-or-path>`
- Device/package (phone-local is fine): `~/agent-workspace/rea.sh inspect-adb-package 127.0.0.1:5555 <package>`; `inspect-adb-device 127.0.0.1:5555`; `inspect-adb-window 127.0.0.1:5555`.

Use `--format md` for readable output and `--filter-output` to keep it short.
Every result carries an `evidence_id` and artifact sha256; cite them.

## Rules

- Only analyze software you own or are authorized to analyze. No cracked
  commercial tools, shared licenses, or bypassing tool licensing.
- JADX/Ghidra runs can take minutes: use a generous timeout and avoid re-running
  the same analysis for a trivial formatting change.
- Do not upload analyzed artifacts to third parties; REA runs locally.
- If the server is unreachable, fall back to `rea.sh` and state the reduced scope.
