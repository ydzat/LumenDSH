# Device Tool Contract v0.1

Status: authenticated MCP status/notification/speech/operation tools and bounded
artifact handoff implemented. Foreground recovery paths are verified; background voice
is M3. See [implementation](../../integrations/device-tools/README.md) and
[device evidence](../../tests/m2-evidence.md).

## Foreground interaction update (0.7.49)

The ordinary-reply policy and foreground keyword mode live in the client interaction
settings; MCP method names and explicit speech semantics remain unchanged. Foreground
keyword capture reuses the existing owner-gated service and relinquishes the microphone
for settings, dialogs, chat and manual recognition. See [interaction design](../design/settings-drawer.md).

## Direction and placement

The 0.7.48 character voice adapter keeps `assistant_speak` and
`assistant_stop_speaking` unchanged. The Android speech owner calls a separate
authenticated loopback synthesis service at port 3086; this is an internal audio
transport, not a DSH/session API or a new MCP Agent. Its credential is delivered
through the existing authenticated bridge's `voice/pair` method and stored in
private preferences. Completed synthesis alone does not settle speech: Android's
playback completion callback does. Pending HTTP results are invalidated on stop,
so cancellation cannot be followed by late playback. Remote cancellation delivery
can fail if the service is unreachable; native work retains its bounded deadline.

The app is a client of DSH for conversations and a provider of device tools for DSH. Prefer a loopback-only MCP service in the Android app, with DSH acting as its MCP client. If the installed DSH cannot consume the selected transport, first evaluate a thin adapter in Termux; do not upgrade DSH implicitly.

M2 preview uses an explicit thin Termux adapter even though installed DSH supports
Streamable HTTP: the official SDK owns MCP, while the already approved Java-WebSocket
library owns the authenticated Android boundary. This avoids another APK protocol
dependency. DSH remains the standard MCP client; the app boundary is an internal
WebSocket protocol, not advertised as a standard MCP transport.

The M2 baseline keeps this transport and adds Android system TTS. It does not
automatically speak ordinary conversation replies. Playback requests are asynchronous;
only TTS completion callbacks settle them as succeeded. Losing the client process
leaves unfinished speech unknown and never replays it automatically.

References: [MCP tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools), [MCP transports](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports), [Termux API](https://github.com/termux/termux-api).

## Initial tool surface

These are Sliver Wolf tool names, not existing DSH tools.

| Tool | Core input | Result |
| --- | --- | --- |
| `assistant_get_status` | No required arguments | App/version, voice/UI states, installed capability availability |
| `assistant_show` | Requested surface | Actual shown state or a platform/permission error |
| `assistant_speak` | Text; optional installed voice profile ID | Operation ID and acceptance; completion reported separately |
| `assistant_stop_speaking` | Optional operation ID | Whether playback was stopped |
| `assistant_set_appearance` | Installed appearance ID and version | Applied version or validation error |
| `assistant_stage_update` | Approved artifact reference and expected digest | Candidate ID, validation result, activation requirements |
| `assistant_activate_update` | Validated candidate ID | Operation ID and actual activation status |
| `assistant_get_operation` | Operation ID | Pending/running/succeeded/failed/cancelled/unknown plus bounded details |

Availability is discovered. Do not advertise unimplemented update tools during initial connection tests. General shell execution, arbitrary filesystem paths, virtual display control, and broad accessibility automation are not initial tools.

## Results and operations

Use MCP input/output schemas and structured results. Each side-effecting operation carries an idempotency key in its tool arguments; JSON-RPC request IDs alone are not durable deduplication keys. The app persists enough operation state to avoid duplicating an activation or replaying speech after a retry. A reused key with different arguments is an error. Retention duration and storage bounds must be chosen in M2.

Use distinct errors for invalid input, unsupported capability, unavailable service, permission required, busy resource, stale candidate, and execution failure. Preserve MCP protocol errors separately from tool execution failures. An acknowledged request is not proof of completion; if the app dies, reconcile operation state rather than reporting success.

Implemented operation lookup uses the caller's `operationKey`; responses include an
opaque `operationId`. The journal holds at most 64 records. Terminal non-artifact
records are eligible for removal after 24 hours. A reused retained key with different
arguments is rejected using a private fingerprint; speech text is not persisted in
the journal. A full journal fails explicitly rather than evicting active operations.

## Android lifecycle and trust

Bind only to loopback and authenticate requests using pairing credentials held outside conversational prompts. Validate Origin when applicable. Other Android apps can reach loopback, so address binding is not authentication. Android grants and DSH approval rules both remain effective.

Microphone, audio focus, incoming-call handling, wake detection, and normal answer playback are deterministic app responsibilities. A microphone stop action must work without a model response. Speech tools cover explicit/unsolicited playback; ordinary assistant answers must not be read twice.

The service advertises current availability when permissions change or the app restarts. DSH must tolerate app/tool unavailability without losing its conversation.

## Artifact transfer boundary

Termux and the APK have separate private directories. A Termux path is not automatically readable by the app. M2 must establish one bounded authenticated transfer mechanism (streamed upload or scoped content handoff), with byte limits, integrity verification, and operation ownership. `assistant_stage_update` references that transferred artifact; it must not read an arbitrary remote URL or private path supplied by a prompt.

Implemented handoff uses the authenticated internal bridge's `artifact/begin`,
`artifact/chunk`, `artifact/finish`, and `artifact/discard`. Chunks are at most 32 KiB;
each artifact is 1 byte to 8 MiB, with at most eight files and 32 MiB reserved. Files
use app-generated IDs in app-private storage. The declared size and SHA-256 must
match before a `.part` file becomes a completed opaque artifact. No URL/path is
accepted by Android, and no extraction/activation occurs. The paired credential owns
the operations. Successful artifact records remain until explicit discard; a partial
upload interrupted by process death is failed and discarded on recovery.

## Reviewed inbound shares (M4.5)

The reverse path uses the same paired loopback boundary. Android accepts external
content URIs only into inert private ShareInbox storage. A user confirmation binds a
batch to the selected session and reserves its prompt request ID before submission.
Only then do share/manifest and share/chunk permit reads for that batch/session.
The Node MCP adapter implements assistant_receive_share; it writes generated file
names under its configured private share directory, checks byte counts and SHA-256,
atomically commits a batch receipt and acknowledges the hashes to Android.
Chunks are at most 32 KiB; the upstream limits remain 16 MiB/file, 32 MiB/batch and
16 files. Sender display names are metadata, never destination path components.
Repeated reception verifies the same committed batch, without another prompt.
An interrupted/unknown prompt is not resent automatically. Deleting the Android
batch revokes future reads; already received Termux files are retained.

## Owner-bound wake boundary

Owner voiceprint enrollment, replacement, deletion and raw embeddings are not MCP
operations. The user performs enrollment locally after Android device-credential
confirmation. `assistant_get_status` exposes only wake phase and numeric diagnostics,
including owner rejection count; it does not export audio or voiceprint vectors. A
voiceprint match permits the wake cue/command recognition, not blanket tool approval.

## M8 speech providers

Existing `assistant_speak`, `assistant_stop_speaking` and operation/status contracts
remain unchanged for system, phone-local and HTTPS speech. Each utterance is one
operation even when split into network/audio segments. The authenticated internal
`voice/provider` pairing method saves device-local provider credentials and returns
only its selection reference; it is not advertised as an Agent MCP tool and requires
the foreground app. Only actual model/voice IDs may be activated.
