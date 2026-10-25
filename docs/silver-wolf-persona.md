# Silver Wolf personality and voice

Status (2026-10-25): persona revision 2 is deployed in the existing phone workspace.
The accepted character TTS is active in Android 0.7.48; see
[character speech integration and evidence](character-voice.md).

## Personality

The executable instruction source is
[`integrations/companion/persona.md`](../integrations/companion/persona.md).
It expands the original companion baseline with personality, emotional continuity,
relationship development, spoken delivery, contextual touch reactions and bounded
initiative. It is a fan interpretation of Silver Wolf from Honkai: Star Rail.
The default name remains independent of the LumenDSH application brand.

Research references:

- [S_Wolf config_data.py](https://github.com/yese6g/S_Wolf/blob/605be99b075bdeef6a6328c8b3bbab36c5c8882b/config_data.py),
  revision `605be99b075bdeef6a6328c8b3bbab36c5c8882b`, MIT. Reference for combining
  a Silver Wolf persona with a functional technical assistant. No application,
  memory engine, tool configuration or fictional capability list was imported.
- The locally inspected `dsh-whale-persona` persona v8.1.4, revision
  `4f884899df629b88c331e96151225dc825c15c96`, MIT: reference for contextual emotion
  and continuity during work. Whale identity, relationship labels, physical traits
  and dialogue were not transplanted. This profile is newly written English text.

## Deployment and evidence

Run `node update-persona.mjs` alongside `persona.md` in the existing Termux
environment. The updater replaces only the existing marked persona section of
`~/agent-workspace/AGENTS.local.md`. It retains surrounding instructions byte for
byte, saves a private backup, checks for concurrent edits and reads back the result.
It does not restart DSH, change the selected session, install a plugin or touch
memory databases. The historical `v1` marker remains the section identifier;
`Persona revision: 2` identifies its current contents.

Verification performed:

- `node tests/persona-update.mjs`: surrounding-content preservation, idempotence,
  missing/duplicate/reversed marker rejection passed.
- Phone deployment and readback succeeded. Final persona SHA-256:
  `438f6f1b288038cb5b04465b3024c7b01928f09e7ff5e87ce09bc83b07a7203a`.
- The existing isolated M7 test session received the new workspace instructions
  without restarting DSH. A real model response identified revision 2, responded
  to fatigue and touch, retained the unfinished task, and did not claim unrun tests
  passed. No tools were invoked. These are instructed scenario responses, not
  proof of actual busy-task scheduling or long-term personality consistency.
- The first response narrated a physical action. After strengthening the spoken-
  only rule, the repeated real request produced a touch response without stage
  directions. Prompt adherence is not deterministic and needs ordinary use review.

No APK change or rebuild was required. A screen recording was not produced: this
increment was verified through a controlled backend session and file readback,
not a new visible client feature. No owner conversation was used for demonstration.

## Character voice investigation

Preferred candidate: the Silver Wolf model in the
[AI Hobbyist GPT-SoVITS V4 release](https://www.ai-hobbyist.com/thread-953-1-1.html),
credited to 红血球AE3803 and 白菜工厂1145号员工. The inspected release describes
noncommercial use, no redistribution and attribution requirements. Its restricted
download link has not been obtained; current attempts to reopen the page timed
out. Model revision, hashes and reference-audio provenance remain unverified.
Do not package these weights in Git or an APK.

The [altri example](https://huggingface.co/spaces/altri/GPT-SoVITS-Lab/blob/main/app.py)
contains a different Silver Wolf model configuration, CPU inference and reference
audio handling. Its Space was paused when inspected. It is neither a verified
available API nor evidence that its model has the same provenance as the V4 release.

The [GPT-SoVITS requirements](https://github.com/RVC-Boss/GPT-SoVITS/blob/main/requirements.txt)
include a PyTorch-based runtime and additional language/audio processing packages.
Phone inspection found aarch64, Python 3.14.8, and no installed `torch`,
`torchaudio` or Python `onnxruntime` modules. This does not prove inference is
impossible; it establishes that the existing Termux environment cannot currently
run this candidate as installed. Android's separately bundled ONNX runtime does
not supply those Python packages.

`DeviceSpeech` currently delegates to Android `TextToSpeech`; its profiles select
engine/language/rate/pitch. GPT/SoVITS weights cannot be loaded through those
parameters. Changing pitch is not a Silver Wolf voice implementation.

Remaining voice work, in dependency order:

1. Obtain the author's model and reference audio, record exact versions, hashes
   and permitted use, and audition a short controlled Chinese sample.
2. Establish a maintained Android/Termux inference route for that exact model.
   Benchmark cold load, first audio, synthesis time, peak memory and cancellation
   on the target phone alongside the existing embedding and wake services.
3. Integrate with the shared speech owner, preserving audio focus, interrupt,
   one-reply correlation and return-to-standby. Keep model data separate from APK.
4. Validate foreground and background speech on the phone and obtain a listening
   assessment. Record a controlled audible demonstration when this stage works.

No new synthesis runtime or model was installed, no cloud voice service was added,
and no character voice quality or real-time performance result is claimed.

## Reference implementation audit (2026-10-25)

The follow-up audit distinguishes character writing from synthesis and playback.
The Whale persona package itself supplies instructions, not a voice model.

| Project | Inspected implementation | Applicability |
| --- | --- | --- |
| [deepseek-harness-android](https://github.com/guzhou079-arch/deepseek-harness-android) | `VoiceManager.java` uses Android `TextToSpeech`, Chinese locale and utterance callbacks | Already the same engine family as our client; no custom character synthesis |
| [dsh-whale-musume](https://github.com/Sutera-Diffusus/dsh-whale-musume) | README documents optional `dsh-xiaomi-tts` integration | Pet delegates speech to another plugin |
| [dsh-xiaomi-tts](https://github.com/ppy-web/dsh-plugin-xiaomi-mimo-tts) | MiMo/Doubao synthesis plus browser `speechSynthesis`; cloud credentials configured independently | Reusable provider and playback design; using cloud synthesis would send reply text to another provider |
| [dsh-whale-pet](https://github.com/dleaf6211-hash/dsh-whale-pet) | README describes 19 bundled prerecorded lines | Cannot synthesize arbitrary LLM replies; does not meet our touch-response requirement |
| [Fairy-DSH](https://github.com/Chengzhibense/Fairy-DSH) | `fairy-voice/.../local-tts-proxy.js` posts text, reference audio path/transcript to loopback `/tts`, forwards raw PCM with cancellation | Reusable transport/lifecycle ideas; the synthesis engine and character model are external |
| [dsh-gpt-sovits](https://github.com/91koukou/dsh-gpt-sovits) | Existing GPT-SoVITS engine, voice presets, sentence queue, text normalization and browser playback | Reuse candidate for text preparation and synthesis protocol; not a phone inference runtime |
| [ChatWaifu_Mobile](https://github.com/Voine/ChatWaifu_Mobile) | README specifies client-local VITS/ncnn and separately imported models | Evidence of a different mobile architecture, not compatibility with GPT-SoVITS V4 weights |

Source-only checkouts are under ignored `.local/references/`. No plugin was installed.
Pinned inspected revisions:

- Android reference: `a5af7b105b7fdb006e3fc3be2a2179d83907c2cb`.
- Xiaomi TTS (MIT): `1728439dae81831d55dd87ffa99868d4740807c4`.
- DSH GPT-SoVITS (MIT): `90ec8470efd411b21984c9bb04e6ecb9e78a35c0`.
- Fairy: `d639887a386b0de6fef2e61c97dc268631854591`.

### Phone inference candidates

[GSV Mobile](https://github.com/tuxKOH/GPT-SoViTs-android), inspected revision
`94418a31c5510b5b8bfeefddf4db4586aefb9c16`, has actual Android Kotlin code serving
`POST /v1/audio/speech` on `127.0.0.1`. It supports converted V2 Pro Plus/V4 packages
and Android API 26+. This removes the assumption that Termux Python is required.
It is GPL-3.0; no code is imported into our MIT application.

However, its documented V2PP floor is 8 GB physical RAM and large V4 tasks recommend
8--12 GB available process memory. Its README reports a 26.98-second first CPU
request on Snapdragon 8 Elite for a short sentence, including loading. These are
upstream measurements, not nova 5T results. Its experimental QNN path targets
Qualcomm hardware, not Kirin 980.

Read-only inspection of our phone reported `kirin980`, MemTotal 5,765,948 kB and
MemAvailable 2,130,780 kB at that moment. Consequently this full FP32 V4 route is
not the first deployment candidate for this device. No OOM experiment was run.

[gpt-sovits-onnx-rs](https://github.com/bailu-obj/gpt-sovits-onnx-rs), MIT, inspected
revision `204ae4abcd218113aad86f6f9a66bdba92adcfd7`, supplies Android build scripts,
Rust/ONNX inference and conversion for v2/v2Pro/v2ProPlus. Its historical performance
record reports approximately 1.977 seconds end to end for a three-second output
on Snapdragon 8 Elite using its optimized INT8 path. That does not establish
Kirin performance or V4 support. The current v2Pro quality deployment is documented
as roughly 2.12 GiB of model files; file size is not peak working memory.

This is the preferred next local feasibility candidate: verify a compatible Silver
Wolf checkpoint/reference pair, then benchmark the upstream Android implementation
before adding a production dependency or changing the shared speech owner. The
previous V4-only preference is superseded by device constraints. Do not silently
substitute an unrelated narrator voice or prerecorded catchphrases as completion.

Cloud character synthesis remains an alternative requiring an explicit provider
choice and credentials; no private reply or reference audio has been submitted.

### Feasibility execution and OOM incident

The user approved investigating the ONNX route. An isolated native build was
started in Termux under `~/lumendsh-voice-lab`, using official Termux Rust 1.99.1
and two Cargo jobs. No existing packages were upgraded. This was not a client or
DSH deployment.

The phone rebooted during that build. After reconnection, Android reported
`sys.boot.reason=kernel_panic,oom`; this was a real resource failure, not an ADB
transport-only problem. No inference had run. Cargo's job count did not provide a
sufficient memory boundary for this build. Phone compilation is suspended for this
experiment; do not repeat it while the phone hosts the assistant.

Recovery used the existing `agent-start` and embedding startup scripts, without
version/configuration changes. The authenticated DSH session list returned HTTP 200
with 15 sessions and zero running tasks. The embedding health endpoint returned
`status=ok`. No remaining Cargo, rustc, clang, CMake, Ninja or Make processes were
found in the follow-up process check. This verifies service recovery, not the
entire foreground/background audio lifecycle after reboot.

Build findings:

- Upstream's Android script targets API 32, whereas this phone runs API 29.
- Current `ort` rc.13 defaults to ORT API 27; the script's ORT 1.22 library is
  therefore not the matching runtime. The experiment uses the official Android
  ONNX Runtime 1.27.0 AAR, with dynamic loading and default binary downloads disabled.
- Native Termux C dependencies need `--target=aarch64-linux-android29`.
- The pinned `knf-rs-sys` build script assumes an external NDK even for a native
  Termux build. A local native-build adaptation was attempted, but its completion
  was not verified before the OOM reboot. Prefer the upstream cross-build path.

The replacement Windows cross-build succeeded with MSVC, the Android Rust target
and Google NDK r27c. The Android executable's help command runs on the phone.
Downloaded reference audio, models, intermediate files and logs stay in ignored
`.local/voice-feasibility/`.

### Desktop-prepared voice conditioning

Reference extraction now runs on the computer. The phone playback path loads
precomputed semantic prompts and VITS conditioning, without reference SSL, SV,
or T2S encoder models. The character JSON is 1,196,678 bytes and contains no raw
reference waveform. Shared playback weights occupy approximately 1.48 GiB.

Desktop synthesis from this separate conditioning file generated a real WAV at
32 kHz. VITS parity using actual synthesis inputs passed with maximum absolute
error `1.259148120880127e-06`. These checks establish the processing split, not
perceptual Silver Wolf similarity or usable phone latency.

A bounded phone probe reserved 1 GiB of available memory for existing services.
At a 1,777,020,928-byte address-space limit, loading BERT failed with
`std::bad_alloc`, followed by an upstream mutex cleanup abort (exit -6).
No WAV was generated. ADB remained connected and the embedding health endpoint
still returned `status=ok`. This was a bounded process failure, not another
observed phone reboot. Address space is not resident RAM, so this probe alone
does not determine the model's minimum physical-memory requirement.

Implementation, provenance, reproduction and remaining validation are documented
in [the voice integration notes](../integrations/voice/README.md). The installed
application continues to use its existing speech implementation.

### Phone synthesis unblocked

The user accepted the desktop sample as the correct target voice. Its conditioning
is preserved. Staged native execution now runs text preprocessing first, releases
those models, then loads waveform synthesis models. Disabling allocation arenas,
prepacking and spinning alone did not resolve the simultaneous-loading problem.

An independent exit-time abort was traced to the Rust `ort` environment cleanup
order. A pinned local patch reuses its late cleanup-registration strategy on
Android; successful computation now exits normally without bypassing cleanup.

Two distinct Mandarin requests completed on the nova 5T, with both phases exiting
0 and producing nonzero finite audio. Cold total times were 14.77 and 16.39 seconds;
sampled per-process peak RSS remained below 936 MiB. A real timeout stopped the
child and skipped further work. The tone was accepted on desktop; phone speaker
playback, long replies, cancellation through the app, and wake-loop integration
are not yet verified. Existing system TTS remains active until that integration.
