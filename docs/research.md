# Upstream Research

Reviewed on 2026-10-24. Evidence below is source/document inspection, not a successful build or device acceptance result. Upstream branches can change; pin revisions before reuse.

## Android DSH app with a companion overlay

Repository: https://github.com/guzhou079-arch/deepseek-harness-android

Useful existing components include `OverlayService.java`, `VoiceManager.java`, and `EngineRpc.java` under `android-app/src/com/deepseek/harness/`.

Observed constraints:

- The app bundles and manages its own DSH runtime; this differs from our required client-only architecture.
- The inspected voice standby path repeatedly uses Android `SpeechRecognizer` and matches wake words in recognized text. It is not evidence of a dedicated offline, low-power keyword model.
- The inspected `submitVoiceCommand` implementation creates a session before each prompt. Conversation continuity needs redesign or verification.
- Protocol/authentication code assumes the app's own engine/log locations.
- `selfbuild/scripts/javac-app.sh` and `selfbuild/selfbuild.sh` contain fixed private-app paths. The Java build references a `v118/` source layout.
- `selfbuild/scripts/make-dist-apk.sh` includes a specific version/base-APK constraint. Documentation alone does not establish a reproducible clean build.
- Phone builds use PRoot/Alpine/OpenJDK, Android API stubs, D8, packaging helpers, and APK signing. These are reusable build components, not a generic self-modification SDK.

Licenses: primary project MIT; virtual-display components LGPL-3.0; other dependencies retain their licenses. Inspect `LICENSE` and `THIRD_PARTY_NOTICES.md` before importing.

## DSH desktop companion

Repository: https://github.com/PC2005-cloud/dsh-pet

Useful for character animation, state mapping, and pet-pack concepts. Native desktop presentation uses Electron; browser rendering does not supply Android background microphone or lock-screen lifecycle support.

## Fairy presentation for DSH

Repository: https://github.com/Chengzhibense/Fairy-DSH

Useful for Fairy-themed presentation and DSH state integration. `fairy-visual` is a Web client presentation module; it is not a native Android background assistant. Original code is Apache-2.0, with separate boundaries for third-party material and character assets.

## Embedded Fairy reference

Repository: https://github.com/Michae1o/fairy-stackchan

An ESP32-S3/M5Stack StackChan modification set built on the Xiaozhi ecosystem, with wake words, animated expressions, DeepSeek conversation, and server-side voice synthesis. Useful as an interaction reference; not an Android APK or a drop-in DSH frontend. It may or may not be the embedded project the user originally saw.

## Contract references selected for development

- [DSH Session Controller](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/api/session-controller/README.md) and [Gateway](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/api/gateway/README.md): preferred attach/control reference; current upstream differs from the installed phone release.
- [DSH SDK client](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/sdk/client/README.md): subprocess ownership/close semantics explain why it is not the default attachment model.
- [MCP tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools) and [transports](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports): device tool protocol; selected as design baseline, not proven compatible with the installed DSH transport.
- [Termux API](https://github.com/termux/termux-api): device capability implementation reference, not a complete persistent voice lifecycle.
- [ysyyhhh/dsh-pet](https://github.com/ysyyhhh/dsh-pet): Codex-compatible sprite-pack importer reference; exact format/revision still needs pinning.
- [TUF](https://theupdateframework.io/docs/overview/): update trust design; not an activation/rollback implementation.
- [Android PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) and [signing](https://developer.android.com/studio/publish/app-signing): in-place native upgrade reference.
- [Tinker](https://github.com/Tencent/tinker): later native hot-fix option, not selected; documented limitations include AndroidManifest changes.

These sources were inspected during the design discussion. No source code or third-party assets have been imported. Pin exact revisions, inspect licenses, and reproduce the relevant behavior in M0/M4 before depending on mutable branches.

## M0 candidate pins (2026-10-24)

Read-only upstream HEAD queries and pinned license inspection established:

- Android reference: [`a5af7b105b7fdb006e3fc3be2a2179d83907c2cb`](https://github.com/guzhou079-arch/deepseek-harness-android/tree/a5af7b105b7fdb006e3fc3be2a2179d83907c2cb), root MIT license.
- Appearance importer candidate: [`a17346b9c41efb4220f387216008928bba652e0e`](https://github.com/ysyyhhh/dsh-pet/tree/a17346b9c41efb4220f387216008928bba652e0e), root MIT license.

These are research pins, not vendored dependencies or validated asset licenses.
No code/assets were copied from either. Native Termux tooling successfully built
the original minimal APK, so adapting the Android reference's PRoot/base-APK scripts
is unnecessary for M0. No standard Gradle/AGP phone build was attempted; the simpler
route is selected based on actual build/install results rather than a claimed comparison
benchmark. Exact pet schema/asset license review remains M4.

## Local Android reference review and reuse plan

Source checkout: `.local/references/deepseek-harness-android` (ignored, reference only).
Inspected revision: `a5af7b105b7fdb006e3fc3be2a2179d83907c2cb`, matching the M0 pin.
Root license: MIT, copyright 2026 zhou-gu24. Copy its full notice when adapting code;
inspect component-specific notices before importing virtual-display components.

| Source under android-app/src/com/deepseek/harness | Reuse target | Integration boundary |
| --- | --- | --- |
| OverlayService.java: startPetIdle, petIdleRunnable, stopPetIdle | Calm breathing, spaced idle gestures and animation lifecycle | Reference now; Canvas sprite timing stays separate from view transforms |
| OverlayService.java: applyEdgePos, animateToEdge, touch handling, setPanelVisible | Future native overlay drag, edge docking, dismissal and recall | Extract these routines; replace engine probing and process scanning with our session events |
| OverlayService.java: card presentation and bubble timeout routines | Foreground speech cards and dismissal mechanics | Fit our attention queue and occupancy rules; do not copy the whole service |
| ShareInbox.java | Future Android share intake and bounded attachment staging | Adapt content URI copy and pending queue; replace shared-storage archive destination with our private authenticated handoff |
| ScheduleAlarms.java | Future local scheduling support | Reuse alarm identity/cancellation logic with its store/receiver dependencies; keep agent task execution in the existing conversation |
| VoiceManager.java | Voice lifecycle reference | Reuse suitable Android callback/audio handling only after comparison; retain our session continuity and do not claim recognizer polling is low-power wake detection |
| EngineRpc.java | Protocol comparison only | Its token extraction and authenticated newer engine assumptions do not match installed rc.7; keep our verified adapter |

The reference pet uses view transforms, not this Silver Wolf atlas. Source inspection
found a 2600 ms reversing breath (5200 ms round trip) and idle gesture scheduling
at 6500-12499 ms. These are source facts, not phone validation of the reference app.
Our chosen 0.4.6 presentation policy keeps upstream atlas timing data unchanged,
plays activity frames at half speed, and holds frame zero for four seconds between
idle cycles (2.2 seconds of animation plus four seconds of rest). This is a local
UX choice informed by the reference, not copied upstream sprite timing or code.

Before implementing each listed feature, start from these concrete routines and
record any copied revision/license/local changes. Existing bundling differences do
not justify reimplementing reusable Android UI mechanics from scratch. Scheduling,
share intake and system overlays are follow-ups, not capabilities added by this review.

0.4.7 reuse update: HabitatView now directly adapts the current character UI's
breathing and tap/long-press feedback from OverlayService; see
[design and scope](design/m4-attention.md#character-interaction-reuse-047).
This supersedes the earlier reference-only classification of those routines.

## Comprehensive reuse audit

The [2026-10-24 audit](reuse-audit.md) supersedes the earlier narrow reuse shortlist.
It covers current character UI, plugins, companion behavior and release tooling,
distinguishes inspected source from runtime verification, and sets extraction priorities.
