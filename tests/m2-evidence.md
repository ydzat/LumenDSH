# M2 Device Bridge Acceptance

Date: 2026-10-24. Source baseline: `eec476d` plus the M2 commit containing this record.
Device: Huawei nova 5T / YAL-L21, Android 10, Termux 0.118.3. Wireless ADB endpoint
was reverified as `<PHONE_IP>:5555`. DSH remained `0.1.0-rc.7-termux.1`.

## Build

The independent Termux toolchain built and signed version `0.2.1-m2`, code 4.
Final APK SHA-256: `c954b29fec5d72d4b4d6da4f4c9ed6fd78a0cada18b4beb1c5b8064e038e5eec`.
The persistent developer certificate remains unchanged from M0/M1.
Final in-place installation succeeded; cold launch was 249 ms.
Private artifact: `artifacts/m2/sliver-wolf-0.2.1.apk`, excluded from Git.

Main integration checks used `2de65e2ef94b1482e2b32dcbcc0f7ba8dd47706d78a9b8f5146d23f4081a45cb`.
The final APK only removes the storage-only argument fingerprint from wire results;
it retains the tested speech, operation journal, and artifact transfer behavior.
Final recovery verification checks that the fingerprint is absent.
That check passed on the final installed APK without replaying audio.

## Real-device results

| Case | Result | Evidence |
| --- | --- | --- |
| D01 | PASS | Existing DSH discovered five actual MCP tools. In the same designated M1 acceptance session it invoked `assistant_speak` and `assistant_get_operation`; both tool results had `isError: false`. Submission returned `pending`, later lookup returned `succeeded`, and the agent turn ended `completed`. The earlier status/notification path remains covered by the preview record and the current SDK check. |
| D02 | PASS for authentication, Origin, notification permission, and unsupported input | Missing/incorrect bearer credentials and foreign Origin were refused. Unsupported surfaces and conflicting operation arguments were rejected. With the actual Android notification setting disabled, status reported false and show returned a permission error without reserving an operation. The original enabled setting was restored and verified. |
| D03 | PASS for speech and upload recovery/retry | Speech completion and explicit stop callbacks were observed. Reusing a speech key returned the existing operation without replay; changed text with the same key was rejected. After force-stop during `running` speech, restart retained `unknown`; resubmitting the original key did not play again. After force-stop during an incomplete upload, restart marked it failed, discarded its partial file, and refused finalization. |
| D04 | PASS | An authenticated 88,000-byte upload crossed from Termux into app-private storage; app-side size/SHA-256 verification succeeded. Repeated chunks/finalization were safe. Wrong digest, oversized declaration, path-like key, and changed metadata were rejected. The file helper also streamed a real explicit Termux file and returned the same artifact on retry. Test artifacts were explicitly discarded after checks. |

The installed system engine selected by Android was `com.google.android.tts`. A short
Chinese utterance completed through real TTS callbacks. After a requested replay, the
user confirmed that the audio was audible and clear. An English utterance was explicitly
stopped and recorded as `cancelled`. No audio was recorded; no engine or voice pack was
installed, and no system voice selection or volume was changed.

The notification permission check used the actual system Settings switch. A preceding
AppOps-only attempt did not make `areNotificationsEnabled()` false on this device and
was not counted as success. The AppOp was restored to its original effective default
allow mode; the Settings notification switch was verified enabled after testing.

## Reproduction

Set the documented `DSH_ROOT` and `SLIVER_WOLF_TOKEN_FILE` in the existing Termux
environment. Run from the source `integrations/device-tools/` directory with the app open:

```sh
node check-device.mjs
node check-speech.mjs
node check-artifact.mjs
node check-upload-helper.mjs ../../apps/android/AndroidManifest.xml
```

The speech check is audible. For interrupted-operation checks, set `CHECK_STATE_FILE`
to a private test-state file, run `check-speech.mjs prepare-crash` or
`check-artifact-recovery.mjs prepare`, force-stop only `local.sliverwolf.assistant`,
relaunch it, then run the corresponding `recover` phase. The TTS check must actually
reach `running` before the force-stop. State files contain only controlled test input.

`check-notification-denied.mjs` must run while the actual app notification setting is
disabled. Verify that switch state rather than inferring it from a tap or AppOps exit
code; bring the client back to the foreground before invoking the check. Restore the
original setting afterwards. Do not change permissions for unrelated applications.

The active adapter was copied into `~/.local/share/sliver-wolf/device-tools/0.2.1`.
Only the existing owned MCP entry was updated through profile HMR, retaining its prior
patch backup. DSH was not restarted or upgraded. No model credentials were read, and
no test session identifiers or private transcript/log files are committed here.

## Scope and limits

- M2 establishes a foreground native capability bridge, explicit TTS, and opaque artifact
  handoff. It does not implement continuous conversation, STT, wake-word detection,
  background/lock-screen service guarantees, or the M3 audio lifecycle gates.
- Android callbacks plus the user's audible confirmation establish the tested playback;
  they do not constitute acoustic quality, latency, power, or multi-engine benchmarks.
- Interrupted speech is deliberately `unknown`, never assumed complete or automatically
  repeated. Operation retention is bounded; no unbounded exactly-once promise is made.
- Transferred bytes are inert and unextracted. Update validation/activation belongs to
  M5/M6. No update tool is advertised yet.
- Pairing is developer-assisted and single-peer; production pairing/revocation UX remains
  a productization follow-up. The current listener follows the client Activity lifecycle.
