# M4 Native Habitat Implementation Evidence

Date: 2026-10-24. Status: first native UI slice implemented, Java compilation verified;
phone build/install and the scoped UI checks below are verified. Full M4 acceptance remains open.

## Accepted scope

User approved Attention Choreography Review 05 and authorized implementation.
Installed version: 0.4.0-m4-preview, versionCode 7, confirmed by Android package manager.
The APK continues to connect to the existing independent phone-local DSH deployment.

## Host compilation

Compiled all production Java sources using the existing Microsoft JDK 17.0.20.1,
`javac --release 8 -encoding UTF-8`, against the same pinned Android API 29 stubs,
Java-WebSocket 1.6.0 and SLF4J API 2.0.13 used by the phone build. Exit code 0.
The pre-existing TTS deprecation notice remains. The official platform archive,
android.jar and dependency hashes matched the build-tooling documentation.
Downloads and class outputs are ignored under `.local/compile-check/`.

This establishes Java/API compatibility only. It does not establish resource packaging,
DEX generation, APK signature, installation, layout, animation, or voice behavior.
The new vector resource is original project artwork adapted from the approved SVG;
no third-party character or voice assets were imported.

## Phone build and device checks

The remembered wireless endpoint timed out. The user connected USB, and the explicitly
selected YAL-L21 USB device returned ADB_USB_OK. The existing phone toolchain compiled
all sources, packaged resources, generated DEX and signed the final APK. No DSH restart,
replacement, configuration change or toolchain upgrade occurred.

- Final APK SHA-256: `67ff65b9c4b4bde05fc968476dd55e7d131f1a632c59d4790c9620bbdd462eaf`.
- Signing certificate SHA-256: `4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.
- `adb install -r` succeeded; the original controlled conversation recovered without
  creating a replacement session. Final cold start returned Status: ok.
- Both space and character compositions were inspected on the real screen; the chosen
  composition survived the subsequent APK upgrade.
- A precise text request sent through the native conversation page received the real
  DSH reply `HABITAT_READY`, displayed in the primary scene.
- Tapping the reply entered reading mode, retained the excerpt and exposed the explicit
  reading-exit control. This is excerpt retention, not a memory-provider integration.
- A real `ask_user_question` request produced the persistent pending-decision entry.
  It survived an in-place upgrade. Opening the entry, selecting Continue and submitting
  the original form yielded `HABITAT_DECISION_OK`; the pending entry disappeared.
  This verifies a question path; tool-approval variants were not rerun in this slice.
- Recognition entered actual Listening state; the captured screen shows its voice bar
  and the foreground reply withheld. With no spoken test phrase, Android returned
  no-match (code 7), displayed clearly. This is not an acoustic round-trip acceptance.
- The secondary page was inspected with the real Huawei keyboard open: input and Send
  remained visible. Long existing test history remains in a scrollable transcript.
- The temporary exact-input helper was uninstalled after checks; the assistant remains
  installed. No personal conversation or reference audio was used.

A larger Java file exposed the old transfer helper's Windows command-line length limit.
The helper now sends Base64 over standard input; the complete source transfer and
subsequent builds succeeded. Failed/intermediate build outputs were never represented
as the final release evidence.

## Demonstration

Local silent video: `artifacts/demos/20261024-223502-m4-habitat-native-67d97baf.mp4`.
Ten-second recording limit; decoded duration 9 seconds, 540x1168, 110604 bytes.
The inspected recording shows the controlled reply and composition transition; a
rendered video frame was reviewed. It is a UI demonstration, not audio evidence.
Screenshots and raw UI captures remain ignored under `artifacts/m4/`.

## Gates at 0.4.0 (historical)

Current gate status is in [M4 stage evidence](m4-stage-evidence.md).

- UI01: PARTIAL. The scoped real-device checks above passed. Remaining work includes
  imported animated characters, broader font/orientation layouts, all approval variants,
  complete attention/event scheduling and final user review of the installed UI.
- P01-P03: NOT RUN. Pinned pet importer, package validation and composed profiles remain
  unimplemented; the built-in vector is not evidence of Codex pet compatibility.
- V01-V04: prior M3 evidence and deferred cases are unchanged; rerun relevant foreground
  interaction checks after installation. No wake/background capability is added here.
- Pinned sprite import and normalized appearance/profile validation are the next M4
  implementation slice; this UI delivery does not close those gates.

Platform implementation reference: Android Developers,
[Custom drawing](https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing).
Canvas owns decorative rendering; native widgets own focus, controls and text surfaces.
