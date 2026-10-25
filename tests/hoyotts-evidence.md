# hoyoTTS phone integration evidence

Date: 2026-10-25. Device: Huawei YAL-L21, Android 10, USB ADB verified. Existing
client: 0.7.49-settings. Existing ONNX Runtime: 1.27.0, two inference threads.
The user accepted desktop sample B before deployment and explicitly approved the
isolated upstream Chinese frontend dependencies. No local fine-tuning was used.

## Model checks

- Pinned pretrained Silver Wolf speaker 221 loaded with strict tensor matching.
- Exported ONNX synthesis graph: 169,802,803 bytes.
- Three real, different-length Mandarin inputs passed deterministic PyTorch/ONNX
  waveform checks with stochastic scales set to zero. Largest absolute difference:
  0.000029862. Normal inference keeps the accepted stochastic settings.
- Full-token quantized BERT interior outputs matched the existing cropped graph
  exactly in the checked input. Weights were not changed.
- Frontend checks covered numeric text, bounded long-text segmentation and an
  explicit error for unsupported English instead of silent text deletion.

## Complete phone pipeline

The candidate used an isolated authenticated service on 127.0.0.1:3089. All text
normalization, phoneme conversion, BERT features and waveform synthesis ran on
the phone. Controlled test text contained no user conversations.

| Case | Observed result |
| --- | --- |
| Short speech | HTTP 200; 1.435 s audio; 15.744 s complete request |
| Hundred-character reply | HTTP 200; 18.663 s audio; 44.606 s complete request |
| Duplicate request ID | HTTP 409; no repeated synthesis |
| Unauthenticated request | HTTP 401 |
| Concurrent request | HTTP 409 while the first request was active |
| Cancel during synthesis | HTTP cancellation acknowledged; synthesis stopped in 0.071 s |
| Speech after cancellation | HTTP 200; 2.503 s audio; 14.103 s complete request |
| Private temporary data | Request directory empty after all cases |

The dedicated resource probe monitored each actual child PID rather than scanning
unrelated processes. Sequential peak RSS: frontend 185,958,400 bytes, BERT stage
502,820,864 bytes, waveform stage 263,704,576 bytes. The largest child peak was
about 480 MiB; these figures are not total device/application memory. The probe's
complete request took 17.434 s. A separate waveform-only test measured about
277 MiB, which must not be presented as the full pipeline peak.

Scripts: `hoyotts-phone.py`, `hoyotts-resources.py`; private receipts:
`~/lumendsh-hoyo-candidate/integration-receipt.json` and `resource-receipt.json`.

## Installed application

After candidate checks, only the separate voice service was switched to hoyoTTS.
The existing token, Android profile, conversation and DSH service were preserved.
The candidate service was stopped. Startup authenticated health reported the
correct backend. Original voice assets and startup scripts remain for rollback.

`integrations/voice/check-live.mjs` passed `play`, `stop-synthesis` and
`stop-playback` against the installed client: actual playback completed, duplicate
operations were suppressed, and both interruption paths ended as cancelled.
The settings drawer temporarily suspended foreground wake microphone ownership
during controlled playback checks; user settings were not changed.

Acceptance mapping: this provides updated character-playback evidence for the
playback/explicit-stop portion of V02. It does not rerun V01's two voice-input
turns, V03's long background/lockscreen trials, or V04's incoming-call conflicts.
The user accepted desktop timbre; no new human acoustic quality rating is claimed.

Stage demo: `artifacts/demos/20261025-210253-hoyotts-voice-masked.mp4`, approximately
eight seconds of real installed-app playback, without audio. The message-history
region is masked because the previously hidden history appeared during playback.
The delivered masked frame was inspected. It is not an acoustic wake test.

Remaining limits: the character frontend is Chinese-only, the service returns a
completed WAV rather than streaming audio, and longer replies still incur their
full synthesis latency. System speech remains a separate selectable option.

The direct playback checks above did not cover automatic replies with foreground
keyword capture active. That interaction subsequently failed and was fixed in
0.7.50; see [reproduction and regression evidence](voice-reply-handoff-evidence.md).
