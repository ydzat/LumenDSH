# M3 Foreground Voice Preview Evidence

Date: 2026-10-24. Device: Huawei YAL-L21, Android 10. Backend unchanged:
DSH 0.1.0-rc.7-termux.1. Source: working tree following 0825e71.

## Executed checks

- Explicit Wi-Fi ADB shell returned ADB_OK at the selected network endpoint.
- Android service query found GoogleRecognitionService; the secure default
  recognition-service setting selects that component.
- Phone-side pinned javac/aapt/D8/apksigner pipeline completed successfully.
- APK 0.3.0-m3-preview, versionCode 5, SHA-256:
  `1ef915d567ec82ce9b9674d79108191792841266a7736015fc10fa4fb75cdcb6`.
- APK v3 signature verified with the existing certificate SHA-256
  `4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.
- In-place install returned Success; cold Activity launch returned ok (394 ms).
- UI inspection showed the existing local DSH connection and visible voice controls.
- `git diff --check` passed before evidence documentation.

## Acceptance status

V01: PASS within the foreground, tap-per-request policy, based on the user's
target-phone confirmation. With auto-send enabled, the user was asked to speak
two consecutive requests: a one-sentence introduction followed by a question
about the previous request. The user reported success for recognition, submission,
and one audible reply per request in the same conversation. This is human-reported
acoustic evidence; no recording or independent event-count trace was captured.

V02-V04: NOT RUN. This result does not establish acoustic barge-in, wake words,
background/lock-screen operation, incoming-call handling, or offline recognition.

See [policy and limitations](../docs/voice.md). No DSH restart, new runtime dependency,
recognition-engine installation, recording capture, or backend configuration change
was performed for this preview.
