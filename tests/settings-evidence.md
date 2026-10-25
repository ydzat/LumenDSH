# Settings redesign evidence

Target: 0.7.49-settings, Huawei YAL-L21 / Android 10.

- Host Java 8 compatibility compile with the pinned dependency jars: passed.
- Phone build (pinned Termux Java/aapt/D8/apksigner), signature validation and in-place
  installation: passed on USB device <USB_SERIAL>. No DSH service restart.
- Installed `SettingsChecks`: passed. Exercises grouped navigation, the home/chat
  reply-policy matrix, owner enrollment entry, optional confirmation, and preserved
  chat drafts. A separate controlled real DSH session accepted the home recognition
  result automatically and produced a correlated nonempty completed reply; the
  original session and reply preference were restored. This tests submission after
  recognition, not acoustic recognition itself.
- Foreground keyword service: real microphone sample count increased in standby;
  opening the settings drawer released microphone ownership. The temporary home
  keyword preference was restored. No recordings were saved.
- UI inspection: side drawer, hierarchy/back, current-value summaries, single-choice
  selection persistence and presentation sliders inspected on the phone.
- Acoustic foreground keyword recognition, owner rejection, noise and call interruption:
  not established by these UI checks; no synthetic recognition is claimed as acceptance.

`SettingsChecks` checks the installed platform UI and the actual reply-policy matrix,
then cancels a controlled confirmation to verify chat-draft isolation. Its live phase submits one controlled message in a separate DSH session and restores
the original selection. It does not claim to test recognition quality.

Related acceptance: V01 (voice interaction), F05 (configuration), F11 (voice
ownership), F13 (visual/accessibility) and F14 (upgrade). Only the exercised portions
are covered; the new settings checks do not close historical untested cases.

- Installed `HabitatInteractionChecks`: passed body-region taps, double-tap
  suppression, pinch scaling, idle fade, expansion and selectable transcript text.
  Touch callbacks were captured for gesture assertions; real DSH delivery is covered
  separately above.
- Final APK SHA-256:
  `a1023f43553729e9bb8c3500c7324ef10c6b181ad63f1c883c8d9a321b269917`.
- Silent real-device demo: `artifacts/demos/20261025-201444-settings-drawer.mp4`.
  The capture was cropped to the inspected settings panel at recording time to
  exclude the underlying conversation and system status bar. Frames were inspected.
  The video is ignored and is not a public upload.

Reproduce installed checks with explicit device selection:

```powershell
& 'D:\Programs\MuMuPlayer\nx_main\adb.exe' -s <device> shell am instrument -w local.sliverwolf.assistant.tests/.SettingsChecks
& 'D:\Programs\MuMuPlayer\nx_main\adb.exe' -s <device> shell am instrument -w local.sliverwolf.assistant.tests/.HabitatInteractionChecks
```

The first test creates a controlled DSH conversation and submits one bounded prompt,
then restores the original selection and preferences. It also briefly enables home
keyword capture and restores that preference. Run it on an idle phone.
