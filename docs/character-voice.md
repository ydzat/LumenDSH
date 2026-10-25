# Local character speech

Android `0.7.48-character-voice` integrates the user-approved Silver Wolf voice.
The character model runs independently in Termux. The APK keeps the existing
microphone, audio-focus, operation-journal and reply-correlation ownership.

## Runtime and pairing

The prepared native executable, ONNX Runtime, model files, and voice conditioning
are installed under `~/lumendsh-voice/`. The model directory is `models/`; the
runtime files are `gpt_sovits_demo` and `libonnxruntime.so`. Preparation, pinned
sources, patches, licenses and the accepted reference are documented in
[the voice integration](../integrations/voice/README.md).

Copy `integrations/voice/serve.py` and `start.sh` into that private runtime directory.
Start it with:

```sh
bash "$HOME/lumendsh-voice/start.sh"
```

The separate `lumendsh-voice` tmux session serves only `127.0.0.1:3086`.
This command does not restart DSH. After a phone/Termux restart, start this runtime
alongside DSH; the APK does not silently launch Termux commands or install models.
The runtime generates a private independent pairing token. With the existing
device-tools pairing active, run `integrations/voice/pair.mjs` in Termux with
`DSH_ROOT` and `SLIVER_WOLF_TOKEN_FILE` set to their existing private paths.
The credential crosses the authenticated device bridge, not a chat message.

Select **Silver Wolf · local synthesis** in the composition profile's voice engine
selector (Chinese UI: **银狼 · 本地合成**). System speech remains another explicit
selection. Language, speed and pitch remain separate from the appearance;
speed/pitch are applied during Android playback. Changing skins does not change
the Agent identity or its memory. The deployed phone profile keeps its existing
appearance and selects the local voice at speed/pitch 1.

## Request lifecycle

1. `DeviceSpeech` reserves the existing correlated operation and owns audio focus.
2. `CharacterSpeech` submits the text and operation ID over authenticated loopback
   HTTP. It never automatically retries a submission.
3. The Termux worker runs text models in one short-lived process, releases them,
   then runs synthesis models in a second process. Character voice conditioning
   was already prepared on the computer.
4. The service converts the real float waveform to PCM16. Android plays it through
   `MediaPlayer`; completion, failure and interruption update the original operation.
5. Stopping speech immediately stops local playback and invalidates any pending
   callback, then cancels the corresponding native synthesis request. Cancellation
   arriving before submission is also retained, preventing a late start.

The request limit remains 1,000 characters. Each native phase has a 300-second
deadline and an address-space cap of at most 2 GiB, while reserving 1 GiB of
currently available system memory. Insufficient memory fails explicitly. Jobs are
serialized, and repeat operation IDs are rejected by the service as well as being
deduplicated by the client journal. Temporary text features, logs and WAVs are
removed after a request. App playback caches are removed on completion/cancellation.

The UI reports local synthesis while waiting. Service errors are visible; there
is no automatic substitution of another voice. Foreground replies, profile preview,
device speech tools, and the background wake reply all reuse `DeviceSpeech`.
The wake loop keeps listening paused through synthesis and playback, then follows
its existing return-to-standby path.

## Verification

Real phone checks, Android 10 / nova 5T:

- Existing-signature APK upgrade installed successfully, preserving profile/session.
- Independent authenticated service startup and app pairing passed.
- Existing profile release/activation selected the local engine without changing
  the appearance.
- A real application speech request completed Android playback. Repeating the
  operation key retained the same operation ID and did not enqueue another utterance.
- Stop during synthesis and stop after the playback-start state both produced
  `cancelled`, with no continued client playback.
- Stopping only the new voice service caused explicit speech failure; restarting
  it restored availability.
- Real HTTP checks rejected invalid credentials, browser Origin requests, empty
  text, and a submission whose cancellation had already arrived.
- Private request directories were empty after these checks; the existing embedding
  health endpoint remained healthy.

Reproduction helpers: `integrations/voice/check-live.mjs` (`activate`, `play`,
`stop-synthesis`, `stop-playback`, `unavailable`) and `check-http.py`.
These extend V04/P03 speech ownership and profile integration evidence. They do
not assert a new owner-wake acoustic test or incoming-call test. Earlier acoustic
test waivers remain separate from this engine integration.

Two native cold runs took approximately 15–16 seconds for short requests, with
sampled peak RSS below 936 MiB per phase. This integration is usable with that
initial wait; it does not claim real-time streaming synthesis or establish the
latency of 1,000-character replies. Voice data and generated media remain ignored
and are not included in the APK or public repository.

Final installed APK SHA-256:
`e59b4f0e0ad632bf5f003c54064f2014d1d3e1822dec275900b3b7cfc39d2b5c`.
The final build passed real playback after upgrade/profile restoration.
An eight-second inspected demonstration is saved locally at
`artifacts/demos/20261025-192924-character-voice-masked-59e7efb9.mp4`.
It records the real phone during playback, without audio; the existing message
history region is masked. It is not an acoustic recording or a wake-word test.
