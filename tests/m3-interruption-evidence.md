# M3 Foreground Interruption Evidence

Date: 2026-10-24. Source: working tree following 4ecd79e. Target: Huawei YAL-L21,
Android 10; unchanged DSH 0.1.0-rc.7-termux.1. No server restart or profile change.

## Artifact

Phone-built and installed APK 0.3.1-m3-preview, versionCode 6:
`e512be86d05d062a524cb18a9ead7e9a55448aa5eab7ea8d1ff4b0899179690d` (SHA-256).
Existing certificate preserved; v3 signature verified. In-place install succeeded;
final cold launch completed in 400 ms. No additional APK dependency or permission.

## Executed device checks

- V02, local stop: the separate test APK clicked Speak, observed the real recognizer's
  Listening state, clicked Stop voice, and observed Voice stopped persisting after
  1.5 seconds. This ran on the intermediate build listed below; recognition/stop code
  is identical in the final build.
- V02, tap interruption: real Chinese TTS reached running. Tapping Speak cancelled
  playback with the recorded local-control reason and activated recognition. Tapping
  Stop voice released recognition. Final bridge status reported no active recognizer
  or playback. No acoustic, hands-free interruption is claimed.
- During that active recognition, a retry of the interrupted speech returned the
  same cancelled operation; a different speech key was refused as microphone busy.
- V04, audio-focus subset: a separate installed test process requested actual Android
  audio focus while TTS was running. Android granted it. The speech journal recorded
  cancelled / Audio focus lost. Playback remained stopped after the competing lease
  was released. Both this check and tap interruption passed on the final APK using
  `check-audio-focus.mjs` plus real Android input/focus operations.
- No synthetic recognition results or fake DSH responses were used.

## Source discovery and limits

The phone's audio-service log showed GoogleRecognitionService requesting its own
exclusive transient focus. Recognition is delegated to that service; client-side
competing focus is not used. The new non-normal audio-mode startup refusal and
500 ms recognition mode monitor are implemented but not verified with a real call.
V03 remains NOT RUN. V04 overall remains PARTIAL, with incoming calls, competing
microphone capture, and backend-loss voice recovery unverified. V01's human acoustic
confirmation belongs to 0.3.0; no new acoustic recognition-quality claim is made here.

## Local demonstration

The inspected silent MP4 is in ignored local storage:
`artifacts/demos/20261024-212646-m3-start-stop-verified-0c5399b7.mp4`.
Windows MediaPlayer decoded it as 540x1168, approximately five seconds. Reviewed frames
show Hearing speech followed by Voice stopped, with only the controlled test conversation
visible. Capture used the intermediate 0.3.1 build with SHA-256
`3c9733356d22a6e00abd536e9085a3682cb6e80d7a650b9b482d94c7cd49f74b`;
the final build additionally preserves speech retry semantics while listening.
This clip demonstrates UI state transitions, not audible output or full M3 completion.

The phone lacks `/system/bin/screenrecord`. Portable official scrcpy 5.0.1 captured
video with audio and control disabled; no recording APK was installed and no microphone
was captured by the recorder. The failed initial/static capture is not a feature demo.
