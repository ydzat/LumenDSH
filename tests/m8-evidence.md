# M8 speech provider evidence

Date: 2026-10-25. Device: Huawei nova 5T, Android 10, USB ADB.
Client: 0.8.0 (versionCode 52). DSH remains 0.2.1-rc.3.
Server: Debian 12, eight EPYC vCPUs, 16 GiB RAM, CPUFast pinned in
[deployment instructions](../integrations/voice/providers.md). The voice unit
uses four CPUs and a 6 GiB memory cap. Existing server applications remain running.

## Executed checks

| Case | Observed result |
| --- | --- |
| S01 | Authenticated phone catalog returned both installed engines. Switching to local hoyoTTS completed actual Android playback. GPT-SoVITS selection activated, but synthesis returned 503 at 2,371,504 KiB available RAM: `Insufficient available memory for character voice`. This is verified resource rejection, **not a GPT-SoVITS playback pass**. Existing local pairing remained valid. |
| S02 | Real HTTPS requests: invalid credentials 401; unknown model/voice 404; one model `gpt-sovits-v2pro`, three voices; all three produced WAV. MP3 and 24 kHz PCM16 responses were also decoded/checked. Duplicate and pre-cancelled IDs returned 409. |
| S03 | `VoiceProviderChecks` passed real local/HTTPS catalogs, encrypted credential envelope and restored selection. App restarts and APK update retained provider configuration; subsequent authenticated playback succeeded. Profile payloads contain only immutable selection references, by source inspection. |
| S04 | Remote multi-sentence operation reached actual MediaPlayer playback and completed once. Repeating the MCP operation key returned the same operation. Android settings show the selected provider, model and voice. |
| S05 | Stop during remote synthesis and after playback started both completed as cancelled; no late playback during the observation window. Server active-job cancellation returned 409 after 2.67 seconds and the next synthesis succeeded. |
| S06 | During voice-service restart, Android received an explicit HTTP 502 failure, without fallback/replay. Server authorization/model checks above and local memory rejection remained explicit. |
| S07 | Foreground `VoiceReplyChecks` passed real DSH reply → remote speech → standby. `BackgroundVoiceChecks` passed the same path in the real background service. Android system TTS and local hoyoTTS both played successfully. |
| S08 | Phone compilation, signature verification and in-place installation passed. Provider UI inspected on device; final demo and artifact receipt are recorded below. Python syntax checks and `git diff --check` passed. |

`VoiceReplyChecks` and `BackgroundVoiceChecks` enter at the final recognized-text
callback. They use a controlled actual DSH conversation and restore the original
session/preferences. They verify reply/lifecycle ownership, not acoustic recognition
or a new human wake-word trial. Earlier acoustic waivers remain unchanged.

Server live test: `tests/remote-voice-live.py`; Android tests under `tests/android/`;
real MCP operations: `integrations/voice/check-providers.mjs`. Private receipts,
audio, logs and keys stay outside tracked files. Systemd and nginx were both active
at the final service check. No source commit or push is part of this milestone.

## Performance and completion boundary

The user accepted CPUFast's generated voice quality before M8. Server samples for
Silver Wolf, Firefly and Robin took approximately 8.88, 5.63 and 6.96 seconds to
generate 2.70, 2.54 and 3.38 seconds of audio respectively in the recorded run.
These are observed samples, not a latency guarantee or a claim of real-time output.
Preparing the next sentence while playing the current sentence can still leave gaps.

All provider implementation work is delivered. Local GPT-SoVITS retains its
pre-existing memory limit and has no new successful playback result in this gate.
The service does not weaken memory limits or terminate other phone processes to
make the test pass. The installed default is remote Silver Wolf; local hoyoTTS and
system speech are tested alternatives. Additional remote model checkpoints can be
configured but were not installed or claimed as verified.

## Final artifacts

Final APK SHA-256: `cbb3e72badf468cb809f5770364fe501a54ad6e2029e6d0d10ac004728ae1a41`.
The existing signing certificate was retained and Android installation succeeded.
The final UI correction removes the loading label after catalog arrival.

Silent 10-second device demonstration:
`artifacts/demos/20261025-220123-m8-voice-providers-1af4287d.mp4`.
It shows provider → actual model → named voices. The inspected output crops
status-bar and habitat-edge content; it records no audio and is Git-ignored.

After the final UI build/install, the selected remote voice again reached actual
Android playback and completed successfully with duplicate suppression. Package
inspection confirmed versionCode 52 and versionName 0.8.0.
