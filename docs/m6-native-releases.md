# M6 Native Releases

Status: COMPLETE within B02/U05 and the recorded native recovery scope on 2026-10-25. Installed client: 0.6.39-repair, versionCode 39.

## Ownership and installation boundary

This local-only installation retains the existing private developer signing identity in Termux, outside source, APK content and receipts. No shared production key or public distribution is introduced. Build tools consume the keystore/password files internally; their contents never enter prompts or logs. Every upgrade and higher-version repair must match the installed package/signing identity.

The client uses Android API 29 PackageInstaller sessions and the system confirmation flow. REQUEST_INSTALL_PACKAGES does not grant silent installation. The local update screen opens Android's per-app installation permission page. An authenticated request stages a candidate; an explicit install request subsequently invokes the system UI. Candidate bytes never come from caller-chosen app filesystem paths or remote URLs.

## Build and transfer

`native-release.mjs CANDIDATE_SOURCE BASE_CODE RELEASE_ID` invokes the independent phone build, verifies pinned dependencies and APK signature, and links source/resources/build scripts/dependencies, DEX and signed APK digests. It refuses modified inputs during compilation. Pure hash/atomic receipt helpers are reused from the pinned upstream extraction.

The same agent uses its existing command tool and normal DSH approval semantics. Candidate source is separate from the active APK. Initial real-agent build used a new copy containing only apps, tooling and integrations, excluding old build outputs and private signing material.

`native.mjs stage APK RECEIPT` reuses authenticated chunk transfer with explicit `purpose: native-apk` (256 MiB/file, 512 MiB total inbox bound). Ordinary content retains its 8 MiB/file and 32 MiB total admission limits. Transfer processing runs outside the UI thread. Operation journal updates are serialized across the UI and transfer worker.

## Client validation and recovery

The client checks the exact current base code, increasing target code, package ID, minimum SDK, current signing certificates, complete APK digest and staged readback. Android performs final cryptographic installation validation. Source/DEX digests link to the locally verified build evidence; the client does not reconstruct source from an APK.

The staged receipt is persisted before installation. The installer session ID is recorded before writing/committing bytes. System callbacks retain pending-user-action, success or failure. Next status reconciliation recognizes the exact installed version/hash, or marks a vanished unresolved installer session failed. It never blindly retries an uncertain install.

`repair-release.mjs EVIDENCE BASE_CODE HIGHER_CODE RELEASE_ID` verifies retained build inputs, copies only those inputs into a new candidate directory, increments the Android version and rebuilds/signs them. It does not mutate the active APK or assume that Android permits downgrade. No data migration is implemented in this release.

The app restores selected session/profile and previously opted-in voice/overlay services on foreground launch. Package replacement also restores persisted schedule alarms. Native replacement interrupts client audio; DSH owns the ongoing task independently.

## Platform references and device finding

- https://developer.android.com/reference/android/content/pm/PackageInstaller
- https://developer.android.com/reference/android/content/pm/PackageInstaller.Session

On the target YAL-L21 Android 10, GET_SIGNING_CERTIFICATES alone returned a parsed archive with null signingInfo. A device instrumentation check showed that combining GET_SIGNATURES with GET_SIGNING_CERTIFICATES returned signingInfo and one legacy signer for both installed and candidate packages. The implementation still compares actual modern signing certificates; it does not accept an unsigned archive or replace certificate checks with a hash.

## Evidence recorded during implementation

- Same-agent native build: `m6-agent-37`, base 36 / target 37; actual DSH command-tool invocation completed the independent phone build and wrote the linked receipts. APK SHA-256 `f5303464450a43da5c7aae26734a31ec7f6e60a8ee634e1be752c006b56ee37e`. This discovery candidate was not installed after the archive-query incompatibility was found.
- Final normal update: `m6-final-38`, base 36 / target 38, APK SHA-256 `2a3f7660e79c0de250283c580f46e85ae7fc0a9d2ba96a9c2fac5dbf9d5dabe6`, source digest `10ed776ae05677a2ba48a0e8eac059e95e960e6794cd971f0c7062f94f545c5c`, DEX digest `389d25ae2ede2624e891ca11f7103e8839473a01eeac126dfdf9fb6ef01a90bd`.
- The user's installation-source grant was confirmed, then the real Android update dialog was used. Native status reconciled `installedCode: 38`, `state: installed`, exact installed APK hash. This was the app's PackageInstaller path, not `adb install`.
- A real same-session DSH bash/Node task waited across client replacement, remained running after code 38 launched, and then completed normally with `DSH_TASK_SURVIVED_CLIENT_UPGRADE` after the test continuation marker. No backend restart or replacement occurred.
- Instrumentation captured only local baseline data and reported `UPGRADE_DATA_AND_OWNER_DECRYPTION_PRESERVED`: same selected session, voice auto-send preference, wake/overlay opt-ins, presentation configuration, schedule registry, active profile pointer and encrypted owner records; owner data remained decryptable through the existing Android keystore. Baseline data were not exported or committed.
- The update was requested at an idle-audio boundary. The app process/services were replaced, so wake/listening and TTS availability were interrupted until foreground reopening and initialization. No live utterance was used and no acoustic continuity claim is made.
- Higher-version repair build: `m6-repair-39`, base 38 / target 39, APK SHA-256 `724df3965f7d08b2c37a6ebb808132f98c7ca3ab460964bb2d6981b2af74bb62`. Retained source digests were checked before copying; DEX is identical to code 38, while the package version increases. The real PackageInstaller update from 38 to 39 completed after the system identity confirmation. Installed hash/code reconciled, and the same data/owner-decryption preservation check passed again.

Termux's Node reports the Android linker as process.execPath in this setup. The repair launcher therefore invokes `node` from the configured environment instead of executing process.execPath as if it were a Node binary. The corrected launcher was run beside the original retained source file so that its evidence could still be verified; the repository contains the corrected launcher.

Final negative checks rejected stale native bases, wrong APK digests and non-increasing candidate versions using real completed artifacts, without changing the installed release receipt: `NATIVE_STALE_HASH_VERSION_REJECTIONS_PASS`.

The retained-source repair is an actual higher-version rebuild and installation, not a forced deployment of deliberately broken native code. It is explicitly initiated and requires Android confirmation; unattended recovery from every possible OS/installer failure is not claimed.

Local silent M6 demonstration: `artifacts/demos/20261025-030416-m6-installed-repair-7c9512d4.mp4` (decoded duration 6.015 seconds). Screen and decoded video frame inspected. It shows code 39 installed with the reconciled receipt. The temporary device-test APK was uninstalled and temporary ADB forwards were removed.
