# M8: Local and remote voice providers

Status: DELIVERED in Android 0.8.0. Authorized 2026-10-25. CPUFast is the selected remote
inference implementation; the user accepted its voice and requested no further
inference-project comparison once usable.

## Scope and ownership

Keep Android system speech, phone-local hoyoTTS and phone-local GPT-SoVITS
available as explicit choices. Add configurable HTTPS speech providers with
separate provider, model and voice selection. DSH remains phone-local and
unchanged. Only submitted speech text goes to the selected speech provider.
Do not silently change voice, replay uncertain requests, or fall back to a
different service. Reuse DeviceSpeech's microphone/audio-focus/operation owner.

Reuse CPUFast revision `03706cce25085a3f2522679193dd437896e0be21` (MIT) for
server inference. Add only missing provider protocol/catalog/authentication
integration. Preserve existing phone-native engines and pairing. Expose actual
installed models and configured voices, never placeholder catalog entries.

## Deliverables

1. Standard `GET /v1/models` and `POST /v1/audio/speech`; named voice discovery
   via `GET /v1/audio/voices?model=...` (compatible extension, not an OpenAI
   universal standard). Support explicit model/voice entry when third-party
   servers do not implement discovery.
2. Local catalog and per-request engine selection, retaining bounded model
   residency and cancel behavior. GPT-SoVITS availability does not override its
   phone memory requirements.
3. Persistent provider configuration, protected credentials, model/voice
   discovery and selection, preview and removal in the existing settings drawer.
   Profiles reference provider/model/voice choices without exporting credentials.
4. HTTPS remote CPUFast deployment, named voice configuration, authentication,
   resource limits, cancellation and reproducible setup; do not expose upstream
   administrative endpoints. No incidental change to existing applications.
5. Shared foreground/background/tool playback path, bounded audio storage,
   early playback of sentence segments with next-segment preparation, reliable
   cancellation and visible provider errors. No claim of faster-than-realtime
   synthesis: measured CPU throughput remains a limitation.

## Consolidated acceptance

| ID | Required evidence |
| --- | --- |
| S01 | Real local model/voice discovery and both existing engine selections; preserve pairing |
| S02 | Remote HTTPS authentication, actual model/voice catalog, correct WAV output; reject unknown selection |
| S03 | Saved provider and voice survive app restart; credentials absent from exported profiles |
| S04 | Real Android remote preview and complete multi-segment reply, one correlated operation |
| S05 | Stop during synthesis/playback, no later playback or microphone overlap |
| S06 | Explicit network/auth/model error with no automatic replay or voice substitution |
| S07 | Foreground and background reply ownership, system/local selection regression |
| S08 | Phone build/install, controlled real-device demonstration and user documentation |

Completion requires evidence for each row. Prior acoustic wake waivers are not
converted into new passes. Server-only synthesis is not Android playback proof.

## Closure

See [S01-S08 executed evidence](../tests/m8-evidence.md) and
[provider setup](../integrations/voice/providers.md). Local GPT-SoVITS selection
and resource rejection are verified; playback did not pass at the available RAM.
Remote CPU throughput may leave inter-sentence gaps. No additional inference
project was installed, and prior acoustic waivers remain unchanged.
