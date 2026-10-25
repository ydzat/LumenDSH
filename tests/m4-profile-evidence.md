# M4 local profiles and attention queue evidence

Date: 2026-10-24. Device: Huawei YAL-L21, Android 10; explicit USB ADB transport.
DSH remained independently running in Termux. No backend restart, upgrade or model
credential inspection was performed. The client remains `local.sliverwolf.assistant`.

## Implemented scope

Version `0.4.2-m4-preview`, code 9, adds the local combined profile editor/selector,
immutable appearance/voice references, transactional activation, restoration, and
the bounded sequence-aware reply presentation queue. See
[profile design](../docs/design/m4-profiles.md) and
[attention policy](../docs/design/m4-attention.md).

## Executed checks

- Host javac against the pinned API 29 jar and existing dependency jars passed.
- `tooling/android-build/check-pets.sh` ran production classes on actual Android
  through D8/app_process. Existing BitmapFactory/Canvas/ZIP/pointer regressions passed.
- `ProfileChecks` passed actual Android JSON and AtomicFile round trips, exact
  appearance/voice references, idempotent immutable save, mismatching appearance and
  profile hash rejection, voice digest/range rejection, changed-file rejection and
  old-pointer retention. These are storage/logic checks, not simulated TTS evidence.
- Queue logic checks passed reading retention, blocked-time exclusion, 12-second
  visible lifetime, sequence deduplication, distinct messages with identical text,
  bounded overflow, overflow acknowledgement and session reset.
- Native UI created `Baseline` (built-in, zh-CN, rate/pitch 1) and `Circuit Slow`
  (the original CC0 robot fixture, zh-CN, rate 0.75, pitch 1). Activation visibly
  changed the character; the authenticated actual device status reported rate 0.75.
- The preview produced an actual speaking state and the foreground bubble showed
  its own Chinese/English preview text, rather than a prior conversation reply.
  Acoustic quality and perceived speed were not human-rated in this increment.
- During actual long TTS playback, attempting to activate Baseline showed
  `Finish current voice interaction before switching profiles`. The old robot and
  rate 0.75 remained active. Explicit stop cleared the active speech operation.
- Force-stopped/relaunched only the client. The robot and rate 0.75 restored.
- After the final APK update, compared the visible session identifier with the
  privately saved pre-increment identifier; it matched and the new controlled reply
  remained in history. Final status restored Baseline's rate/pitch 1 and zh-CN.
- In the existing controlled DSH session, pinned `HABITAT_DECISION_OK`, submitted
  a text request for `ATTENTION_QUEUE_READY`, then returned to the habitat. The old
  reply remained in reading mode and `待查看 1 条` appeared. Explicitly advancing
  displayed the real new reply; subsequent visible idle time removed the bubble.
- Restored the built-in Baseline, zh-CN, rate/pitch 1 and space composition. Kept
  both local profiles available for inspection; removed the exact-input test APK.

## Acceptance and remaining scope

P03: PASS for local installed-appearance/current-engine voice compositions, tested
activation failure and client restart. Full external combined-package import and
behavior profiles are not implemented. Missing/replaced TTS providers, unavailable
language packs, power loss during pointer commit and changed-language recognition
were not exercised on the phone. Voice checks use the existing shared engine; they
do not change the previously deferred wake/background acceptance status.

UI01 was PARTIAL at 0.4.2; the subsequent [stage record](m4-stage-evidence.md)
contains the current result. The actual reading/new-message queue, current speech text and profile
forms were exercised. General memory/notification sources, complete long-history and
all decision/keyboard layout combinations remain outside this increment. Unit queue
checks do not prove every real lifecycle path. M4 is not declared complete.
U01 is NOT RUN: manually saving a profile is not an agent-published patch.

## Build and demonstration

Main UI checks and video used APK SHA-256
`683dded995186846ad620b31bfc2143bec49fd3c4e3a8c14fdc2899f221d9f46`.
The final build adds strict nested-reference field checking and an actionable
overflow-to-transcript control; affected Android checks were rerun.
Final installed APK SHA-256:
`f0f59823bd9d40d7099c176c35f2e3059c1449241b95227186266d32413f1102`.
Phone build directory: `artifacts-build-8PQsRVvj`; APK v3 signing verified with the
unchanged developer certificate SHA-256
`4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.
Final in-place install and native launch succeeded. USB briefly went offline during
final transfer, recovered, and the full affected check/build command completed.

Silent real-device video:
`artifacts/demos/20261024-230110-m4-profile-attention-09644b34.mp4`.
Decoded duration 10 seconds, 540x1168. The controlled screen and decoded frame were
inspected. It shows the imported character, pending reply reveal and composition
interaction; it is not acoustic evidence. Video, screenshots, APK and private test
session identifiers stay in ignored local artifacts.
