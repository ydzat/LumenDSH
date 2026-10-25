# M4.5 presentation configuration and overlay follow-up

Final installed version: 0.4.23-m45-preview (code 30), Huawei YAL-L21 / Android 10.
Termux build APK SHA-256:
`2bff8abb0e6b33cb2434567b046c6837ee9ca4ddf6c42d78870f3a2d918eae0c`.
Signing identity unchanged. No DSH restart, upgrade, remote, or publication.

## Implemented scope

C04: bounded JSON import with explicit preview/application, document export, immutable
SHA-256 local history and restore, color and animation-speed controls, and optional
weighted preset phrases. Existing settings migrate through defaults. Weighting
content source kinds remains separate/open; preset weights do not invent memories.

C11: shared colors/size/pacing, decision/disconnection precedence over standby text,
voice animation state, measured-window rotation clamp. No overlay voice buttons.

Real document-picker testing exposed the overlay intercepting a file tap and opening
MainActivity (RESULT_CANCELED). Client-owned file picker lifetimes now suppress the
overlay and wake microphone; pending document/export state survives Activity saved
state. The same fix covers appearance ZIP import. Numeric fields now request numeric
keyboards after the Chinese IME converted a decimal point to punctuation.

## Executed verification

- Host Java/API29 compilation and Termux signed build passed.
- check-conversation.sh passed conversation (10), habitat mapping/settings, notification
  archive, schedule registry (12), and voice reply (5) checks. Added checks cover
  weighted distribution with fixed seed 7, invalid/unknown fields, optional defaults,
  archive identity, old-version preservation and tamper rejection. Synthetic events
  are not live backend acceptance.
- Real settings editor rendered all controls. Export produced a readable UTF-8 JSON
  document in the existing controlled example folder.
- A controlled JSON document (same Agent name, animationSpeed 0.75) passed Android
  document selection, displayed preview, applied, and remained after client force-stop
  and cold launch. Cancelled import did not activate.
- History preview/application restored the original Silver Wolf settings. Original
  animation pacing, name, tag, colors and phrases were restored after the tests.
  The temporary external test document created by this run was removed.
- Landscape overlay screenshot showed the entire character/status panel within the
  available display, with no buttons. Original rotation settings were restored.
- Recorded a 10-second, 540x1168, silent real-device settings/menu/import-preview clip;
  sampled frames inspected locally. It is not acoustic or full M4.5 acceptance.

Demo: `artifacts/demos/20261025-020914-m45-settings-import-preview-07ae4674.mp4`.
Screens: `artifacts/m4/m45-settings-ready.png`, `artifacts/m4/m45-overlay-landscape.png`.

F05/F12 are verified only within the listed subset. Full size/font/inset/permission
revocation matrix, content-kind weights, user visual acceptance and remaining F gates
remain open. No claim that M4.5 or M5 is complete.
