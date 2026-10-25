# M4.5 Background Wake Evidence

## Installed diagnostic build

0.4.14 (code 21), APK SHA-256:
`7da160263105858fca8d79353218724be7b845b35243c41e9f6b6f3eed40c642`.
The installed APK passed native/ICU checks again. The controlled TTS fixture also
passed through the same `decodeReady` function now used for live microphone blocks.
The actual service reported advancing sample counts and nonzero RMS on standby;
the second real-user wake attempt also failed (no cue, hit count zero).

## Scope

The user approved sherpa-onnx and the configurable greeting `嗨，<Agent name>`.
Current target: Huawei nova 5T / Android 10, name 银狼. V03 remains OPEN.

## Source and implementation

Pinned engine 1.13.8 and Chinese WenetSpeech 3.3M model are recorded in
`apps/android/DEPENDENCIES.md`. Native JNI and ONNX Runtime libraries plus selected
model files are packaged independently of Termux/DSH. Model files are rechecked before
private activation. Android ICU derives model tokens from the presentation name.
Unsupported names fail explicitly. Native instances are rebuilt after name changes:
upstream per-stream keywords are additive and would otherwise retain the previous name.

The explicitly enabled foreground service owns background AudioRecord KWS, one-shot
system recognition and same-session submission. It releases KWS before the recognition
cue. Foreground and service code share the MCP/TTS bridge and correlated reply selector.
No DSH restart or deployment change was made. PCM is not retained. Numeric diagnostics
expose sample count, peak RMS, detections, recognized requests and readout starts only.

## Executed checks

- Host Java compile and independent phone Java/DEX/APK/signature/install succeeded.
- Instrumentation against the installed APK initialized the actual native model.
- Android ICU produced `h āi y ín l áng` for the complete default greeting.
- A different Chinese name produced different tokens and a replacement native engine.
- Unsupported Latin name rejected; one second of zero PCM did not detect a keyword.
- A local Google TTS fixture saying `嗨，银狼` was synthesized to a temporary private
  WAV (not played), decoded through the model and detected. The fixture was deleted.
  This is generated test audio, NOT a successful real-user wake test.
- Existing conversation (10), habitat projection, notification archive and schedule
  registry (12) checks passed. Five synthetic completed-turn/RPC correlation checks
  passed, including explicit-speech suppression and unrelated-turn exclusion.
- The actual background service reached RECORDSTATE_RECORDING and displayed standby.
  AudioFlinger showed an active 16 kHz built-in microphone track, not system-silenced.
  Media stream was unmuted at volume 10/15.

## Failed real-user test

The user said the requested wake phrase and reported **no wake cue**. This is a failed
V03 attempt. Standby UI, native initialization and generated-audio detection do not
override that result. Background recognition/submission/reply/resume have therefore
not passed end-to-end user acceptance. Investigation separates live audio availability
from keyword detection using numeric diagnostics without saving microphone audio.
The decoder loop was aligned with the official Android example to inspect every
individual decode result, rather than only inspecting after draining ready chunks.
This correction is not yet established as the cause of the reported miss.

## Demo and remaining limits

`artifacts/demos/20261025-005344-m45-keyword-standby-3b80216d.mp4` is a 9-second actual
screen recording of the button-free character and standby status; no audio captured.
It demonstrates presentation only, not a successful wake cycle.

False-trigger rate, battery, long standby, calls, lock screen, real spoken rename
behavior and background provider operation remain unverified. M4.5 is not complete.

## Discriminating acoustic diagnostic

The second spoken attempt produced nonzero microphone samples and a peak RMS of about
0.465, but no keyword detection. This establishes received sound energy, not what words
were actually present. A separate test APK then played only the fixed greeting through
the phone speaker. The client microphone KWS detected it (`hits=1`) and entered system
recognition, then returned to standby without submitting a request when no command was
recognized. This is a controlled generated-speech acoustic loopback, not user acceptance.
It narrows the remaining miss to acoustic matching under the user's conditions rather
than a universally broken microphone-to-KWS route. The exact cause is not yet proven.

The next candidate aligns the keyword threshold with upstream Android's 0.25 default
and adds greeting-tone tolerance while requiring the complete configured name. Earlier
human failures remain recorded; this tuning is not a passed V03 result.
