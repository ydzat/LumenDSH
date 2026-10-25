# Local and remote speech providers

Android 0.8.0 separates the speech provider, synthesis model and named voice.
Open **Menu → Interaction and voice → Reading voice → Speech provider**. Choose
Android system speech, Phone local, or add an HTTPS provider. Select its model
and voice, then return to Reading voice to preview. Home and background replies
use this same selection and the existing input/reply preferences.

## Client contract

- Base URL ends in `/v1`; the app appends it when omitted. API keys are stored
  using Android Keystore AES-GCM, independently of exported character profiles.
- `GET /models` returns OpenAI-style `data` entries with model `id` values.
- `POST /audio/speech` receives `model`, `voice`, `input`,
  `response_format: "wav"`, and `speed: 1`. The player applies profile rate/pitch.
- Optional `GET /audio/voices?model=...` returns a `voices` array of objects
  with `id`, `name` and `language`. This is an extension, not a universal
  OpenAI endpoint. Manual model/voice entry supports services without discovery.
- Our catalogs advertise `cancellation: true`. The client then sends
  `DELETE /audio/speech/{request-id}` on stop, using the prior `X-Request-ID`.
  Other providers still stop local playback and disconnect the request.

Remote requests require HTTPS. Only the text being read is submitted, not the
conversation history, microphone audio or owner voiceprint. Profiles contain a
device-local selection identifier; another device must configure its provider
again. Removing the active provider requires selecting another voice first.

The app prepares sentence segments ahead of playback with at most two audio
files in flight. Stop invalidates the operation, disconnects synthesis and
discards queued audio. Failures are visible and do not switch services or replay
requests automatically. Generic providers must return complete WAV audio;
this implementation does not consume SSE or an arbitrary streaming protocol.

## Phone-local engines

The existing paired service on `127.0.0.1:3086` now exposes `/v1/models`,
`/v1/audio/voices`, `/v1/audio/speech` and cancellation alongside its legacy API.
The catalog includes engines whose local assets are installed. Requests select
`hoyotts` or `gpt-sovits` without changing the service's legacy default.

Use [hoyoTTS setup](hoyotts.md) or [prepared GPT-SoVITS setup](README.md).
Installed models do not imply sufficient currently available RAM: GPT-SoVITS
retains its 2.5 GiB available-memory preflight; hoyoTTS is the lighter local option.
No models, reference recordings or build tools are bundled in the APK.

## Remote CPUFast deployment

Reuse [GPT-SoVITS-CPUFast](https://github.com/baicai-1145/GPT-SoVITS-CPUFast)
at `03706cce25085a3f2522679193dd437896e0be21` (MIT). Inference is unchanged.
`remote-provider.py` supplies catalog, authentication, format conversion and
cancellation around its `TTS` implementation. FastAPI and Uvicorn already belong
to the upstream locked environment; no Android HTTP or audio dependency was added.

The tested layout uses Debian 12, Python 3.11.5, CPU PyTorch, ffmpeg and uv:

```sh
mkdir -p /opt/lumendsh-tts-test
git clone https://github.com/baicai-1145/GPT-SoVITS-CPUFast /opt/lumendsh-tts-test/cpufast
cd /opt/lumendsh-tts-test/cpufast
git checkout 03706cce25085a3f2522679193dd437896e0be21
uv sync --locked
python3 /path/to/LumenDSH/integrations/voice/download-cpufast.py
```

The downloader pins model repositories and verifies available LFS SHA-256 values,
writing receipts locally. It supplies two required G2PW dictionaries omitted by
the pinned upstream installer. Model revisions:

| Repository | Revision |
| --- | --- |
| XXXXRT/GPT-SoVITS-Pretrained | `0c47645e02a7bc3688d7b263b0042c81e3cd82cd` |
| baicai1145/g2pw | `57112a7c10b49c26a171800cf4af04d32a41d5de` |
| GitYCC/g2pW dictionaries | `b9328ce8129c4717a70f16a2688542a0197541dd` |

Create `/opt/lumendsh-tts-test/tts.yaml` with this JSON-compatible YAML:

```json
{"custom":{"device":"cpu","is_half":false,"version":"v2Pro","t2s_weights_path":"GPT_SoVITS/pretrained_models/s1v3.ckpt","vits_weights_path":"GPT_SoVITS/pretrained_models/v2Pro/s2Gv2Pro.pth","bert_base_path":"GPT_SoVITS/pretrained_models/chinese-roberta-wwm-ext-large","cnhuhbert_base_path":"GPT_SoVITS/pretrained_models/chinese-hubert-base"}}
```

For the evaluated character set, supply your private `silver-wolf-reference.wav`
and `reference-text.txt` in that directory, then run `prepare-remote.py`.
It creates a private provider configuration and random token, and downloads
the two additional reference samples from WLWolf56/gpt-sovits-model revision
`8ca20dd1f84feda57396531ef5413130a5d9cc60`, recording their provenance/hashes.
Reference audio and model licenses are separate from the MIT integration code.

Alternatively create `/opt/lumendsh-voice/provider.json` yourself with
`token_file`, `tts_config` and `models`. Each model maps to `gpt_weights`,
`sovits_weights` and a `voices` object; each voice needs `name`, `language`,
`reference_audio` and `reference_text`. These are server-local paths and text.
More compatible checkpoint pairs can be registered as separate models; the
verified deployment has **one model, three voices**, not three different models.

Copy `remote-provider.py` to `/opt/lumendsh-voice/remote_provider.py`, install
`lumendsh-voice.service`, and enable/start that unit. Include
`nginx-location.conf` inside your existing TLS server, run `nginx -t`, then
reload nginx. The public base URL is `https://YOUR_HOST/lumendsh-voice/v1`.
Keep the token/config private; enter the token into the app's API key field.

The service binds loopback only, serializes inference, limits memory to 6 GiB
and CPU to four cores, and omits submitted text and credentials from logs.
All routes require Bearer authentication. WAV, MP3 (default), and 24 kHz PCM16
responses are supported; ffmpeg converts the latter two formats. Busy and
duplicate requests return 409. A cancelled active job releases the inference
slot; the same request ID is not rerun.

Test with `VOICE_PROVIDER_URL` set to your HTTPS `/v1` URL using
`tests/remote-voice-live.py` on the server. The test reads the private token file
without printing it. See [M8 evidence](../../tests/m8-evidence.md).

CPU synthesis can remain slower than playback. Sentence preparation reduces
initial waiting but cannot guarantee gapless long replies on this server.
