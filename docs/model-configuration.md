# Model-aware client configuration

Status: implemented in Android 0.7.46 and companion host 0.2.1, 2026-10-25.

## Authority and reuse

The installed DSH 0.2.1-rc.3 `sessionController.agents.selectionFor` resolver owns
the effective next selection, including pending changes and deployment defaults.
The companion adapter calls native `llm.resolveModelInfo` for that exact provider
and model. No model-name table, external model catalog or provider credential is
copied into the Android client. This version-specific access is isolated in
`integrations/companion/model-config.mjs`.

The authenticated read-only `/sliver-wolf/companion/model?sessionId=...` endpoint
returns only selected model metadata, observation time and bounded policy values.
It uses existing cookie/Host/Origin admission. Selection is rechecked after lookup;
a concurrent switch cannot publish another model's capabilities as current.

Android clears metadata across connection generations and selected-session changes.
It refreshes on connection readiness, native snapshots, model-selection/context
projections and request context/header changes. Obsolete asynchronous results are
discarded. Lookup failures display unavailable state without breaking the ordinary
chat connection. The secondary **Model and context** page follows these updates.

## Configuration semantics

| Value | Owner and behavior |
| --- | --- |
| Provider/model/reasoning | Native effective selection and exact adapter defaults |
| Input modalities | Adapter metadata; absent means unknown; native prompt admission remains authoritative |
| Context capacity | Exact adapter `context.contextWindow`, shared request/response capacity |
| Default output | Adapter `defaultMaxTokens`; not a hard model limit |
| Hard maximum output | Unknown: the inspected exact-model API does not expose this field |
| Autonomous output | Each actual request uses the smaller of user budget and explicit native request maxTokens, or the exact model default when no explicit value exists |
| Normal user output | Unmodified native DSH request configuration |
| Compaction thresholds/input fitting | Native DSH; no client truncation, second compactor or stale usage subtraction |
| Embedding context/speaker thresholds | Independent local model settings; not derived from the chat model |
| Activity steps/time/writes/daily count | User policy; never enlarged because a model has greater capacity |

Missing metadata does not become a guessed capacity. A metadata-resolution failure
in an autonomous request fails explicitly; cancellation is checked after lookup.
The activity receipt records actual provider, model, context, default output and
applied maxTokens. The page distinguishes a default-based preview from the same
model's last actual request, because request hooks can override defaults.

DSH rc.3 seeds later requests from durable headers. A scoped, persisted override
receipt therefore retains the original output setting before applying our cap.
On later requests the adapter removes only its own matching route/value override;
it preserves an unrelated explicit setting or changed route. A missing original
value returns to the model's current native default. This also prevents repeated
autonomous steps from treating their previous cap as a user-selected limit.

## C26 evidence

- Seven local policy/model tests passed, including lower model defaults, explicit
  request overrides, unknown capacities and a selection-change race. These are
  logic tests, not device/network evidence.
- Actual authenticated phone reads reported `deepseek-flash`, displayed name
  `DeepSeek-V4.1-Flash`, context 1048576, default output 256000 and text/image input.
  Those numbers are adapter-reported configuration, not independent provider limits.
- Native selection in the existing controlled session changed to
  `deepseek-v4-pro` and back to `deepseek-flash`; the read endpoint followed both
  selections and unauthenticated access returned 401. No model inference was needed.
- The credential-free isolated host had no registered models and therefore could
  not exercise selection; it is not counted as a passing model-switch test.
- Native selection materializes a reasoning default into profile configuration.
  The first exact-default restoration check detected this difference. The original
  default override block (including its absence) was restored from the activation
  backup and the same DSH version reloaded. The reusable check now preserves that
  block explicitly; it requires a subsequent idle-gated reload.
- Phone build/sign/install succeeded; certificate unchanged. APK SHA-256:
  `87c5af8e6c114bca5a403477116c004a7a26956adb482eb0086fdf6eef8db69e`.
  Actual model settings were visually inspected with controlled-session metadata.
- A real autonomous step used maxTokens 2048. The initial follow-on user request
  exposed inherited maxTokens 2048, despite native default 256000. After adding
  scoped override restoration, the same real sequence passed: autonomous 2048,
  ordinary user request 256000, with successful smart memory recall. Model
  configuration checks therefore cover an actual behavioral regression, not only
  a metadata page. Original behavior policy and selected session were restored.
- Restoring the original user session also verified distinct per-session metadata:
  its retained model is `deepseek-v4-flash`, context 1000000, default output 256000.
  The controlled session uses `deepseek-flash`, context 1048576. These are retained
  native selections, not client aliases; no model was forced onto the user session.

Final deployed host revision:
`73e665a55632c1943c83b1b398d305df8d047e277d311da5a183ac328e203014`.

Reviewed real-device demonstration: approximately 9 seconds, no audio,
`artifacts/demos/20261025-154437-model-config-live-f8b8b462.mp4`.
The first static-screen recording contained only one second of timestamped frames
and is not used as the stage demonstration.

No claim is made for unregistered providers, a hard output limit that DSH does not
publish, or independent accuracy of provider-advertised capacities.

## Independent model selection (0.8.2, 2026-10-26)

The Android **Model and context** page now reads the native `session/modelCatalog`
and offers two separate controls: the persisted default for new sessions and an
explicit selection for the current session. A third action copies the current
default into the existing session; it is a one-time selection, not a live binding.
Provider/model identifiers and labels come from DSH, never an APK model table.

The installed rc.3 `session/selectModel` also saves the deployment default. To
preserve independent choices, the authenticated companion `model-selection` route
reuses `resolveCallConfig`, `serializeImageAdmission`, `selectForNextRequest`, and
`agentDefaultModel.saveSelection` with separate scopes. It validates the live
catalog, leaves an in-flight request running, and installs the session choice for
subsequent requests. There is no automatic retry of an uncertain write.

The reported header issue was a retained legacy session route, not a hard-coded
HTTP header in the client. Existing conversations can select a current route
without rewriting history or creating another session. Unknown models are not
silently aliased or assigned guessed image capabilities. A failed metadata lookup
does not block opening the model selector. HTTP authentication remains the paired
DSH cookie, separate from model-provider credentials.

See [C32-C34 evidence](../tests/model-selection-evidence.md).
