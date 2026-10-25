# Android Reference Reuse Audit

Date: 2026-10-24. Requested after the user identified insufficient upstream reuse.
Status: source inspection and implementation comparison, not reference-app device validation.

## Evidence baseline

Reference: [deepseek-harness-android at a5af7b1](https://github.com/guzhou079-arch/deepseek-harness-android/tree/a5af7b105b7fdb006e3fc3be2a2179d83907c2cb).
Local checkout: `.local/references/deepseek-harness-android` (ignored).
Our baseline: `89eb081`, installed 0.4.7. No backend plugin was installed or changed
for this audit. No upstream scripts were executed or reference APK installed.

Inspected repository inventory, README, LICENSE, THIRD_PARTY_NOTICES, relevant native
classes, plugin manifests/implementations/tests, and build/release script entry points.
This is a targeted functional audit, not a claim that every line or bundled skill
has been reviewed. Skill documents in the reference were read as project evidence,
not invoked as instructions for this workspace.

## What the earlier investigation missed

The earlier research concentrated on engine bundling, protocol mismatch and build
portability, then treated the Android app largely as a future overlay reference.
That was too narrow. It already contains present-day character UI behavior, content
configuration, reply presentation, lifecycle handling, plugins and release evidence
that should have been evaluated before implementing our own equivalents.

Adding breathing and touch transforms in 0.4.7 is only a small extraction. It does
not fulfill the broader reuse requirement or make the character UI feature-equivalent.
The native Canvas decision does not prevent reuse of Java routines, state policies,
configuration semantics or interaction tests. Conversely, existing verified code
should not be replaced solely to increase copied line count.

## Current habitat: highest-priority missed opportunities

All OverlayService references below are under
`android-app/src/com/deepseek/harness/OverlayService.java` at the pinned revision.

| Reference evidence | Our current implementation / gap | Reuse disposition |
| --- | --- | --- |
| `loadPetConfig` (628-740): name/tag, appearance sizing, bubble style, lines, weighted bubble kinds, card behavior | Native header is fixed to Silver Wolf; appearance selection does not provide equivalent presentation settings; many style/timing choices are constants | Extract a bounded presentation configuration subset. Keep appearance, voice and behavior separate; do not adopt its provider-key configuration |
| `onBallTap`, `nextPetLine`, `nextBubbleText` (769-824) | 0.4.7 tap only rebounds; no configured phrase/status content | Adapt selection logic into an explicit preset-content source. Label it as configured expression, never model output, memory or private reasoning; feed our attention queue |
| `showBubble`, `followBubble`, `clampBubble` (1664-1793) | Our bubble is placed at the lower-right of the stage, not anchored to character geometry | Extract character-relative placement and boundary clamping; add our face/decision/trace occupancy constraints instead of importing overlay WindowManager offsets |
| `collapseCard`, `expandCard` (1271-1308) | Current reply excerpt can be pinned/dismissed but has no compact-to-expanded card mode | Adapt the two-state card and transition mechanics inside the habitat; keep background work text as unboxed typewriter text |
| `MaxHeightScrollView`, `isCardAtBottom`, `renderCardText`, `updateCard` (1310-1361, 1790 onward) | Existing bubble has bounded excerpt text; there is no independently scrollable long reply with follow-tail policy | High-value Java extraction: capped height and follow-tail only when the user is already at the bottom and not touching. Preserve reader position on updates |
| `maybeStreamCard`, `scheduleStreamPoll` (1444-1511) | Habitat foreground primarily presents completed replies and active TTS text | Reuse presentation transitions and cursor policy; drive them from our actual rc.7 event adapter, not its private-session-file reader/polling |
| `startPetIdle`, `petIdleRunnable`, sleep/wake/tap/drag routines (3839-4035) | Breath/tap/long-press partially adapted; sparse idle gestures, sleep/wake and drag tilt absent | Continue selective extraction. Idle/sleep are visual states only; never imply the backend stopped. Respect attention/reduced motion and avoid stacking frantic motions |
| `addNativePressFeedback`, `pillButton`, `paintToggle`, `capCard` (2916-3063) | Current native controls have basic ripple feedback; scene does not have equivalent contextual capability cards | Reuse press feedback and explicit toggle styling where useful. Capability cards are optional, not a reason to turn the primary habitat back into a dashboard |
| `relayoutForCurrentScreen`, `applyReplyWidth`, `clampPanelOnScreen`, input-focus handling | We independently implemented adaptive near/far and landscape layouts | Retain verified layout work; compare upstream keyboard, measured width and screen-edge cases before extending it. Do not copy device-specific offset constants |

The reference's animated character is primarily a static-image/View-transform pet;
it is not a drop-in Codex sprite renderer. Our pinned atlas importer/renderer remains
necessary. Its UI still offers substantial reusable behavior around that renderer.

## Missed plugin and companion layers

| Inspected source | Findings and comparison | Next action |
| --- | --- | --- |
| `plugins/whale-shota/lib/client.js` and `tests/state-machine.test.mjs` | Five-state presentation with error precedence, thinking/streaming distinction, 2600 ms completion hold, collapse persistence, reduced motion and observer/timer disposal. Our scene mostly maps running to working, then returns to idle; `thinking` exists in PetSprite but is not selected by HabitatView | Port applicable state transitions and corresponding cases to native session events. DOM signals are not backend guarantees; preserve disconnected/unknown separately from task error |
| `plugins/whale-shota/skin.json`, package.json, README and NOTICE | Web platform; package peer range starts at 0.1.7-rc.1 while our backend is rc.7-termux.1. README documents missing art and a separate archived 2.5D experiment; NOTICE contains historical SVG wording inconsistent with current image-crop code | Reuse code/state ideas with attribution; keep the selected Silver Wolf asset. Do not promise one-command compatible installation or treat archived layers as shipped assets |
| `plugins/dsh-plugin-turn-notify/lib/index-v3.js` | Automatic completion notification, final text accumulation, short-turn suppression, cooldown and foreground suppression; nonblocking turn-stopping listener. Our MCP notification operation is explicit and does not implement this policy | Prefer adapting this existing plugin after installed event compatibility discovery; replace bridge addresses/auth and foreground detection. Do not mistake explicit notification tools for automatic completion notification |
| `plugins/dsh-plugin-auto-summary-policy/lib/index.js` | Configures existing BasicCompactionEngine instances and restores config on disposal; depends on cordis, agentPresets and compaction service identities | Candidate for memory/context work only after rc.7 compatibility inspection. Compaction policy is not autobiographical companion memory |
| `skills/dsh-life/scripts/life.js` | Actual Node/SQLite notification parsing, local task/cron scheduling and daemon code, with fixed private/shared paths and bridge ports; watchdog comments identify possible paid agent-session invocation | Reuse relevant scheduling/local-task routines after separating paths, bridge access and same-session ownership. Do not deploy its broad notification/clipboard/daemon behavior as an incidental UI change |
| `skills/dsh-memory-sync/SKILL.md` | Describes mem.db and commands targeting `/sdcard/DeepSeekHarness/tools/mem.js`; no mem.js found in this checkout | Useful intended schema/workflow, not a complete reusable memory implementation. Keep other memory plugin candidates in the companion track |
| `plugins/dsh-plugin-session-rewind/lib/core.js` and README | Pure human-message classification and prefix fork-boundary logic; explicitly changes session ID and does not restore files | Optional future user-requested rewind feature, not M5 release rollback or a replacement for shared-session continuity |
| `plugins/dsh-android-ui/lib/client.js`, `plugins/dsh-android-console/lib/index.js` | Web style injection with lifecycle cleanup; console routes to local tools and auto-starts a life daemon | Reference for theme cleanup and capability separation; CSS selectors/WebView bridge are not native UI components, and console installation has backend side effects |

## Native capabilities and release work

| Inspected source | Reuse value | Boundary / decision |
| --- | --- | --- |
| `VoiceManager.java` | Recognizer/TTS state callbacks, draft ownership, interaction tones and lifecycle handling | Our VoiceInput/DeviceSpeech duplicate some platform plumbing. Compare callback cases before extending them; keep our cancellation and audio ownership. `submitVoiceCommand` creates a new session for every command, so wholesale replacement would regress continuity |
| `ShareInbox.java` | Bounded URI intake, staged batches, acknowledgement and pending order | Prefer extraction when adding Android share-to-agent; our ArtifactInbox is authenticated candidate transfer, a different direction and responsibility |
| `NotificationArchive.java` | Small private persisted notification history with bounded retention | Reuse when notification history is scoped; it is not an existing equivalent of our operation records |
| `ScheduleAlarms.java`, store/receiver/executor family | Alarm identity, cancellation, persistence and receiver execution | Extract as a coherent subsystem, not a lone utility; replace execution policy to preserve our agent/session and Android 10 grants |
| `BackupValidator.java`, `BackupRestore.java` | ZIP preflight and restoring overwritten files on ordinary errors | Compare validators rather than rewrite. BackupRestore explicitly lacks crash-consistent journaling; it is not sufficient for U03 recovery after process death |
| `selfbuild/scripts/release-receipts.mjs` | Content hashes linking compile inputs, DEX, APK and checks; atomic receipt write/readback | Strong M5/M6 extraction candidate. Refactor fixed v118/PRoot paths and split pure receipt helpers from packaging dependencies |
| `verify-apk-payload.js`, release checks/publish scripts | APK content checks and release identity inspection | Reuse local checks selectively. Do not run publisher/network release scripts: our project is local-only, and scripts have pinned baseline/repository/private-path assumptions |
| `EngineRpc.java`, EngineService and engine-session scanning in OverlayService | Useful protocol/lifecycle comparison | Keep our validated rc.7 API adapter and independent Termux service. Do not copy token-log extraction, own-engine process control or same-UID session scanning |
| Accessibility/vscreen/Shizuku components | Potential future Android automation infrastructure | Outside current UI work. Vscreen has separate LGPL-3.0 coverage; do not introduce it as a dependency of character rendering |

## Assessment of our existing work

- **Avoidable independent UI work / missed extraction:** bubble/card mechanics,
  native interaction feedback, presentation configuration and character state policy.
  These should have had source comparison before coding. Some were implemented in
  a narrower form, leaving useful upstream behavior out.
- **Valid project-specific implementation:** rc.7 session adapter, MCP authenticated
  boundary, independent Termux build, Codex atlas import and attention-aware habitat
  layering. The reference's different boundaries do not eliminate their need.
- **Partial reuse already done:** 0.4.7 breathing/touch adaptation and earlier
  dsh-pet atlas contract reuse. Neither justifies claiming a comprehensive reuse pass.
- **Untapped future reuse:** turn completion notification, local scheduling,
  attachment intake and content receipts. Their source should become the starting
  point when these features enter scope, not merely a link in research notes.

## Concrete order for the next implementation batch

1. Extract reply-card expand/collapse, height cap and reader-preserving follow-tail;
   anchor character speech to available geometry while preserving background traces.
2. Add a source-aware presentation configuration subset and preset phrases; preserve
   independent voice/persona settings and label non-generated content honestly.
3. Adapt the five-state transition policy to real session events, including bounded
   completion feedback and precedence tests; extend idle gestures only within attention rules.
4. Consolidate UI01/P01 device validation and the milestone demo for that batch.
5. For subsequent backend/release work, begin with the notification/scheduling/receipt
   modules above and verify rc.7 capabilities before enabling anything in Termux.

This is an implementation shortlist, not approval to expand scope to all bundled
skills, finance, clipboard monitoring, virtual displays, public releases or engine
migration. No new acceptance cases were marked passed by this audit.

For each future nontrivial component, record the upstream file/symbol and pin,
what is extracted, required local adaptations, license, and focused verification.
If reuse is rejected, state the concrete incompatibility instead of simply choosing
another handwritten implementation. Maintain accepted design and existing user data.

## Superseding milestone assignment

The user subsequently required a full M0-M4 completion gate, rather than only the
three-item UI batch above. [M4.5](m4.5-completion.md) now assigns every audit area:
C01-C13/F01-F14 are mandatory before M5; release primitives belong to M5/M6 and
backend companion integration to M7. Use that disposition for implementation scope.


The 0.4.24 [closure batch](../tests/m45-closure-evidence.md) records the final
source-kind/weight adaptation, viewport measurement and source-specific card captions.
`nextBubbleText` supplies the upstream weighted-kind policy reference; the local
scheduler uses only eligible, referenced content while preserving attention ownership.
Account/balance producers remain excluded. See apps/android/DEPENDENCIES.md for
extraction/adaptation boundaries. Implementation does not upgrade open acceptance.
