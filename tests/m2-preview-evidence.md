# M2 First Device Tool Path

Date: 2026-10-24. Baseline: `57350e6` plus this preview change. Huawei YAL-L21,
Android 10; Termux 0.118.3; unchanged DSH `0.1.0-rc.7-termux.1`.
Explicit wireless ADB was reverified. This is partial M2 delivery, not its exit gate.

## Build and device results

- Phone build/sign verification succeeded using the existing independent toolchain.
- APK `0.2.0-m2-preview`, code 3, installed in place; cold launch 428 ms.
- APK SHA-256: `1e8c4bc3e2639fd1f15c189e69f54b13848ae474fc0519830ad068718fe8c44f`.
- Developer certificate unchanged from M1. APK is ignored at `artifacts/m2/sliver-wolf.apk`.
- Existing client selection/history remained available after the upgrade.

`node check-device.mjs` on the phone used the actual installed MCP SDK client and
stdio server adapter against the Android app, producing:

```json
{"authenticationRejected":true,"foreignOriginRejected":true,"discoveredTools":["assistant_get_status","assistant_show","assistant_get_operation"],"notificationState":"succeeded","retrySameOperation":true,"invalidSurfaceRejected":true,"uiVisible":true}
```

The Android notification service contained one active notification from this package.
Its actual notification row was tapped; Android reported MainActivity as resumed.
No other app's notification content was retained or printed.

The empty Web profile patch list was backed up, then one MCP entry was added via the
installed profile HMR mechanism. No DSH restart, package update, or model credential
change occurred. The adapter release is separate from the candidate source tree.

The same explicitly designated M1 acceptance session then called
`mcp__sliver_wolf__assistant_get_status` and `mcp__sliver_wolf__assistant_show` through
the running DSH. Both durable tool results had `isError: false`. Status reported the
actual app version and notification availability; show reported `state: succeeded`
with notification-posted semantics. The turn ended `completed`. No new agent/session
was required for device tools. Personal session history was not inspected.

## Acceptance scope

| Case | Result |
| --- | --- |
| D01 | PASS for status/notification discovery and actual phone action through existing DSH |
| D02 | PARTIAL: wrong credential, foreign Origin, and unsupported surface rejected; Android permission revocation not exercised |
| D03 | PARTIAL: repeated operation returned the identical durable result; mid-operation process death and restart still NOT RUN |
| D04 | NOT RUN: artifact transfer is not implemented |
| UI01 | NOT RUN: product visual design is planned for M4 |

Initial verification exposed two harness issues that were corrected and rerun: Huawei
dialog buttons use uppercase labels, and Termux's `process.execPath` resolves to the
Android linker. These were test/provisioning fixes, not a DSH deployment change.

The preview's bridge follows Activity lifetime. No voice playback, always-on service,
overlay, lock-screen wake, production pairing/revocation UI, or update handoff is claimed.
The remaining M2 work includes these contract-specific pairing/operation refinements,
speech tools, permission failure checks, process-death recovery, and bounded artifact transfer.
