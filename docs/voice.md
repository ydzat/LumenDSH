# M3 Voice Preview

Status: foreground implementation built and installed; the user confirmed two
successful spoken requests and audible replies (V01, tap-per-request policy).
This preview is not the completed M3 voice lifecycle.

## Platform evidence

The target Android 10 phone exposes GoogleRecognitionService as its selected and
only queried `android.speech.RecognitionService`. This establishes discovery, not
working Chinese recognition or offline support. Android's
[SpeechRecognizer contract](https://developer.android.com/reference/android/speech/SpeechRecognizer)
requires main-thread calls, RECORD_AUDIO permission, and destruction after use.
It warns that implementations may stream audio remotely and are unsuitable for
continuous recognition. The
[RecognizerIntent contract](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE)
also allows implementations to ignore the offline preference.

## Preview policy

- A user tap starts one Chinese recognition request; no automatic listening loop.
- Microphone permission is requested through Android after explaining provider audio
  processing. The client retains no recordings. Provider behavior is separate.
- Default behavior produces a draft. The visible auto-send checkbox also enables
  reply playback for that voice request; it resets on Activity recreation.
- Auto-send uses the existing selected session and M1 submission journal. It never
  creates a session. Existing drafts must be sent or cleared first.
- Reply playback correlates the submitted RPC ID to its enclosing server turn and
  waits for successful turn completion. It reads the last textual assistant message,
  up to 1,000 characters. Longer replies remain in the transcript with a visible notice.
- An explicit speech tool call in the same turn suppresses additional ordinary
  reply playback. Playback uses the existing TTS operation journal and audio focus.
- Recognition callbacks from a cancelled request are ignored; a 30-second timeout
  releases the recognizer. Errors require a new user tap, never silent retries.
- Stop voice cancels recognition and playback locally, and disarms pending automatic
  playback. It does not cancel a task already submitted to DSH.
- Hiding the Activity, changing session, or observing a disconnect stops the voice
  request. Reconnection/process recreation does not re-arm reply playback.
- A tap on Speak interrupts current playback before listening. A new MCP speech
  request is refused while recognition is active; retries of retained speech
  operations still return their existing result without replay.
- Recognition and playback refuse to start in a non-normal Android audio mode.
  While recognition is active, the client checks audio mode every 500 ms and stops
  when communication audio becomes active. Actual incoming-call behavior remains
  unverified; this is not a call-state listener or a complete phone-call guarantee.
- The installed Google recognizer owns audio focus. The client does not request a
  competing focus lease for recognition. TTS yields on audio-focus loss and does not
  resume automatically. These responsibilities differ from exclusive microphone access.
- Acoustic barge-in, wake words, continuous conversation, foreground services, and
  lock-screen operation are not implemented in this preview.

## Remaining gates

V01 passed the user-operated two-request foreground test; see
[device evidence](../tests/m3-preview-evidence.md).
V02 passed tap-to-interrupt and local microphone-stop checks. V04 passed the TTS
focus-conflict subset only; actual calls and backend-loss voice recovery remain open.
V03 remains open. See [interruption evidence](../tests/m3-interruption-evidence.md). System service
discovery and APK compilation cannot satisfy these gates. The final wake engine and
continuous-conversation policy remain separate M3 decisions.

On 2026-10-24 the user deferred remaining wake-related validation to allow M4
development to proceed. No additional wake or incoming-call checks should block
that work. Deferred capabilities must remain visibly unavailable, not enabled by
default or represented as passed.

Platform references: [audio focus](https://developer.android.com/media/optimize/audio-focus)
and [Android 10 input sharing](https://developer.android.com/media/platform/sharing-audio-input).

## Overlay interaction revision (2026-10-25)

The user requires hands-free overlay interaction. The overlay contains the character
and task status only; drag/dock remains available. Management actions live in the
notification and application settings. The habitat retains its visible manual voice
button and removes the duplicate character long-press voice shortcut.

Automatic wake is now an active M4.5 implementation requirement, superseding its
earlier implementation deferral. The button-removal build did not implement it; the
subsequent implementation and evidence are tracked below.
The proposed maintained engine is sherpa-onnx, approved by the user on 2026-10-25, with separate model provenance verification. Its official keyword-spotting documentation
provides Android examples and configurable keywords:
https://k2-fsa.github.io/sherpa/onnx/kws/index.html

The intended lifecycle is local keyword detection, command recognition, submission to
the existing session, one correlated spoken reply, then return to standby. Microphone
ownership must be shared between foreground and background modes, with wake detection
suspended during recognition/playback. No repeated SpeechRecognizer polling or
automatic Activity launch counts as background wake. V03 and outstanding V04 device
checks remain unverified; implementation does not imply low power or lock-screen support.

## Approved configurable wake phrase

The user approved sherpa-onnx and specified **"嗨，<Agent name>"**, currently
**"嗨，银狼"**. The source of truth is `PresentationSettings.name`, not the asset
package name, APK label, session title or a separate hard-coded wake setting.
A name change discards the prior keyword stream before constructing the new one.

The initial engine uses sherpa-onnx 1.13.8 and the WenetSpeech 3.3M Chinese KWS model
(epoch 12, chunk 16, int8 encoder/joiner, fp32 decoder). The downloaded model's
README explicitly declares Apache License 2.0. Android API 29 ICU Han-Latin supplies
pinyin; model tokens are checked before listening. The initial model accepts Chinese
names only. Unsupported names fail visibly rather than retaining an old phrase;
polyphonic names still need pronunciation review and acoustic acceptance.

The implemented preview is explicitly enabled in settings and stopped through a
foreground notification. While the Activity is visible, only its manual voice entry
is active. Outside it, local KWS detects the complete greeting, releases AudioRecord,
plays a short cue and starts one system recognition request. The user must speak the
command after the cue: this is not yet a seamless wake-and-command utterance.
Recognition may use the installed provider's network service. PCM used for KWS is
not stored. The service submits to the existing selected conversation and uses the
same request-correlated reply selector and MCP/TTS owner as foreground voice.
It resumes KWS after playback and a cooldown; errors never poll SpeechRecognizer.
A disconnect, session/name change, foreground return or notification stop disarms
pending playback. Uncertain submission is not retried. Process death does not replay
speech or automatically restart this preview's microphone.

The overlay contains no interaction buttons. Its status reflects the voice service.
Build success, native-model initialization and PCM checks are separate from real
spoken wake acceptance, false-trigger rate, call interference, battery and lock-screen
reliability. V03/V04 remain open until those observations are recorded.

### Keyword matching and calibration

Wake matching is acoustic KWS over partial-pinyin tokens, not a full SpeechRecognizer
transcript equality check and not speaker-embedding similarity. The name is encoded
into model tokens before recording. The greeting has four tone variants of `h ai`,
each followed by the complete configured name. Keyword score 1.5 and threshold 0.25
match the pinned upstream Android defaults. These variants do not permit name-only
activation. Acoustic and false-positive acceptance remain open after tuning.

## Owner-bound wake requirement (2026-10-25)

The user requires speaker verification: other people's speech should not wake the
assistant. Enrollment and verification therefore precede any claimed hands-free
completion. A wake-word hit is only a candidate. Before the cue or system recognition,
the client verifies the candidate audio against the enrolled owner; a mismatch stays
silent and returns to local keyword detection. No enrollment means no background wake.

Enrollment is an explicit foreground flow protected by Android device-credential
confirmation. The user reads three passages and one held-out wake phrase. Silero VAD
selects speech, the approved sherpa-onnx runtime extracts CAM++ speaker embeddings,
and enrollment rejects inconsistent samples or a failed held-out phrase. Only three
embeddings are saved, encrypted with an Android Keystore AES-GCM key in private app
storage. Raw enrollment/verification audio stays in bounded memory and is not uploaded,
saved, exposed through MCP, or committed. Replacing/removing the owner also requires
system identity confirmation; cancelling enrollment keeps the previous record.

Owner identity is independent of the Agent display name and appearance. Renaming the
Agent changes the complete greeting but preserves the owner voiceprint. The threshold
0.6 is a conservative implementation choice, not a measured error rate or a universal
model guarantee. Actual owner and non-owner trials remain mandatory. Speaker verification
is not liveness detection: replay/voice synthesis resistance is not implemented, and
voice identity never substitutes for Android unlocking or backend tool approvals.

### Phrase-specific owner calibration

The owner settings page offers a separate credential-protected short-phrase
calibration. It records three natural repetitions of the current greeting and uses
a fourth repetition as a held-out check. Existing pairwise consistency and held-out
verification at 0.6 remain required. The three short embeddings are encrypted in a
separate Android Keystore record; the original long-reading record is preserved.
The encrypted payload binds the template to the model and exact Agent name.
Background wake requires the original owner record, then selects the calibrated
short template when its binding matches. A renamed Agent uses the original record
until recalibrated. Corrupt calibration fails explicitly. Owner replacement/removal
invalidates calibration. Cancellation does not save a candidate template.

Reading prompts appear only after AudioRecord has started; the preparation interval
shows no text to read. This avoids dropping the beginning of a short phrase while
the user reacts to a prompt before recording starts. No personal PCM is persisted.
This is an implementation candidate, not evidence that short-phrase human acceptance
or non-owner rejection has passed V05.

### Personal backend comparison experiment (0.4.18)

The credential-protected settings action compares regularized logistic regression
(LIBLINEAR Java 2.44, L2R_LR, C=1, epsilon=0.001, max iterations=300, bias=1) and
RBF-SVM (LIBSVM Java 3.35, C-SVC, C=10, gamma=1, epsilon=0.001, 8 MB cache).
Both use unit-normalized frozen CAM++ embeddings and inverse-frequency class weights.
These fixed candidate hyperparameters are design choices, not fitted to test results.
No probability is reported as a calibrated owner confidence.

Four natural wake-phrase recordings train the candidates; two later recordings are
held out completely. The experiment does not reject training recordings because
they fail the legacy 0.6 similarity threshold; VAD quality checks still apply.
No data augmentation is currently used. Public speaker1_a and speaker2_a embeddings
provide negatives; speaker1_b is a separate negative utterance used only for evaluation.
This background cohort has only two speakers and is not an unseen-speaker benchmark.

Background features are computed before the first prompt; positive features are
computed as each recording ends. The UI reports each solver's fit time and elapsed
time from the final recording's end through feature extraction, fitting, and scoring.
The target is 3 seconds on the Huawei nova 5T. Numeric code and model weights stay
in memory; this comparison neither saves a new profile nor changes standby behavior.
Raw personal audio and embeddings never leave the phone or enter logs. The existing
encrypted owner and short-phrase records remain intact. Selection/activation requires
the actual-owner comparison result; public fixture success is insufficient.

### Current single-action enrollment (0.4.19)

Supersedes the exposed calibration/comparison UI above. The only button is
`录入声纹`; Android back navigation leaves the page. Successful existing encrypted
records are reused without requiring new recordings. Adaptation happens privately
before standby: compare logistic regression and RBF-SVM by leave-one-recording-out
owner checks plus the separate public background utterance, then fit the selected
backend on all existing owner samples. Ties choose logistic regression. These are
model-selection observations, not an independent final acceptance test. No fixed
0.6 similarity gate is used for adapted wake. Background features are cached in the
service worker. Stored owner vectors remain unchanged; fitted classifiers live in
memory and are rebuilt when standby starts. Numeric diagnostics include backend and
fit duration, never embeddings or audio.

New enrollment remains protected by device credentials. Three wake-phrase samples
train the backend and a fourth independent phrase checks it before saving. Cancellation
keeps the original record. The old comparison implementation is not a user-facing
operation. Existing successful short-phrase templates remain usable for their bound
name; otherwise the original owner samples are used. Reusing long-reading samples
does not itself prove short-phrase wake acceptance.

### Conservative learning from wake audio (0.4.20)

The user confirmed that 0.4.19 wakes successfully but inconsistently, then authorized
automatic sample learning. Scope is explicitly limited to client-owned wake PCM;
keep system SpeechRecognizer and do not add a new ASR model. Android's optional
`onBufferReceived` has implementation-dependent rate and cannot supply a reliable
training stream. No concurrent recorder is opened beside the system recognizer.

Admission is stricter than wake classification: sufficient VAD speech, no non-finite
samples, no more than 1% clipping, acceptance by the adapted backend, and a separate
0.6 upstream speaker-manager match against the unchanged credential-enrolled anchors.
Learned samples cannot authorize their own admission. This is a conservative score
criterion, not a calibrated probability or proof against all other speakers. Only
after a command is recognized and submitted to the unchanged selected session is
the pending embedding committed. Cancellation, foreground return, stale generation
and failed recognition do not promote it. Raw PCM remains memory-only.

Up to nine distinct learned embeddings are kept in a separate AES-GCM record, bound
to the original encrypted owner record hash, model and Agent name. The original
record is never overwritten by learning. Near-duplicate vectors (cosine >0.995)
are skipped; oldest learned entries rotate at capacity. The next standby refits on
anchors plus the bounded learned set. Name/owner changes invalidate that learned
record. Diagnostics expose count and status, not embeddings/audio.

The wake cue now occurs at SpeechRecognizer.onReadyForSpeech, not before startup.
The system recognizer receives a 10-second minimum-session request and 2-second
silence requests; providers may ignore these hints. Before any speech begins, only
NO_MATCH/SPEECH_TIMEOUT may retry within the same request, at most twice and only
within 10 seconds of first readiness. Retries never replay a task or emit another cue.
The entire request remains bounded to 30 seconds and stops on call/foreground/cancel.
This is a grace window, not a guarantee of uninterrupted capture across provider
restarts. Real provider timing and human comfort require actual-use observation.

References: [RecognitionListener](https://developer.android.com/reference/android/speech/RecognitionListener#onBufferReceived(byte[])),
[RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS),
[Android audio sharing](https://developer.android.com/media/platform/sharing-audio-input).
