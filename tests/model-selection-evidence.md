# Model selection evidence — Android 0.8.2

Date: 2026-10-26. Device: Huawei YAL-L21, Android 10. Backend: DSH 0.2.1-rc.3.

## Source findings

- The Android transport does not hard-code a provider/model in prompt submissions.
  The reported `request/header` retained an old session route.
- Installed `session/selectModel` couples session selection to saving a default.
  The adapter reuses its underlying normalization/admission/selection primitives
  but exposes independent session and default operations.
- The live DSH catalog advertises Flash and Pro. No provider credentials or
  personal conversation contents were copied into source or evidence.

## Real-device results

- C32 PASS: authenticated native catalog read; session selection changes while
  the default remains identical.
- C33 PASS: changing the default leaves the existing user session unchanged;
  explicitly copying the default changes only the addressed session.
- C34 PASS (bounded): unauthenticated writes return 401, unknown model writes
  return 400 without changing selection. A controlled real model turn completed
  with a new `request/header` matching the selected Pro route. Flash metadata
  advertises text/image input. This is not a new end-to-end image-reading test.
- The original user conversation was switched in place from its retained legacy
  Flash route to the currently advertised default Flash route. Its identity and
  history were preserved; no replacement conversation was created.
- The authorized idle-gated plugin reload retained 24 session identities and
  checked existing document storage/content in all 24 scopes.
- Android model settings opened successfully through instrumentation and were
  visually inspected. Both independent controls and current DSH metadata rendered.
  A discovered preferences Long/Integer mismatch was fixed before final installation.
- The server's required `agentDefaultModel` injection was corrected after the
  first default-save test failed. Those failed attempts are not passing evidence.
- Request headers are emitted on change, not every turn. The final live check
  deliberately changes the route before checking a newly emitted header.

## Reproduction and artifact

`tests/model-selection-live.mjs <existing-user-session-id>` runs on the phone and
uses the existing controlled session recorded in the companion private directory.
It performs real inference and restores the controlled/default selections in a
finally block. It never prints authentication values or private messages.

`ConnectionChecks` mode `models` opens the actual model page for visual inspection;
this mode is not an automated assertion of every touch interaction.

Final installed APK SHA-256:
`62240581540e5db9ba83b1bb7b0ce773b1ddde2da9008a5af68870aa077d2f06`.
Existing signing certificate retained. JS syntax checks and `git diff --check` passed.

No unattended test of model selection during a long-running task or simultaneous
edits from multiple clients is claimed. The adapter uses native next-request selection.

Reviewed 10-second silent device demo: `artifacts/demos/20261026-005024-model-selection-dfce43e6.mp4`. Shows the actual settings and native model options, with no conversation content.
