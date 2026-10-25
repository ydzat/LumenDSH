# Phone-Side Android Build Tooling

Status: M0 and M1 phone-native build/install verified on 2026-10-24. See
[M0 evidence](../../tests/m0-evidence.md) and [M1 evidence](../../tests/m1-evidence.md).

## Selected M0 route

Native Java Activity, Android API 29 stubs, Termux aapt, OpenJDK 21, D8 and apksigner.
No Gradle, PRoot, base APK, bundled DSH, or third-party app-private build environment.
The minimal UI and probe are original code; no character or voice assets imported.
This proves the small client pipeline, not future AndroidX/NDK dependency support.

Run inside Termux (the source tree is copied to `$BUILD_ROOT/source` over authorized ADB):

```sh
apt-get --no-install-recommends --no-upgrade -y install \
  aapt=16.0.0.4-4 d8=37.0.0 apksigner=37.0.0 openjdk-21=21.0.13
export BUILD_ROOT="$HOME/sliver-wolf-build"
bash "$BUILD_ROOT/source/tooling/android-build/prepare-platform.sh"
export ANDROID_JAR="$BUILD_ROOT/platform/android-10/android.jar"
export SIGNING_DIR="$HOME/.local/share/sliver-wolf/signing"
export DEPS_DIR="$BUILD_ROOT/deps"
bash "$BUILD_ROOT/source/tooling/android-build/build.sh"
```

The build script rejects mismatched package versions/platform hash, creates a fresh
output directory, records source hashes, and verifies the signed APK. It creates a
persistent developer key only if none exists; the random password remains in a private
file and is passed via file references. Do not remove or export this signing directory.
No DSH configuration or startup helper is required by these build scripts.

M1 adds pinned Java-WebSocket and SLF4J API jars, downloaded and checked by
`prepare-deps.sh` during the build. See [dependency provenance](../../apps/android/DEPENDENCIES.md).
Copy the Android assets directory as well as source/resources to the phone.
The separate [device checks](../../tests/android/README.md) are not packaged in the client.

Host helper `send-text.ps1` streams Base64 through standard input (avoiding Windows
command-line length limits) and transfers LF-normalized text files to the dedicated phone
build directory using `run-as com.termux`; specify `-Endpoint`, `-Source`, and
`-Destination` explicitly. This is an ADB development transport, not the future MCP
artifact handoff. Install the output with `adb -s <endpoint> install -r <apk>` and launch
`local.sliverwolf.assistant/.MainActivity`.

The platform is Google's [Android 29 revision 5 archive](https://dl.google.com/android/repository/platform-29_r05.zip),
identified in the official [repository metadata](https://dl.google.com/android/repository/repository2-1.xml).
Keep the SDK license shipped in that archive; it is not an application asset.

| Input | Version / SHA-256 |
| --- | --- |
| Platform ZIP | `951da8bf175254da74626824f919bd28def64f8828f29dd3b124a535cf4049d8` |
| `android.jar` | `d4ce0daae1695abc7497009c61237f774f52c6ffdc222784e03f60effb917532` |
| Termux aapt | `16.0.0.4-4`; binary `57f1f313a16ffa9c76de4d9eb6341dc485a53fcec629149cc87dcc44c57ab6e9` |
| Termux D8 | `37.0.0`; jar `a26ee56252adcff6752c379ae255a0ad538ebec60189e078ec6855487107637d` |
| Termux apksigner | `37.0.0`; jar `2defad215d7ff52968a409cde528cdaef7918b115e276b8e3378ca7a178e4180` |
| Termux OpenJDK | `21.0.13`; runtime/build `21.0.13` |

Termux recipe snapshot: [`21e4d7f9de7481c211caa0444a841c343dc391dc`](https://github.com/termux/termux-packages/tree/21e4d7f9de7481c211caa0444a841c343dc391dc/packages).
Its aapt recipe selects `termux/android-build-tools` tag `16.0.0.4`; D8/apksigner use
Android Build Tools 37.0.0 Java archives (Apache-2.0). OpenJDK recipe uses
`jdk-21.0.13-ga` (GPL-2.0 as declared by the recipe; retain upstream exceptions/notices).
Packages come from the authenticated Termux apt repository, with no local tool patches.
Recipe snapshot is an inspected source reference, not a claim that every repository
binary was built at that exact monorepo commit. Apt snapshots may expire; the build
fails on missing pinned versions rather than silently upgrading.

Platform references: [D8](https://developer.android.com/tools/d8),
[apksigner](https://developer.android.com/tools/apksigner),
[network security configuration](https://developer.android.com/privacy-and-security/security-config).
The APK allows cleartext only for `127.0.0.1`, declines redirects, validates RPC IDs,
and bounds response size. No access token is manufactured for a backend without auth.

The build environment is independent of the APK and of DSH's runtime. DSH invokes explicit build commands; the client receives artifacts through the device transfer/update contract.

## Upstream reference

[Phone self-build scripts](https://github.com/guzhou079-arch/deepseek-harness-android/tree/main/selfbuild) demonstrate Java -> D8 -> packaging -> signing under a phone-hosted PRoot/Alpine environment. They contain private-app paths, source-layout assumptions, and base-APK dependencies; they are not a verified drop-in SDK for this repository.

## M0 build record

Record pinned versions and sources for the JDK, Android API stubs, D8, resource compiler/packager, APK signer, and any runtime container. Record hashes for downloaded artifacts, exact entry commands, environment requirements, and produced APK hash.

A real build must generate valid resources and manifest, compile client code, package it, sign it, and launch on the phone. Placeholder R IDs or replacing DEX in an unexplained APK skeleton do not establish reproducibility.

Prefer a build from declared source/resources. If a skeleton is temporarily necessary, document its source/license/hash and how it will be reproduced before calling the release pipeline complete.

## Signing and storage

Keep a persistent developer key in a private, untracked location. APK updates must preserve the chosen identity. Product self-build signing is an explicit M6 gate, not solved by copying the developer's private key into the application.

Use ignored `.local/` for host configuration and ignored `artifacts/` for outputs. Phone build root belongs in Termux-managed internal storage with paths supplied by configuration; do not hard-code another app's private directory. Do not include the build toolchain or DSH payload in the resulting client APK.

Tests B01/B02 and U05 in [acceptance](../../tests/acceptance.md) are the delivery checks.
## Milestone demonstration recording

The project completion hook in `AGENTS.md` calls for a short real-device demo after
substantial completed stages. Inspect the screen first, use the controlled test
conversation, and keep all captures under ignored `artifacts/demos/`. Review the
resulting video before linking it. Recording never replaces acceptance tests.

```powershell
python tooling/android-build/record-demo.py --endpoint <PHONE_IP>:5555 --label m3-listen-stop --seconds 8 --scrcpy .local/tools/scrcpy-win64-v5.0.1/scrcpy.exe
```

Without `--scrcpy`, the script uses Android `screenrecord`. This Huawei firmware
lacks that executable, so the verified route uses portable scrcpy with `--no-audio`,
`--no-control`, `--no-playback`, and `--no-window`. The script selects the explicit
wireless device and the existing ADB executable. Recording is silent and does not
compete for microphone capture. Static display frames can make the encoded clip
shorter than its wall-clock capture limit; inspect duration and visible transitions.

Development-only tool provenance (not an APK dependency): official
[scrcpy v5.0.1](https://github.com/Genymobile/scrcpy/releases/tag/v5.0.1), unmodified
`scrcpy-win64-v5.0.1.zip`, SHA-256
`b12a2c4ee8be317422451fc7dcf8ee20a71b5ea7ef9ad73ddd825a25316227a5`, verified against
the release API asset digest before extraction. Scrcpy source is
[Apache-2.0](https://github.com/Genymobile/scrcpy/blob/v5.0.1/LICENSE); retain the
upstream bundle's dependency license files. The downloaded bundle stays in ignored
`.local/tools/`. See [upstream recording options](https://github.com/Genymobile/scrcpy/blob/v5.0.1/doc/recording.md).

## Wake dependency preparation

`build.sh` invokes `prepare-wake.sh` to verify pinned sherpa-onnx 1.13.8 Java/Android
artifacts and the separately licensed WenetSpeech Chinese KWS model. It extracts only
arm64-v8a JNI and ONNX Runtime libraries for the current phone. Download caches stay
in `DEPS_DIR/wake-1.13.8`; generated model assets stay in ignored `assets/wake`.
A fresh independent Termux build requires network access to the pinned public release
URLs; subsequent builds reverify cached bytes. The APK does not contain this tooling.
The Android 29 manifest intentionally does not use the microphone foreground-service
type introduced in newer Android APIs. This preview targets the validated Android 10
phone; newer Android foreground restrictions require a separate target-SDK update.

Owner verification reuses that JNI/runtime and additionally verifies the CAM++ and
Silero v5.1 model hashes. For isolated real-native/Keystore fixture checks, set
`OWNER_FIXTURE_DIR` to an ignored dedicated directory, run `prepare-owner-fixtures.sh`,
then `build-device-checks.sh` and the `OwnerChecks` instrumentation. Public fixture
WAVs enter only the separate test APK, never the production APK. The test never
registers a fixture speaker as the actual device owner.
