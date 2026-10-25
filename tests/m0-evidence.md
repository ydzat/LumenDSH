# M0 Device Evidence

Date: 2026-10-24, approximately 20:05–20:16 CEST. Device: Huawei YAL-L21,
Android 10. Wireless transport explicitly selected as `<PHONE_IP>:5555`.
Baseline revision: `a21f782`; implementation source is the local M0 commit containing
this record. APK provenance is additionally tied to the hashes below.

## S01 — PASS (compatibility discovery)

See [installed capability matrix](../integrations/dsh/compatibility-rc7.md) for
source pins and evidence levels. Deployed DSH is `0.1.0-rc.7-termux.1`;
the live API version field is `0.0.1`.

Procedure: verified wireless shell; probed the existing service; inspected installed
API/connection code; ran `integrations/dsh/probe.mjs` in Termux. Expected and observed:

- `host.describe` and `session.list`: valid correlated success envelopes, one session.
- Foreign Origin / untrusted raw Host: HTTP 403.
- History/cancel for a fresh nonexistent ID: `session-not-found`.
- Reply to a fresh nonexistent decision: `not-pending`.
- Both event endpoints: plain GET 426; WebSocket opens successfully.

Authentication is absent, not passed or bypassed. Deduplication is not assumed.
No raw conversation, key, or credential was extracted. The probe caused no agent turn.

## B01 — PASS (phone build, install, launch)

Preconditions: existing Termux 0.118.3 and running DSH; no client toolchain initially.
Installed only new Termux packages using `--no-install-recommends --no-upgrade`:
34 packages, 132 MB downloaded, reported 341 MB additional space; zero upgrades/removals.
JDK dependencies include audio/X11 libraries; these are build-environment dependencies,
not APK features. No DSH restart, replacement, or upgrade was performed.

Procedure: [build instructions](../tooling/android-build/README.md); original Java/XML
sources; real aapt resource/manifest packaging; javac; D8; persistent private developer
key; apksigner verification; binary transfer over authorized ADB; `adb -s <endpoint>
install -r artifacts/m0/sliver-wolf.apk`; explicit Activity launch.

Observed:

- Package `local.sliverwolf.assistant`, version `0.0.1-m0` / code 1, min/target SDK 29.
- Installation `Success`; cold launch `Status: ok`, first launch 540 ms.
- apksigner verifies APK Signature Scheme v3 for this minimum SDK.
- APK SHA-256: `f82eac455857930d2a20b50c47c99aaa45a32316b20a9502aa8498c14d421c11`.
- Certificate SHA-256: `4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.
- Three successful fresh-output phone builds produced the same APK hash. This is a
  bounded repeatability observation, not a guarantee across future toolchain releases.
- UI hierarchy and screenshot show `Connected to phone-local DSH`, API version `0.0.1`,
  one available session, and the explicit no-authentication limitation.
- Force-stopped only the client, launched it again (163 ms), and visually verified
  restored connection. DSH remained available. There was no active agent task, so this
  does NOT pass S05.

Private local artifacts: ignored `artifacts/m0/sliver-wolf.apk` and `client.png`.
Phone build outputs remain in the dedicated `~/sliver-wolf-build/source/artifacts-build-*`
directories. Signing material stays in `~/.local/share/sliver-wolf/signing`, outside
source and APK. No project remote, upload, or public release was created.

## Not run

S02–S05 and all D/V/P/U scenarios and B02 remain NOT RUN. In particular, real
approvals, questions, active-task cancellation, task retry/recovery, speech, lock-screen
wake, overlays, appearance import, and native self-upgrade have no acceptance result.
