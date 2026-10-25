# M4.5 Owner Voiceprint Evidence

## Installed candidate

Huawei nova 5T / YAL-L21, Android 10. Independent Termux build 0.4.15 (code 22),
APK SHA-256 `d9716478f95b5fd88561ff248dc12102d165fd5879967a168eaf6f6748f2b8a8`.
Java/DEX/APK construction, existing signing identity verification and in-place
installation succeeded. No DSH deployment change or restart was made.

## Implemented boundary

A valid locally enrolled owner is mandatory for background voice. KWS retains a
bounded four-second in-memory PCM window; each keyword candidate is filtered by
Silero VAD and compared through sherpa-onnx CAM++ speaker embeddings. Only an accepted
owner reaches the cue and system recognition. A rejected speaker remains silent.
Owner data is independent of the configured Agent name. The complete greeting
remains `嗨，<Agent name>`; the name alone is not a keyword.

The foreground enrollment page requires Android device-credential confirmation before
enrollment/replacement/deletion. Three passages produce three embeddings; a separate
wake-phrase reading must match before saving. Raw recordings are never written.
The voiceprint is an AtomicFile containing AES-GCM ciphertext under an Android
Keystore key. No MCP endpoint can enroll/export/delete owner embeddings.

## Executed real-device lower-level checks

The separately signed instrumentation APK loaded the production model/VAD and ran
against the model author's pinned public v1.0.0 example WAVs. No fixture voiceprint
was written to the actual owner record.

- Real CAM++ native initialization and Silero VAD processing succeeded.
- Public same-speaker samples matched at the candidate 0.6 threshold.
- A public different-speaker sample was rejected at that threshold.
- Zero PCM was rejected as insufficient speech.
- An isolated encrypted test record round-tripped through Android Keystore.
- Altered ciphertext was rejected with authentication failure. The isolated record
  and its temporary key were removed by the test.
- With no actual owner record, tapping enable opened owner enrollment and did not
  start WakeVoiceService. The service was absent from the Android service listing.
- The system credential flow was opened and cancelled without inspecting or entering
  credentials. The page reported that identity confirmation failed and the voiceprint
  remained unchanged. There was no path into recording after that cancellation.

The test output was `OWNER_NATIVE_VAD_PUBLIC_SPEAKER_AND_KEYSTORE_CHECKS_PASS`.
These observations do not establish a human owner's acceptance rate or non-owner
false acceptance rate. The earlier unguarded wake failures remain recorded in
[m45-wake-evidence.md](m45-wake-evidence.md).

## Pending user-owned steps

The actual user confirmed successful enrollment ("主人声纹已保存"). No actual
owner has been substituted by a synthetic voice or test fixture. The subsequent
human wake attempt produced no cue. Sanitized service diagnostics recorded five
keyword candidates rejected by the owner gate, zero accepted hits, zero recognized
commands, and zero replies. That build did not distinguish insufficient speech
from speaker mismatch. V05 owner wake acceptance has failed; non-owner human
acceptance remains NOT RUN, and V03 remains open.

Candidate 0.4.16 preserves the enrollment and threshold (0.6), collects 600 ms of
post-trigger microphone audio before speaker verification, and reports only a
rejection category plus the VAD speech sample count. It does not retain audio or
export embeddings. A truncated wake utterance is a hypothesis, not an established
cause. Human retest is required before accepting this change.
No replay or synthetic-voice liveness defense is implemented. Tool approval and
Android unlock requirements are not waived by a voiceprint match.

A successful owner-gated wake demo will be recorded after real enrollment and a
controlled successful request. The existing standby-only clip predates mandatory
owner verification and is not evidence of this behavior.

## Candidate follow-up validation

0.4.16 (versionCode 23) built in Termux and installed in place with the existing
signing certificate. APK SHA-256:
`a5b06740f7920c4b0442d03a813fdb16b2d43e13725c9f3f2897704abf25c987`.
Host Java compilation, `git diff --check`, and the existing real-device OwnerChecks
passed. The latter uses isolated public fixtures/storage, not the actual enrolled
owner. The actual enrollment was preserved and the service was re-enabled without
re-enrollment. Human wake retest remains pending.

## 0.4.16 human failure and short-utterance investigation

The user again reported no cue. Sanitized diagnostics showed three owner rejections,
zero accepted hits, and last rejection `speaker_mismatch` with 18,560 VAD speech
samples (1.16 s at 16 kHz). This rules out the insufficient-speech branch for that
last candidate; it does not establish the reason for its low similarity.

OwnerChecks was extended to compare the public model-author fixtures at full length
and a centered 19,200-sample (1.2 s) crop. On the actual Android CAM++ runtime, the
upstream manager accepted the same-speaker full sample through threshold 0.60, but
the cropped sample only through 0.26 (0.01 probe increments). The different-speaker
full and cropped samples did not pass 0.01. These are two public speakers, not an
error-rate benchmark or actual-owner score. Native/VAD/encrypted isolated storage
checks still passed. No personal audio or voiceprints were exported or replaced.

The original CAM++ model card uses an example threshold of 0.31; our 0.6 originated
from a different upstream speaker example and is not CAM++ calibration. Even 0.31
would reject the public short same-speaker crop. Therefore the production threshold
has not been lowered in response to this failure. Short-phrase enrollment/calibration
and recording-distance differences need investigation before a new acceptance claim.
V03 and V05 remain failed/open.

## 0.4.17 short-phrase calibration candidate

Implemented a separate credential-protected three-sample short-phrase template with
a fourth held-out verification, encrypted name/model binding, original owner-record
preservation, and invalidation on owner replacement/removal. The threshold remains
0.6. Prompt display now follows microphone start instead of preceding it by 700 ms.
The former timing could clip a quickly spoken greeting; it was found in source and
is not proven to explain the user's prior enrollment. Actual human calibration and
owner/non-owner wake acceptance remain pending.

0.4.17 (versionCode 24) was built in Termux, signature-verified, and installed in
place. APK SHA-256:
`4b369b252ddb4624a23485f0783257057ffc740e5c76de78877304039dd1e36f`.
OwnerChecks passed on-device, including the new encrypted binding mismatch rejection
and unchanged-record check, existing public-speaker/VAD checks, authenticated
encryption roundtrip, and tamper rejection. The real owner was not used by tests.
The settings page still reports the existing owner record and displays the new
short-phrase calibration button. Personal calibration is pending; the service is
paused while this foreground credential-protected setup page is open.
