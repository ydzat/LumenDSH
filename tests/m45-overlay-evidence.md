# M4.5 Overlay Evidence

## Target and build

Huawei nova 5T / YAL-L21, Android 10, explicit USB device selection.
Local Termux build 0.4.13 (code 20), APK SHA-256:
`f7cd0b69aed702a80dfebf4e97f311610680025c0f9e96a1b5193ea7915eb83a`.
The APK signature verified and in-place installation succeeded. Host Java compilation
also passed. No DSH restart, replacement or upgrade was performed.

## Observed on device

The user granted overlay permission. The foreground overlay appeared over a controlled
test Activity with the imported default Silver Wolf sprite and actual session status.
After the interaction correction, visual inspection of
`artifacts/m4/m45-overlay-no-buttons.png` confirmed there are no overlay buttons.
The main habitat removes its duplicate long-press voice shortcut and retains its
explicit manual voice control (source inspection and compilation; no new spoken test).
Earlier checks exercised drag-to-edge, explicit hide and settings recall. The previous
manual overlay voice route was removed and is not a final product capability.

## Open gates

F11/F12 remain PARTIAL. Automatic keyword detection and background recognition/reply are now implemented
as a partial preview; the user reported successful but unstable owner wake. See
[m45-backend-evidence.md](m45-backend-evidence.md). V03 and outstanding V04 cases remain open. Notification action,
permission revocation, stable landscape, process-loss and full lifecycle matrices
are not established by these observations. The character-relative reply-card placement
change is built but has not received its separate layout acceptance.

The 0.4.23 follow-up verifies a landscape clamp and document-picker suppression; see
[presentation evidence](m45-presentation-config-evidence.md). Other lifecycle cases
remain open.
