# Runtime Dependency Pins

The user approved adding Java-WebSocket and its required logging interface on 2026-10-24.
`prepare-deps.sh` downloads only the pinned Maven Central artifacts and checks SHA-256.
No third-party source was modified. Full MIT notices ship in `assets/THIRD_PARTY_NOTICES.txt`.

| Component | Revision and artifact | SHA-256 |
| --- | --- | --- |
| Java-WebSocket 1.6.0 | [`da3cf2a777aed862f2f5b5cf060cae7969958667`](https://github.com/TooTallNate/Java-WebSocket/tree/da3cf2a777aed862f2f5b5cf060cae7969958667), tag `v1.6.0`; `org.java-websocket:Java-WebSocket:1.6.0` | `eae29213e4f16515639c28957200f011b3967fffcada1962cf0255d24919c22f` |
| SLF4J API 2.0.13 | [`5ffbc67def52bbb1c9b954676d25f436746d174e`](https://github.com/qos-ch/slf4j/tree/5ffbc67def52bbb1c9b954676d25f436746d174e), peeled tag `v_2.0.13`; `org.slf4j:slf4j-api:2.0.13` | `e7c2a48e8515ba1f49fa637d57b4e2f590b3f5bd97407ac699c3aa5efb1204a9` |

Licenses inspected: [Java-WebSocket MIT](https://github.com/TooTallNate/Java-WebSocket/blob/v1.6.0/LICENSE),
[SLF4J MIT](https://github.com/qos-ch/slf4j/blob/v_2.0.13/LICENSE.txt).
Java-WebSocket's 1.6.0 POM specifies SLF4J API 2.0.13. No logging provider is included;
socket errors are surfaced through client callbacks, and transcript/frame payloads are
not sent to logs. M2 preview also uses the server implementation for an authenticated
loopback device-tool boundary; see [adapter dependencies](../../integrations/device-tools/README.md).

The inspected [WebSocketClient source](https://github.com/TooTallNate/Java-WebSocket/blob/v1.6.0/src/main/java/org/java_websocket/client/WebSocketClient.java)
defines the async open/message/close/error callbacks and connection lifecycle. Client
connections use fixed phone-loopback URIs, no proxy, and an 8 MiB frame/HTTP-response limit.
TLS/remote-server integration is not part of this revision.

## Adapted sprite contract data

`PetSprite.java` adapts activity-row durations and atlas geometry from
[ysyyhhh/dsh-pet PetContract.ts](https://github.com/ysyyhhh/dsh-pet/blob/a17346b9c41efb4220f387216008928bba652e0e/src/renderer/codex-pet/PetContract.ts),
revision `a17346b9c41efb4220f387216008928bba652e0e`, MIT, copyright 2026 sereinmono and
dsh-pet contributors. The complete upstream license ships in THIRD_PARTY_NOTICES.txt.
Local changes: Java data representation, semantic-state mapping, blank-row fallback,
Android Canvas rendering and attention pause/resume. No upstream loader
implementation, native decoder dependency or additional runtime library is imported.

## Bundled Silver Wolf artwork

Default appearance: Silver Wolf, credited to legeling by the upstream submission.
Source: https://github.com/legeling/awesome-codex-pet/tree/db2f6306ce78f6559ab27155d7bfaf00bf43809d/pets/silver-wolf--lingxiaotian
Revision: db2f6306ce78f6559ab27155d7bfaf00bf43809d.
Artwork license: CC BY-NC 4.0, as declared in submission.json.
License: https://creativecommons.org/licenses/by-nc/4.0/
This is Honkai: Star Rail / Silver Wolf inspired fan art, not official character artwork.
The repository's MIT code license is not used as the artwork license.
Local changes: none to artwork or upstream metadata; runtime maps activity rows to
client states. No voice assets are included.
prepare-default-pet.sh pins all three files by SHA-256, downloads them outside Git,
and verifies them before packaging. Original pet.json and submission.json ship in
APK assets alongside this notice. The sheet is lossless WebP, 1536x1872 (v1's nine
activity rows), rendered by the existing activity renderer. External ZIP import
remains the documented v2-only subset; this bundled v1 asset does not broaden it.
The empty appearance key and builtin-v1 profile reference remain compatible, now
resolving to Silver Wolf. Imported selections and voice settings are preserved.

## Android character interaction adaptation

HabitatView adapts OverlayService.java's startPetIdle, petTapBounce and
petLongPressPulse from guzhou079-arch/deepseek-harness-android revision
`a5af7b105b7fdb006e3fc3be2a2179d83907c2cb` (MIT, copyright 2026 zhou-gu24).
The full license is included in the APK notices. Local changes replace separate
View property animations with Canvas transforms, restrict touch to character bounds,
route long press to the existing voice control, and honor reduced-motion/attention
state. No new engine, audio owner or dependency is introduced.

ReplyCard.java additionally adapts OverlayService collapseCard/expandCard,
MaxHeightScrollView, isCardAtBottom/renderCardText/updateCard at the same pinned
revision. Local changes: Activity-owned layout instead of WindowManager, full text
preservation, stable message identity, attention pinning and native event updates.
The existing upstream MIT notice covers these extracted routines.

## M4.5 state and presentation adaptation

HabitatState adapts whale-shota/lib/client.js completion hold (2600 ms), adding
initial-history suppression and native event/attention precedence. PresentationSettings
uses the bounded loadPetConfig subset; preset cycling follows nextPetLine.
HabitatView additionally adapts sparse idle sway and visual rest/wake (90 seconds)
from OverlayService. All use revision a5af7b105b7fdb006e3fc3be2a2179d83907c2cb,
MIT, copyright 2026 zhou-gu24. Visible-time Canvas integration, standalone settings
storage and same-session source labeling are local changes. No backend plugin is installed.

## Completion notifications and history

NotificationArchive.java directly adapts the reference class at a5af7b105b7fdb006e3fc3be2a2179d83907c2cb
(MIT, 2026 zhou-gu24), retaining private bounded storage and adding original-session
references and explicit read/prune errors. CompletionService reuses the turn-notify
minimum-duration/cooldown/foreground suppression policy and foreground notification
setup, replacing backend hooks with a read-only rc.7 session observer. No new dependency.

ShareInbox.java directly adapts the reference class at
a5af7b105b7fdb006e3fc3be2a2179d83907c2cb (MIT, copyright 2026 zhou-gu24).
Local changes remove external-storage copies, use durable private files instead
of cache, and connect intake to native review and explicit draft controls.
URI copies retain upstream 16 MiB/file, 32 MiB/batch, 16-file and 8-batch limits.
No automatic prompt submission or backend attachment transfer is included yet.

ScheduledTaskStore.java directly adapts the pinned Android reference at
a5af7b105b7fdb006e3fc3be2a2179d83907c2cb (MIT, 2026 zhou-gu24).
ScheduleAlarms adapts unique PendingIntent identity and cancellation. Local changes:
existing-session metadata, pause/resume, retained consumed one-shot records, bounded
input, explicit Android 10 exact-alarm failures and no legacy alarm migration.
Receiver/service ownership follows the reference scheduling subsystem, but the
executor uses only the inspected rc.7 session.list/session.prompt API. Bundled-engine
startup, private paths, root/Shizuku and credential discovery are excluded.

CompanionOverlayService adapts OverlayService window flags, foreground visibility,
drag tilt, snapToEdge/applyDockPos and notification recall policies at
a5af7b105b7fdb006e3fc3be2a2179d83907c2cb (MIT, 2026 zhou-gu24).
Local changes use the current Codex v2 renderer, existing rc.7 session observer,
full-window bounds and hidden/locked/screen-off cleanup. The overlay has no manual
voice controls; notification actions handle recall, hide and disable. Automatic
wake remains pending. Source license notice above applies.

ShareInbox transfer extensions retain the same upstream bounded chunk reader.
Local additions: explicit session-bound release, SHA-256 receipts, 32 KiB chunks
and a reserved submission identity. Termux receive-share.mjs reuses this project's
authenticated artifact-transfer pattern; no additional dependency is introduced.

## Automatic wake (approved 2026-10-25)

- Source: https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8 (Apache-2.0).
- Java binding: `sherpa-onnx-jvm-1.13.8.jar`, SHA-256
  `77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b`.
- Android native source archive: `sherpa-onnx-1.13.8.aar`, SHA-256
  `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`.
  Only arm64-v8a JNI and ONNX Runtime libraries are packaged for the target phone.
  The upstream Java binding is used with explicit Android library loading, avoiding
  the desktop extraction loader and an additional Kotlin runtime.
- Model: official `kws-models` release,
  `sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2`, SHA-256
  `b2f7c89690dc8ce4c6ed6afeab7cd800c36ad1421fb6b6302b4a4b194cf7f35f`.
  Model README declares Apache License 2.0 independently of engine source licensing.
  Epoch-12 int8 encoder/joiner and fp32 decoder are unmodified. Model README and
  per-file hashes are generated into ignored build assets by `prepare-wake.sh`.
- ONNX Runtime 1.28.2 (MIT), matching the pinned upstream Android aarch64 CMake recipe.
  License files are included in APK assets. No downloaded binary/model is tracked.
- Local code owns Android microphone/lifecycle and converts the current Chinese
  display name using platform ICU to validated partial-pinyin keyword tokens. Native
  decoding and model execution remain upstream. No network model or key is bundled.

## Owner speaker verification and speech segmentation

The user's explicit owner-only wake requirement reuses sherpa-onnx 1.13.8 Java/JNI
rather than adding another inference runtime. `OwnerSpeaker` adapts the pinned
[SpeakerIdentification.java](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/java-api-examples/SpeakerIdentification.java)
example (Apache-2.0, Copyright 2024 Xiaomi Corporation); local changes add VAD, single
owner storage, mandatory gating and resource lifecycle. Models are unmodified:

- `3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx`, 28,281,138 bytes, SHA-256
  `f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11`.
  Download: official sherpa-onnx `speaker-recongition-models` release. Original
  [iic model card](https://modelscope.cn/models/iic/speech_campplus_sv_zh-cn_16k-common)
  declares Apache License 2.0, verified through its README API response. Notices are
  in `SPEAKER_MODEL_NOTICE.txt` and the existing Apache license asset.
- Silero VAD v5.1, `src/silero_vad/data/silero_vad.onnx`, 2,327,524 bytes, SHA-256
  `2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f`.
  Source: https://github.com/snakers4/silero-vad/tree/v5.1 (MIT).
  Its original license is included in `SILERO_LICENSE.txt`.
- The model author's three public v1.0.0 example WAVs are downloaded only for the
  separate instrumentation APK by `prepare-owner-fixtures.sh`, with per-file hashes.
  The 0.4.18 experiment also includes these public WAVs in ignored APK build assets
  as a small background cohort; private user recordings never enter APK/Git. Tests do not register those speakers
  as the device owner.

## Experimental owner classification backends (user approved)

- LIBLINEAR Java 2.44: https://github.com/bwaldvogel/liblinear-java/tree/v2.44 .
  Maven `de.bwaldvogel:liblinear:2.44`, BSD-3-Clause; unmodified JAR SHA-256
  `b962746d8be60894ca8adbbe2fe556e40471e6e1d59941285901e0ceaa00eb3f`.
  Source JAR SHA-256 `c427b32aa601b50ac6567392619cd6164ec74983af2668f842481a62a0cb9912`.
- LIBSVM Java 3.35: https://github.com/cjlin1/libsvm/tree/v335 .
  Maven `tw.edu.ntu.csie:libsvm:3.35`, BSD-3-Clause; unmodified JAR SHA-256
  `66793be8d30bc4a51396bd323ad9e59e37ed19a80dfa0b23eaf71dcaed398df0`.
  Source JAR SHA-256 `c2d06bdcff4d87d8b14e9b08186b9f9dc8bcbed3a715333d4f8ed9272a78f695`.

Original licenses are included as `LIBLINEAR_LICENSE.txt` and `LIBSVM_LICENSE.txt`.
`prepare-owner-backends.sh` downloads hash-checked JARs independently of the APK.
No numerical solver was reimplemented. Local code normalizes CAM++ embeddings,
balances class weights, calls the upstream solvers and reports separate test results.
The public model-author example WAVs retain the Apache-2.0 model repository notice;
only two public speakers are available, which is not a representative cohort.

## M4.5 source scheduling and viewport adaptation

Reference remains `a5af7b105b7fdb006e3fc3be2a2179d83907c2cb`, MIT.
Reviewed `OverlayService.nextBubbleText` and its repeated weighted-kind sequence,
plus the already extracted window/card policies. The client adapts selection to
eligible queued source types with smooth weighted service, preserving source FIFO,
server sequence deduplication and pinned reading. No balance/account fetchers or
bundled engine routines are imported. This is a local adaptation, not a claim that
upstream supplied the source/provenance schema.

Android 10 overlay geometry uses a stationary transparent, non-touchable one-pixel
full-height measurement window in the existing service. The moving panel's visible
insets describe only its own clipping and cannot reliably locate the whole IME.
Both windows share lifecycle cleanup; no permission, SDK or runtime dependency is
added. Platform flag semantics were checked against
[WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM).

## Lucide menu asset (0.4.26)

User-requested open-source menu icon, no runtime library dependency. Source:
https://github.com/lucide-icons/lucide/blob/0.468.0/icons/menu.svg
Original SVG SHA-256: `90a31b3ab1cf6c796b6024560324daf425233ae3c11f30818b182ce557bafa99`.
The three upstream lines are converted to Android VectorDrawable paths in
`res/drawable/ic_menu.xml`; stroke color follows the current theme. The pinned
upstream license is retained as `assets/LUCIDE_LICENSE.txt` (ISC, with Feather
MIT attribution). Existing upstream card follow-tail logic remains reused by the
conversation strip. Android native gesture detectors replace the single-click path;
no custom gesture recognizer or new runtime dependency is added.

The same pinned Lucide release also supplies icon-only Expand, microphone and stop controls.
- `mic.svg`: SHA-256 `2db62a8268f7a16f0bd0f062ccd21ec4a3733d19a9d88f5332798b4ce437b757`; https://github.com/lucide-icons/lucide/blob/0.468.0/icons/mic.svg
- `square.svg`: SHA-256 `bd979354f0ab184b95cecf03eedefe40c2dc65830ac6d7e60017b2b25a354acb`; https://github.com/lucide-icons/lucide/blob/0.468.0/icons/square.svg
- `expand.svg`: SHA-256 `e4d730b4d8f8ae69fd214669b724412509b6b8ac0aefb1f680900f53452b0112`; https://github.com/lucide-icons/lucide/blob/0.468.0/icons/expand.svg
- `arrow-up.svg`: SHA-256 `8c22c25874c80bb062bef985d207d8f1d4a4d7847237d0a8cf720a3235968518`; https://github.com/lucide-icons/lucide/blob/0.468.0/icons/arrow-up.svg
- `chevron-down.svg`: SHA-256 `66ea878e72ed3488bb3b464c39dfdccee8d1f78e560dccea40e5e12da0e87e87`; https://github.com/lucide-icons/lucide/blob/0.468.0/icons/chevron-down.svg
