# M4 baseline stage acceptance

Date: 2026-10-24. Target: Huawei YAL-L21, Android 10, explicit USB transport.
Installed client: `0.4.3-m4-preview`, code 10. The independent phone-local DSH service
was not restarted, upgraded or replaced. All prompts used the existing controlled
acceptance conversation. No project files or artifacts were published.

## Implemented baseline

- Native character habitat with persistent near/far composition, state colors,
  local date/time and activity heading.
- Public tool-call/result excerpts in the background, with bounded wrapping,
  typewriter progression and visible-time fade. Foreground speech/reading queues
  preserve the previous increment's attention policy.
- One pinned Codex-compatible v2 importer, animated rendering, independent local
  appearance/voice compositions and resource management.
- Secondary transcript hierarchy, inline composer, scrollable approval details,
  native question forms, and compact landscape layout.

The accepted Review 05 design remains the reference. The built-in character and
diagnostic robot are original placeholders, not a finished third-party character pack.

## Consolidated checks

Host javac compiled all production sources against pinned API 29 and dependency jars.
On the phone, `check-conversation.sh` and `check-pets.sh` passed before packaging:

- Ten existing conversation assertions, including sequence reconciliation and
  non-user/replacement context handling.
- Habitat event projection: public result extraction, reasoning-block exclusion,
  activity settlement, bounded history, new-turn reset and failure preservation.
  These use synthetic parser inputs and are not substitutes for the live checks below.
- Actual Android BitmapFactory/Canvas/ZIP/AtomicFile appearance regressions.
- Profile integrity/activation and attention queue checks, plus rejection of active
  profile/appearance removal, saved-reference protection, successful unreferenced
  removal, and explicit reimport/save after deletion.

## Actual phone observations

1. Installed in place and recovered the selected conversation. Native resource
   management listed the two saved profiles and the imported original robot.
2. Deleting active Baseline was refused. Deleting Circuit Companion while referenced
   by saved Circuit Slow was refused. Created/activated a disposable profile, switched
   back to Baseline, deleted the disposable version and checked the refreshed list.
3. Exercised portrait at font scale 1.3, landscape at 1.3, and portrait with the real
   Huawei keyboard. The first landscape pass exposed a zero-height transcript and
   nearly absent avatar. Fixed the layout, rebuilt, and visually rechecked both:
   the transcript now has a readable scrolling area; character and voice controls
   sit side by side. Rotating the transcript retains that page. Input and Send remain
   visible with the keyboard open. This is not coverage of every font/IME/device.
4. Requested a genuine approval for only `printf M4_APPROVAL_OK`. Installed the layout
   correction while this decision was pending. The actual approval survived, showed
   command/reason/arguments and all three actions. Rejected it; the agent returned
   `M4_REJECTED` without retrying the command.
5. Requested the same harmless command again, inspected its scope and allowed it
   once. Its real tool result was `M4_APPROVAL_OK`. The agent then invoked
   `ask_user_question`; selected Continue and submitted the native answer form.
   The completed response was `M4_GATE_DONE`, with no pending decision left.
6. The real tool-result text and question-result record appeared behind the character
   in the habitat. Switching near/far compositions retained the scene. Reply bubbles
   are temporary: completion was checked in full history, not inferred from whether
   its bubble was still visible. Captured and inspected actual background text.
7. Restored system font scale 1.0, accelerometer rotation 0 and user rotation 0,
   exactly matching the saved settings. Restored Baseline, zh-CN, rate/pitch 1 and
   space composition. Removed the exact-input test APK. DSH remained running.

## Build and video

Final APK SHA-256:
`cb6d234b52bfe0db189ac433aa1bb9ce427aa1ee743a4148d9f2c6cb9cec4cc5`.
Phone output: `artifacts-build-qZZv8xHG/sliver-wolf.apk`.
APK v3 signature verified with the unchanged developer certificate SHA-256
`4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.

Silent actual-device demo:
`artifacts/demos/20261024-231824-m4-space-stage-0cc0ed70.mp4`.
Decoded duration 10 seconds, 540x1168. Inspected a decoded frame, keyboard screenshot,
corrected portrait/landscape screenshots and actual public tool trace. This video is
UI evidence only, not acoustic evidence. Private artifacts remain excluded from Git.

## Gate result and limits

User review after this run found missing ambient animation and deviations from
Review 05. The M4 visual gate is therefore reopened; these functional observations
remain valid but do not establish design fidelity. P01/P02 retain their [pinned-format scope](m4-pet-evidence.md); P03 retains
its [current-engine scope](m4-profile-evidence.md). UI01 passes the exercised layouts, composer, approval/question and character-control
separation, but is PARTIAL overall until the approved visual and motion design is
implemented and compared with actual phone output.

Not delivered by this gate: additional sprite/video/3D formats, gaze/lip sync, custom
Silver Wolf artwork or voice cloning, external combined-package import, provider
switching, general memory/diary/proactive-expression sources, provider outage diagnosis,
or backend companion plugins. Retrieval coloration is a tool-name heuristic; it does
not prove a search succeeded. M3 wake/background checks remain explicitly deferred.
M5 release staging, agent-published patches and recovery are the next implementation
milestone; manual profile activation does not pass U01-U04.
