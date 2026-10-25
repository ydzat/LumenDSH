# Phone-local hoyoTTS voice

The user accepted pretrained Silver Wolf speaker 221 as a lower-memory alternative
to the original GPT-SoVITS voice. The runtime keeps the existing authenticated
HTTP contract, engine ID and Android pairing. It does not replace DSH or require
an APK update. Model fine-tuning is deferred.

## Reuse and provenance

`hoyotts-manifest.json` pins the model/source revisions, downloaded file hashes,
converted model hashes and Python frontend dependencies. The model is
[Genius-Society/hoyoTTS](https://huggingface.co/Genius-Society/hoyoTTS), revision
`69fe1ac0ba1f1caf7522369ba5d002b493331b6f`. The matching Space source revision is
`ad1ac8242554a2f203ca67622f90b109f75b3b35`.

`hoyo-frontend.py` calls upstream `text.chinese.text_normalize`, `g2p` and
`cleaned_text_to_sequence`; the upstream implementation is downloaded separately.
Tone sandhi retains PaddlePaddle's Apache-2.0 notice. jieba 0.42.1, pypinyin 0.55.0,
cn2an 0.5.24 and proces 0.1.7 are MIT-licensed. Setuptools 78.1.0 is retained for
the legacy jieba resource loader. Their wheel metadata and license files remain
with the private isolated `hoyo-deps/` directory; nothing is installed globally.
The user approved these frontend dependencies explicitly.

The model card declares CC-BY-NC-ND-4.0. Model files, converted weights and generated
voice clips remain local and excluded from Git. Source-code licensing does not
grant permission to distribute character weights.

`hoyo_native.rs` reuses existing pinned ort 2.0.0-rc.13, ONNX Runtime 1.27.0,
tokenizers and hound dependencies. Its model computations call the exported
upstream network; no new phoneme, tone-sandhi or waveform model is implemented.

## Stages and memory

1. Python runs upstream Chinese normalization and G2P, splitting normalized text
   at punctuation or 48 characters. It emits phoneme, tone, language and alignment
   arrays. Latin letters cause an explicit Chinese-only error instead of silently
   disappearing; system speech remains the selectable multilingual option.
2. Native `prepare` loads quantized Chinese RoBERTa and the matching tokenizer,
   then writes aligned binary features into the private request directory.
3. That process exits. Native `synthesize` loads the 162 MiB hoyoTTS graph and
   processes one bounded segment at a time, producing mono 44.1 kHz PCM16 WAV.

The server serializes requests, retains duplicate-request and cancellation rules,
and deletes request text/features after success, failure or cancellation. One
300-second deadline applies across the full request. Each child has a 2 GiB virtual
address-space ceiling, a monitored 700 MiB RSS budget and a 768 MiB available-memory
reserve. These are distinct controls; virtual address space is not physical RAM.
The preflight requirement is approximately 1.43 GiB available memory, replacing
the previous GPT-SoVITS path's 2.5 GiB threshold for this backend.

The current response is one completed WAV. Playback does not start while earlier
segments are still being synthesized. Long-reply latency therefore remains open.

## Build and activation

Build `hoyo_native.rs` as an example in the pinned, patched upstream Rust checkout
described in `README.md`: copy it to `examples/hoyo_native.rs` and use the existing
Android API 29 cross-build command with `--example hoyo_native`.

The local export lab is `.local/hoyotts/`. `export-hoyotts.py LAB_DIRECTORY`
exports the pinned upstream `infer` method using actual prepared inputs and checks
three dynamic sequence lengths with zero stochastic scales. Normal inference uses
noise 0.6, duration noise 0.8 and SDP ratio 0.2, matching the accepted audition.
The lab contains preparation scripts, source receipts and locked export packages.

Install beside the existing private token and ORT library:

- `hoyo_native`, `hoyo-frontend.py`, `serve.py`, `start.sh`;
- `hoyo-frontend/text/`: the six pinned upstream frontend files;
- `hoyo-deps/`: isolated pure-Python dependencies and license metadata;
- `hoyo-models/`: `hoyotts.onnx`, `bert.onnx`, `tokenizer.json`.

The BERT graph retains CLS/SEP outputs by replacing its final trimming Slice with
Identity. An actual inference check confirmed identical interior feature values.

`start.sh` now defaults to `hoyotts`. Stop only the separate `lumendsh-voice` tmux
session before switching an already running backend. Pairing tokens stay in place.
The original model files are retained for explicit rollback:
`LUMENDSH_VOICE_BACKEND=gpt-sovits bash ~/lumendsh-voice/start.sh` after stopping
the voice session. Neither command restarts DSH.

## Verification

See [phone evidence](../../tests/hoyotts-evidence.md). The accepted B audition is
a desktop listening result; do not substitute it for acoustic wake validation.
