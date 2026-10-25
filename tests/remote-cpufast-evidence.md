# Remote CPUFast evaluation

Date: 2026-10-25. Scope: the user authorized a private server evaluation, with
early stopping once a candidate meets the need. Only CPUFast was installed and
tested. No alternative inference project was deployed. The user listened to the
generated samples and explicitly accepted their voice quality.

## Reused implementation and environment

- Source: <https://github.com/baicai-1145/GPT-SoVITS-CPUFast>, revision
  `03706cce25085a3f2522679193dd437896e0be21`, MIT.
- Debian 12, eight AMD EPYC virtual CPUs, approximately 16 GiB RAM, no exposed
  inference GPU. Existing applications remained running.
- Independent `/opt/lumendsh-tts-test/cpufast` checkout and virtual environment;
  `uv sync --locked` using Python 3.11.5 and the upstream CPU package index.
- Official v2Pro model family: `s1v3.ckpt`, `v2Pro/s2Gv2Pro.pth`, shared
  Chinese HuBERT/BERT and speaker encoder, plus G2PW. The prior accepted A
  ONNX bundle identifies these same source model names. This experiment uses
  native PyTorch weights, not the previous quantized ONNX text frontend, and
  does not claim numerical identity with A.
- The same existing Silver Wolf reference WAV and transcript were used. WAV
  SHA-256: `d2bf32ab6446be1a3391829807abc4c3e92b89b0b55e6d0afc33b9dfb7b9cdbc`.
  Model revisions and individual hashes are retained in ignored
  `.local/cpufast-server/models.json`.

The upstream installer omitted two files required by its Chinese G2PW loader:
`bopomofo_to_pinyin_wo_tune_dict.json` and `char_bopomofo_dict.json`.
The initial synthesis failed with an incomplete HTTP response. Both resources
were obtained from a pinned GitYCC/g2pW revision, with provenance and hashes in
`.local/cpufast-server/extra-assets.json`. No inference code was changed and no
phonetic processing was bypassed.

## Real synthesis results

The native API listened only on `127.0.0.1:19880`. A temporary systemd unit
limited the service to 6 GiB memory and 400% CPU, with four OMP/MKL threads.
Requests used Mandarin, seed 1234, streaming raw PCM, batch size 1, normal speed,
parallel semantic inference and serial waveform synthesis. Received audio was
saved as 32 kHz mono PCM16 WAV.

| Request | Split | First 4,096 audio bytes | Complete synthesis | Audio duration | RTF |
| --- | --- | ---: | ---: | ---: | ---: |
| First successful short request | cut5 | 8.12 s | 8.29 s | 1.74 s | 4.76 |
| Warm short request | cut5 | 2.45 s | 4.44 s | 2.88 s | 1.54 |
| Long reply | cut5 | 3.36 s | 31.33 s | 22.10 s | 1.42 |
| Warm short request | cut1 | 3.55 s | 3.75 s | 2.70 s | 1.39 |
| Long reply | cut1 | 10.76 s | 30.76 s | 22.20 s | 1.39 |

RTF is synthesis wall time divided by generated audio duration. These are
individual runs, not percentile latency estimates. The first successful request
followed a failed attempt which had initialized some components; it is not a
clean cold-start measurement. Timings are server-loopback HTTP measurements,
not phone-to-server playback latency. The first-byte measure waits for 4,096
bytes, rather than counting a response header as generated audio.

The four-sentence split did not materially improve throughput and increased
first-audio delay. Retain `cut5` for this candidate. RTF above 1 means immediate
streaming playback can underrun; fluent uninterrupted streaming is not verified.
All five successful WAVs contained nonzero audio without PCM peak saturation.
The user accepted the cut5 short and long samples acoustically.

Linux reported process VmHWM of 5,194,600 KiB (about 4.95 GiB), with zero
cgroup OOM/OOM-kill events. The test directory consumed approximately 3.7 GiB;
root disk free space was approximately 12 GiB after installation. The temporary
service was stopped after testing, returning available memory to approximately
11 GiB. nginx and all four previously running containers remained running.

## Interface findings and decision

The real OpenAPI document exposed `/tts`, `/control`, `/set_refer_audio`,
`/set_gpt_weights` and `/set_sovits_weights`. It did not expose OpenAI-compatible
speech, model discovery or voice discovery endpoints. Changing global weights
is not proof of independent concurrent multi-model routing.

CPUFast is accepted as the first usable remote inference candidate; do not run
the remaining inference-project comparison merely for completeness. It is not
yet a finished production provider. Remaining integration requirements are:

- A compatible speech endpoint and actual model/voice discovery, preferably
  using existing integration code rather than a new management platform.
- Explicit voice profiles and safe serialization of any global weight/reference
  changes; no claim of tested multi-voice operation yet.
- Provider authentication/TLS and Android provider configuration/playback.
- End-to-end phone latency and playback buffering appropriate to measured RTF.

No public endpoint, production service, APK update, DSH change, Git commit or
push was performed in this evaluation. Voice resources and generated audio
remain ignored and unpublished.

## Local artifacts

- Listening page: `artifacts/voice-server-cpufast/index.html`.
- Real WAVs: `cold-short.wav`, `warm-short.wav`, `long.wav` in that directory.
- Test scripts and receipts: `.local/cpufast-server/`.
- A device demonstration was not recorded: this stage evaluated server synthesis
  only and did not add a phone feature. WAVs are synthesized output, not a
  microphone recording or evidence of Android playback.
