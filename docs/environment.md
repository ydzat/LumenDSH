# Development Environment

Repository handoff (2026-10-25): `origin` now points to
`git@github.com:ydzat/LumenDSH.git`; the existing remote MIT LICENSE was fetched.
Nothing was committed, merged or pushed in this documentation handoff. Older
"no remote" inventory entries below describe their dated baseline. See
[current project status](project-status.md).

## Current upgrade snapshot: 2026-10-25

DSH `0.2.1-rc.3` is now the only installed/supported backend; Android client
`0.7.46-model-config` is installed. `agent-start` starts the independent authenticated
loopback service. The original two conversations and original model credential
file remain in `~/.dsh`; no key was exported. See
[current paths, tests and limits](../integrations/dsh/compatibility-rc3.md).
USB serial `<USB_SERIAL>` was used for this upgrade. Recheck ADB before reuse.
The dated M0/M1/M2 inventories below are historical, not current deployment instructions.

Updated after M2 on 2026-10-24; runtime status is a snapshot, not a guarantee for later work.

## M2 verified state

- Client upgraded in place to `0.2.1-m2`, version code 4, with the existing signature.
- Explicit Android TTS selected `com.google.android.tts`; user confirmed clear Chinese
  playback. No voice engine/data installation, volume change, or microphone use occurred.
- Adapter release `~/.local/share/sliver-wolf/device-tools/0.2.1` is active through the
  existing profile HMR. Previous profile saved as `cordis.patch.yml.before-sliver-wolf-0.2.1`.
- Actual notification permission refusal, speech process-loss recovery, and bounded
  app-private file handoff passed their scoped checks. Notification settings were restored.
- DSH remains the independent existing deployment; see [M2 evidence](../tests/m2-evidence.md).

## Earlier M2 preview state

- Client upgraded in place to `0.2.0-m2-preview`, version code 3.
- Authenticated Android boundary at `127.0.0.1:3081`; listener follows Activity lifetime.
- One `sliver-wolf-device-tools` entry added to the previously empty Web profile patch
  via HMR; original patch retained as `cordis.patch.yml.before-sliver-wolf`.
- Adapter deployed separately to `~/.local/share/sliver-wolf/device-tools/0.2.0-preview`.
  Pairing token remains private; configuration references its path, not its value.
- Existing DSH called the real tools successfully without restart or upgrade.
  See [M2 preview evidence](../tests/m2-preview-evidence.md).

## M1 verified state

- Independent client upgraded in place to `0.1.0-m1`, version code 2, with the same signature.
- Real session streaming, decisions, cancellation, and client recovery exercised;
  see [M1 evidence and limits](../tests/m1-evidence.md).
- Phone build tool versions and existing DSH deployment unchanged from M0.
- Java-WebSocket 1.6.0 and SLF4J API 2.0.13 added to the independent phone build cache.
- Source baseline: local commit `669e685` plus the M1 changes accompanying this record.

## M0 verified current state

- Baseline Git revision `a21f782`, local `main`, no remote configured.
- Wireless shell reverified at `<PHONE_IP>:5555`; existing DSH HTTP 200 and API calls successful.
- No DSH restart/upgrade/replacement; installation version remains `0.1.0-rc.7-termux.1`.
- Termux build tools newly installed: aapt `16.0.0.4-4`, D8/apksigner `37.0.0`, OpenJDK `21.0.13`.
- Google platform 29 revision 5 downloaded and checked against metadata/hash.
- Independent app `local.sliverwolf.assistant` `0.0.1-m0` installed and visually verified.
- Private phone build root: `~/sliver-wolf-build`; developer signing files live separately
  in `~/.local/share/sliver-wolf/signing`. No signing material copied to the host or APK.
- See [build pins/commands](../tooling/android-build/README.md),
  [DSH capabilities](../integrations/dsh/compatibility-rc7.md), and [S01/B01 evidence](../tests/m0-evidence.md).

The following sections retain the explicitly dated pre-development inventory; the
current verified state above supersedes its uninstalled/stopped-service observations.

## Workspace

- Project directory: `<WORKSPACE>/sliver-wolf`.
- Local-only project: do not create remotes or upload project content without a new explicit user instruction. Local Git remains available for version history.
- Windows host; PowerShell shell.
- Git repository initialized on `main`; no project commit yet at this observation.
- Git, Java/Javac, Python, Node, and ADB are discoverable on the host PATH.
- Java path points to Microsoft JDK 17.0.20.101; version execution was not part of this check.
- Gradle is not on the inspected PATH. This does not prove no SDK/Gradle exists elsewhere.
- No project wrapper, Android SDK/toolchain, Android framework, or APK signing identity has been selected or validated.

## Phone and DSH snapshots

- Initial acceptance target: Huawei nova 5T, Android 10.
- Current development transport: Wi-Fi ADB at the last verified endpoint `<PHONE_IP>:5555`; USB was physically disconnected during the successful 20:02:13 CEST check. See [wireless debugging](wireless-debugging.md).
- Host ADB path: `D:\Programs\MuMuPlayer\nx_main\adb.exe`; use device selection explicitly if more devices are connected.
- Installed DSH reported `0.1.0-rc.7-termux.1`.
- Earlier pre-development check: `tmux has-session -t dsh` reported no tmux server. This was not rechecked during the later wireless ADB test.
- In that earlier check, a phone-local HTTP probe to `http://127.0.0.1:3080/` returned curl status `000`, so the expected endpoint was not reachable.
- No restart, update, permission change, or credential read was performed during this documentation task.

The existing Termux startup helper is `agent-start`. Treat that as a deployment-specific convenience, not a public App interface. Source and behavior of helpers must be checked before reuse.

## Required implementation evidence

M0 must record live DSH transport/authentication and exact installed package versions, a reproducible phone build toolchain, source pins and hashes, and successful launch of an independent test APK. The current desktop PATH inventory does not establish phone-build readiness.

Keep machine-specific credentials/paths in untracked local configuration where practical. Preserve the current DSH deployment and workspace. Host sandbox Git ownership warnings should be resolved using the correct user execution context; do not globally disable Git ownership checks.
