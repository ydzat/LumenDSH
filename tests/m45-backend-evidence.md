# Personal speaker backend comparison

Status: implementation candidate; human comparison pending.

The user approved fixed-version LIBLINEAR Java and LIBSVM Java for comparing
regularized logistic regression and RBF-SVM on frozen CAM++ embeddings. See
[voice protocol](../docs/voice.md) and [dependencies](../apps/android/DEPENDENCIES.md).

## Split and interpretation

Device fixture test trains both candidates using speaker1_a and its centered
1.2-second crop as positives, speaker2_a and its crop as negatives. It evaluates
speaker1_b and its crop. There is no independent speaker2_b fixture, so the test
reports zero held-out negative samples. Crops from the same utterance never cross
the train/test boundary. The two evaluated views are not two independent recordings.
Results measure this narrow fixture setup, not owner accuracy or false acceptance.

The actual-owner UI uses four training recordings and two independent owner test
recordings, plus the small public negative cohort. No automatic classifier activation
or personal-data export occurs. V03/V05 remain open.

## Real-device result

Huawei YAL-L21 / Android 10, APK 0.4.18 (code 25), built in Termux and installed
with the existing signing identity. APK SHA-256:
`d83e4e1a804d9802045860c8c28294b808e43f838391e0022d6fedeec002ce3d`.

| Backend | Held-out positive views | Held-out negatives | Fit time, one device run |
| --- | --- | --- | --- |
| L2 logistic regression | 2/2 accepted | none available | 9.259375 ms |
| RBF-SVM | 2/2 accepted | none available | 3.741146 ms |

The positive views are the full speaker1_b recording and its centered 1.2-second
crop. The original manager comparison reached only 0.26 on that crop versus its
0.6 gate. These timings measure solver fitting for this tiny dataset, not final
recording-to-result latency or a latency distribution. The user's comparison will
measure the latter including final feature extraction. No general accuracy or
non-owner rejection rate can be inferred.

Host compilation, Termux build/signature verification, `git diff --check`, and
OwnerChecks (native extraction, public fixtures, isolated encrypted storage, binding
and tamper rejection) passed. The installed UI exposes the credential-protected
comparison button and still reports the existing owner record. Actual-owner trials
remain pending. No milestone-completion video is claimed before that result.

## 0.4.19 single-button and existing-owner adaptation

Installed signed APK SHA-256
`3ec4e13c610330b89b62978d5deabc28e84179a7f56a7ac5ccbdc9b87f3bd38a`.
Host compilation, phone build, diff check, and OwnerChecks passed. Tests additionally
exercise adaptive backend selection with public fixtures, independent positive
utterance recognition, and rejection of the training negative (not an independent
negative acceptance test).

UI inspection found exactly one button, `录入声纹`, with the existing-owner message.
No new personal recording was requested or made. After enabling standby, actual
service diagnostics reported `ownerProfile=long_readings/logistic`, `ownerFitMillis=10`,
and `state=standby`. The existing encrypted owner vectors were used internally and
not exported, overwritten or re-enrolled. The timing covers backend selection and
final fit, not cold model loading/background feature extraction. This establishes
actual-owner adaptation execution, not successful acoustic owner wake. V03/V05
remain open pending normal use observations.

## Human feedback after 0.4.19

The user reported successful but unstable wake. This is the first positive human
owner-wake feedback; no measured hit rate, latency distribution or non-owner rate
was provided. Do not upgrade this observation to complete V03/V05 acceptance. The
user requested automatic learning only from highly matching samples, and retained
system recognition with wake-audio reuse only.

## 0.4.20 conservative wake learning and response grace

Built in Termux, signature-verified and installed in place. APK SHA-256:
`aaef3b0fccd69d6d961557f6305025a83548911febccc744aa6af0fd7c79f137`.
Device OwnerChecks passed the prior model/storage checks plus strict admission
rejection of the public low-similarity short clip, clipping/length quality bounds,
first learned-entry encrypted roundtrip, over-capacity rejection preserving the
previous record, and initial-silence retry bounds (speech begun, deadline, retry
limit, and unrelated error stop retries). These are executed code-path checks,
not real provider timing or actual-owner sample-promotion evidence.

The user explicitly retained system recognition and limited learning to wake audio.
No ASR model or inference dependency was added. Ready-triggered cue and actual
provider grace duration remain to be observed during normal use; learning counters
start at zero. Original owner records remain intact. V03/V05 are still partial.
