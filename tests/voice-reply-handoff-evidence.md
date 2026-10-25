# Foreground automatic speech handoff

Date: 2026-10-25. Huawei YAL-L21 / Android 10; installed fix:
`0.7.50-voice-handoff` (versionCode 50), built and signed in Termux.

## Reproduced failure

The original device preferences were `homeReply=voice` and `homeWake=true`.
On 0.7.49, `VoiceReplyChecks` started the real owner-gated keyword microphone,
submitted controlled recognized text to a real DSH session, then observed:

`Automatic speech operation missing: 播放失败：busy: microphone recognition active`

The foreground activity left keyword capture eligible while waiting for a reply.
When a reply arrived, it removed the pending speech entry before attempting speech;
DeviceSpeech correctly rejected that attempt because the microphone was occupied.
Earlier direct playback checks used an open drawer, which suspends keyword capture
and therefore did not exercise this interaction.

## Fix

- Suspend foreground keyword eligibility while a voice request, queued spoken
  reply, synthesis or playback is active.
- Retain a reply while waiting for microphone release; remove it after speech
  acceptance or a terminal error.
- Use the existing 250 ms UI tick to complete asynchronous microphone handoff and
  resume keyword eligibility after speech ends. No new listener or retry service.

## Verification

The same installed-device instrumentation passed on 0.7.50:

`PASS: keyword active -> real DSH response -> automatic character playback -> standby`

It used actual keyword microphone ownership, actual DSH generation and the
installed hoyoTTS/MediaPlayer path. It enters at the recognized-text callback, so
it is not proof of acoustic recognition, wake-word accuracy or owner matching.
The original conversation selection and interaction preferences were restored.

Script: `tests/android/VoiceReplyChecks.java`. Acceptance mapping: V01's correlated
reply-to-speech routing and V02's microphone ownership, within the scope above.
The existing acoustic/background acceptance limits remain unchanged.

The phone build and in-place installation succeeded. APK SHA-256:
`955c8d3097d710d22ccce7effe0cb78a742206b1e8df54b218bf29538bbf0ff5`.
No DSH restart, voice-model change or credential change was performed.
