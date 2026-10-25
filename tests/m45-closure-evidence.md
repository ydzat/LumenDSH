# M4.5 source and viewport closure batch

Build: 0.4.24-m45-preview (code 31), Huawei YAL-L21 / Android 10.
APK SHA-256: `8c66ddb1ef427333dde1d2c04d962e2d7ad2992c62276dbfe08940cd31487b4e`.
Signing identity unchanged. DSH remains independent and was not restarted or upgraded.
No new production dependency, permission, ASR engine or personal recording was added.

## Implemented

- C04/C06: explicit source kinds/references, bounded eligible-source weighted selection,
  per-source FIFO, existing server-sequence reconciliation and pinned reading. Source
  captions remain visible in expanded cards. Optional settings migrate existing profiles;
  the advanced form and JSON import/export/history share the same validation.
- Actual replies and presets are connected; task traces remain in the background and
  explicit task details use the queue. Archived notifications can return to their original
  conversation with a labeled excerpt. Memory is a reserved source without a producer.
- C11: stable IME/cutout viewport measurement, keyboard avoidance/restoration and shared
  cleanup for the panel and transparent untouchable measurement strip. Both remain in
  the existing service. The strip never receives input or records screen content.
- C12: source-specific card captions, measured compact-layout admission, a 160 dp
  minimum side region, native connection wording and foreground voice-pause reset.

## Executed checks

- Host Java/API 29 compilation passed with UTF-8 source encoding.
- Termux check-conversation.sh passed conversation (10), habitat/settings/source queue,
  notification archive, schedule store (12) and voice correlation (5) checks. Added
  cases establish migration defaults, unknown-kind rejection, weighted source choice,
  source/reference update, pinned/occupied attention and dismissed-reply reconciliation.
  These are actual code execution with synthetic inputs, not live backend acceptance.
- Termux check-pets.sh passed real Android bitmap/ZIP/atomic-profile checks, rejected
  malformed/traversal/executable packs while retaining the active pointer, and passed
  profile/attention regression including growing text replacement and bounded backlog.
- Independent phone build and APK signature verification passed; installed in place.
- The actual content-weight form rendered migrated defaults 4/1/2/2/1. It was cancelled
  without changing the user's presentation configuration.
- Real keyboard testing first exposed self-dependent insets on a small moving window.
  The final stationary measurement strip keeps the bottom-docked character completely
  above the Huawei IME; dismissing the keyboard restores its resting position. Inspected
  `artifacts/m4/m45-ime-final.png`. This is target-device evidence, not all IMEs/devices.
- Sent one controlled, tool-free layout request in the existing conversation. DSH
  returned twenty numbered lines, and the actual reply survived client cold launch.
  No new session or automatic resend was used.

- Both compositions were inspected with the actual long reply in portrait, 1.3 font
  scale and landscape. The final side card stays clear of the character face and
  controls; actual swipe scrolling changed the visible lines while reading remained
  pinned. The expanded card uses the available side height instead of portrait's
  35 percent limit. Screens: `m45-final-long-far.png`, `m45-final-large-font-near.png`,
  `m45-final-landscape-readable.png`, `m45-final-landscape-scrolled.png` under artifacts/m4.
- Installed geometry instrumentation observed the real keyboard viewport
  `[0,108][1080,1437]` and panel `(732,945,336,480)`: panel bottom 1425, leaving
  twelve pixels above the IME. The assertion passed. Original overlay coordinates
  636/756 and original system font/rotation settings were restored after testing.

- F07 scoped live UI check passed: explicit details from the actual current task
  opened as `task`, stayed pinned when a character tap queued a preset, and advanced
  to the visibly labeled preset only after explicit dismissal. No model call occurred.
- Recorded and locally decoded a 10.017-second silent device demonstration:
  `artifacts/demos/20261025-023425-m45-source-aware-habitat-47a5d454.mp4`.
  The clip shows ambient motion, preset source labeling and near/far composition.
  The controlled screen was inspected before recording; decoded frames were inspected
  in an isolated local Edge profile. No personal messages, audio or credentials appear.
  It demonstrates this completed increment, not the remaining acoustic/fault gates.

## Gate status

This batch fills the previously identified source-kind/weight and viewport implementation
holes. It does **not** declare the whole M4.5 gate passed. F02/F04 live interruption and
streaming matrices, F06 duration/lifecycle, F08-F10 fault/lifetime matrices, F11/V04 audio
conflicts, V03/V05 human acoustic statistics and F13 user visual review retain their open
scopes. Existing evidence files remain valid only for their recorded checks. M5 remains
gated until those scopes are accepted or explicitly revised by the user.

The user already confirmed successful but unstable owner wake. Wake-only high-confidence
learning and response grace remain implemented but real sample promotion/provider timing
are not proved by these UI checks. No enrollment was repeated or voiceprint exported.
