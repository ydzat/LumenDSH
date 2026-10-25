# Prepared character voice experiment

Current default: the accepted [hoyoTTS backend](hoyotts.md) is deployed through
the same authenticated service. The GPT-SoVITS preparation and evidence below
remain the reference for the original voice and explicit rollback.

Status: the user accepted the voice; bounded native synthesis and Android 0.7.48
playback integration are verified. The deployed profile uses local Silver Wolf
speech; system TTS remains an explicit profile option. See
[deployment, lifecycle and app evidence](../../docs/character-voice.md).

## Architecture

The preparation computer processes reference speech once. The phone receives
shared synthesis models and a character conditioning file, then synthesizes new
text locally. It does not run the reference SSL, speaker encoder, or T2S reference
encoder. The conditioning file contains model-specific tensors, not a universally
interchangeable speaker embedding.

`freeze-reference.py` extracts and executes the VITS reference-only graph on the
computer. Its output graph accepts the computed tensors as inputs. The character
JSON stores these tensors together with reference phonemes, BERT features, and
semantic prompts. Raw reference audio is removed from the playback JSON.
Conditioning files must match the exported model contract, including tensor shapes;
arbitrary character packs have not been validated.

## Reused implementation

- [gpt-sovits-onnx-rs](https://github.com/bailu-obj/gpt-sovits-onnx-rs), revision
  `204ae4abcd218113aad86f6f9a66bdba92adcfd7`, MIT. License: `upstream/LICENSE`.
- Local changes: `upstream/reference-cache.patch`. They add reference export/load,
  skip reference models during playback, accept prepared VITS conditioning, and
  select dynamic ONNX Runtime loading. The staged mode separates text processing
  from waveform generation; low-memory settings disable arenas, prepacking,
  memory patterns and spinning, and use two inference threads. The synthesis implementation is
  retained. `git apply --check` succeeds against the pinned revision.
- ONNX Runtime Android 1.27.0, ARM64, official Maven AAR SHA-256:
  `077dec5e2d821234c7dc0aba584bec8f999854b546c754cab93a90741c56fbeb`.
- Rust `ort` 2.0.0-rc.13, source revision
  `002f41a8e175eac7f6695ff361d2e51a50874c48`, MIT/Apache-2.0.
  `upstream/ort-android-exit.patch` reuses its Apple cleanup-registration strategy
  on Android: register environment destruction after the native library is loaded,
  rather than in `.fini_array` after native static destructors. This fixes the
  observed successful-computation/exit-time mutex abort. Both licenses are retained
  beside the patch. It does not suppress abort signals or treat failed exits as success.
- Generic v2Pro models: [mikv39/gpt-sovits-onnx-custom](https://huggingface.co/mikv39/gpt-sovits-onnx-custom),
  revision `69063da489c62f34fffc40acd10e2167623509aa`.
  Individual files were checked against repository LFS SHA-256 metadata.
- Private evaluation reference: [WLWolf56/gpt-sovits-model](https://huggingface.co/WLWolf56/gpt-sovits-model),
  revision `8ca20dd1f84feda57396531ef5413130a5d9cc60`, Silver Wolf sample.
  These are generic synthesis weights conditioned on that reference, not a
  verified Silver Wolf fine-tuned checkpoint.

Audio, downloaded weights, character features and local receipts remain ignored.
This experiment does not establish redistribution permission for voice assets.

## Reproduction

Build the patched upstream demo on a computer. The verified Windows setup used
MSVC 2019, Ninja, Rust stable, and NDK r27c with target
`aarch64-linux-android29`. Use `ort`/`ort-sys` 2.0.0-rc.13 with API 27 and
`ORT_DYLIB_PATH` pointing at the corresponding platform's ONNX Runtime 1.27.0.
Do not use upstream's older Android runtime download unchanged.

Place the patched `ort` crate at `../ort-runtime` relative to the patched upstream
checkout; the Cargo patch selects it. Keep the crate version at 2.0.0-rc.13.

1. Export the reference using the patched demo's `--export-reference` option,
   original models, reference WAV, and its transcript.
2. On the computer, install `onnx==1.19.1` and `onnxruntime==1.27.0`, then run:

   ```text
   python freeze-reference.py models/vits.onnx reference-cache.json playback/vits.onnx
   ```

3. Supply the original `bert.onnx`, `g2pW.onnx`, `g2p_en/`,
   `t2s_fs_decoder.onnx`, and `t2s_s_decoder.onnx` beside the exported graph.
   Do not include `ssl.onnx`, `sv.onnx`, or `t2s_encoder.onnx` in playback.
4. Run the patched demo with `--model-path playback --reference-cache
   playback/vits.reference.json --text <text> --output <wav>`.
5. For graph parity, capture actual synthesis inputs using the lab-only
   `LUMENDSH_VITS_CAPTURE` environment variable; run `check-frozen-parity.py --help`
   for its positional arguments. Both comparison graphs use the same random seed.

Phone probes use `probe-playback.py`, a process address-space limit, a timeout,
and a reserve for existing services. A failed bounded allocation is not proof
that all quantized or differently scheduled implementations are infeasible.
Phone compilation is suspended following the documented build-time OOM reboot.

The verified phone command is:

```sh
python probe-playback.py /path/to/playback --low-memory --staged --text '你好，我是银狼。'
```

The lab executable and runtime are in `~/lumendsh-voice-lab/`. The first child
process runs the real upstream text models and exports phonemes/BERT features;
it then exits and releases those models. The second child loads only synthesis
models and the small prepared text/voice data. It rejects a text cache belonging
to another request. Temporary text features are removed even on timeout or failure.
This mode currently targets Mandarin/mixed-language `Auto`, not Cantonese mode.
These on-device text features are distinct from the character's voice features,
which remain prepared once on the computer.

## Evidence

- Windows and Android API 29 ARM64 release builds succeeded.
- Android executable `--help` succeeded on the nova 5T.
- The bounded Android synthesis probe failed while loading BERT with
  `std::bad_alloc` at a 1,777,020,928-byte address-space limit. Upstream cleanup
  then aborted with a destroyed-mutex error. No audio was produced; ADB remained
  connected and the existing embedding service returned healthy. This limit is
  virtual address space, not a measurement of peak resident memory.
- Prepared desktop synthesis generated real 32 kHz audio; the measured synthesis
  call took 4.65 seconds for the demonstration text (one run, not a benchmark).
- Shared VITS graph parity on actual synthesis inputs: maximum absolute error
  `1.259148120880127e-06`, RMS error `1.2481194744395907e-07`.
- Character JSON: 1,196,678 bytes, including 18 VITS conditioning tensors and no
  reference waveform. Playback model files are approximately 1.48 GiB, about
  652 MiB smaller than the original set. Disk savings are not RAM measurements.
- The user accepted the desktop sample as the desired Silver Wolf voice.
- After staged loading and the exit-order fix, two distinct phone requests passed:

  | Request | Text stage | Synthesis process | Sampled peak RSS (text / synthesis) | Output |
  | --- | --- | --- | --- | --- |
  | `你好，我是银狼。` | 3.84 s | 10.92 s | 935 / 866 MiB | 2.54 s WAV |
  | `今天的任务交给我，你先休息一下。` | 4.85 s | 11.54 s | 935 / 865 MiB | 2.74 s WAV |

  Both stages exited 0. Times include cold process/model loading; RSS was sampled
  every 100 ms and is not an exact peak. Both WAVs contain finite, nonzero samples.
  These are two real runs, not throughput or long-utterance acceptance.
- A real 0.5-second timeout test killed the child, skipped the synthesis stage,
  cleaned temporary text data, and produced no WAV. A desktop real-model test
  rejected a prepared text cache for a different request.
- Subsequent Android integration passed foreground playback, interruption and
  profile activation checks; see the linked integration evidence. A new acoustic
  wake-loop or incoming-call acceptance has not been performed.

Inspection found the downloaded BERT/G2PW models already contain 132/72
`MatMulNBits` operators and Q8 weights. Reusing upstream's
`quantize_text_quality` function produced unchanged file sizes (401,805,376 and
391,088,627 bytes). Repeating that quality profile does not solve the observed
loading failure. Staged model residency resolved the tested loading failure;
no unbounded phone retry was used. Application playback is integrated; further
cold-latency optimization remains open.

Local audible sample: `artifacts/voice-preview/silver-wolf-preview.wav`
(4 seconds, 32 kHz, PCM16; converted from the real floating-point output).
Real phone samples: `artifacts/voice-preview/silver-wolf-phone.wav` and
`artifacts/voice-preview/silver-wolf-phone-second.wav` (32 kHz PCM16 conversions).
The native-stage WAVs are not recordings of speaker playback. The later app
integration and its demonstration are documented in the linked integration notes.

## M8 current entry point

Use [provider configuration and deployment](providers.md) for Android 0.8.0.
The service supports per-request local engine selection and the app can use a
separate HTTPS speech server. The historical default above describes the prior
phone-local installation; the current phone selects remote Silver Wolf.
