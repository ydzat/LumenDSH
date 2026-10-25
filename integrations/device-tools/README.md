# Phone Device Tools: M2 Baseline

Implemented path: existing DSH MCP client -> official MCP SDK stdio adapter in Termux
-> authenticated loopback WebSocket -> native Android actions. The adapter is independent
of DSH and the APK. It never owns DSH startup/shutdown. Status, notification, explicit
speech/stop, and operation lookup are advertised. Authenticated artifact handoff uses
the same internal boundary, without exposing arbitrary file reads as MCP tools.

## Transport decision and inspected dependencies

The installed `@deepseek-ai/dsh-mcp-client` `0.1.0-rc.7` supports both stdio and Streamable
HTTP, including headers. Its actual `createTransport` and `apply` implementations were
inspected. The bridge is a design choice to reuse the installed official MCP SDK and
existing Android WebSocket dependency, not a claim that DSH lacks HTTP support. It avoids
adding an APK HTTP server or implementing MCP framing manually.

The adapter resolves dependencies from explicit `DSH_ROOT`, without installing/upgrading
anything in that directory. Inspected installed dependencies are MCP SDK `1.30.0` and
`ws` `8.21.3`, both MIT; their distributed package manifests and runtime code are the
version evidence. No dependency source was copied or modified. SDK version mismatch
fails startup. Existing Java-WebSocket `1.6.0` now supplies the Android server as well
as session clients. See [Android provenance](../../apps/android/DEPENDENCIES.md).

Protocol references: [MCP transports](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports),
[MCP tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools).
Upstream implementations: [TypeScript SDK](https://github.com/modelcontextprotocol/typescript-sdk),
[ws](https://github.com/websockets/ws).

## Pairing and lifecycle

Android binds `127.0.0.1:3081/device-tools`. Each upgrade requires the app-generated
256-bit bearer token and rejects any Origin header. The local credential is separate
from model credentials. Open Device tools -> Pair Termux to access it; never put it in
chat, a tracked file, or command arguments. The listener follows Activity
lifetime, not a foreground service. Force-stop makes device tools unavailable; no
background/lock-screen availability guarantee is made.

During authorized ADB development, with the client visible:

```powershell
python tooling/android-build/pair-device-tools.py --adb <adb-path> --endpoint <wifi-address:port>
```

This reads only the client pairing field, writes directly to private Termux storage,
and removes its temporary UI dump. No credential is printed or saved on the host.
The generated token stays in app private preferences and
`~/sliver-wolf-build/private/device-token`. This is developer-assisted provisioning;
production pairing/revocation UX remains unfinished.

## Check and connect

In the phone's existing Termux environment:

```sh
export DSH_ROOT=/data/data/com.termux/files/usr/opt/dsh-0.1.0-rc.7-termux.1
export SLIVER_WOLF_TOKEN_FILE="$HOME/sliver-wolf-build/private/device-token"
cd "$HOME/sliver-wolf-build/source/integrations/device-tools"
node check-device.mjs
```

The real-device check posts one notification; it is not a fake MCP fixture. The Node
launcher is `node`, not `process.execPath`: the installed Termux launcher reports the
Android linker as its executable path.

For the inspected empty profile patch layer only, initial setup is:

```sh
node connect-dsh.mjs "$HOME/.dsh/profiles/web/cordis.patch.yml" \
  "$HOME/.local/share/sliver-wolf/device-tools/0.2.1"
```

The setup refuses a nonempty profile and existing destination/backup files. To update
the inspected profile containing only our existing entry, add `--update` and use a fresh
release directory. Profiles with other user entries require a deliberate manual merge.
It copies the adapter/upload helper into a separate release directory, backs up the
profile as `cordis.patch.yml.before-sliver-wolf-<version>`, and atomically writes one `sliver-wolf-device-tools`
entry. The entry refers to a token file, never embeds its value. DSH's existing profile
patch watcher performs HMR. Verify discovery and a real tool call before reporting success.
Do not restart DSH to conceal a failed reload.

To disconnect later, remove only this insert entry from the profile patch list. Restore
the backup only after verifying that no later user changes would be lost. The same
watcher unloads the bridge. Leave the model configuration and DSH deployment intact.

## Operation semantics

`assistant_show` supports only `surface: notification` and a caller-supplied
`operationKey` matching `[A-Za-z0-9._-]{1,80}`. It reserves a durable record before
posting. Repeating the key returns that record without another post; unsupported
arguments are refused before lookup. A crash between reservation and completion leaves
`unknown`, never an assumed success or automatic replay. `assistant_get_operation`
reads the record. A successful notification means posted, not viewed or UI launched.

At most 64 records are retained. New operations prune terminal non-artifact records
older than 24 hours; active operations are not evicted. Artifact records remain while
the artifact is retained. At capacity the store returns an explicit busy error.
Do not assume deduplication after retention expiry.

`assistant_speak` accepts `operationKey`, `text` (1..1000 characters), and optional
`language` (`zh-CN`, the default, or `en-US`). It uses the system-selected installed
TTS engine and fails if language data or the engine is unavailable. Only one speech
operation runs at a time. Android audio focus is requested, loss stops playback,
and completion/error/stop callbacks update the journal. It does not change system
voice preferences or automatically read chat replies. `assistant_stop_speaking` accepts
its own operation key and optional target operation ID. Device tools -> Stop speaking
is a local stop control independent of DSH/model availability.

Before dispatch, the journal saves a parameter fingerprint so a reused key with changed
arguments is refused. Speech text is not persisted there. After process loss unfinished
speech is unknown and never replayed. See [M2 evidence](../../tests/m2-evidence.md).

## Artifact handoff

From Termux, transfer an explicit file without exposing its private path to Android:

```sh
node upload-artifact.mjs /explicit/local/test-file unique-operation-key
```

The helper streams 32 KiB chunks through the authenticated bridge. Android caps each
artifact at 8 MiB, at most eight files and 32 MiB reserved, and checks declared size
and SHA-256 before finalization. It returns an opaque artifact ID, not a filesystem
path. Completed files are inert bytes: there is no extraction, update activation, or
APK installation here. Different bytes at a retried offset are refused. Interrupted
partial files fail and are removed on restart; completed files with a matching digest
can be recovered after a crash between rename and journal completion.

`artifact/discard` with the original operation key removes only that owned artifact.
The verified tests discard their files afterwards. Update staging/activation is M5/M6.

Platform sources: [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech),
[UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener),
[audio focus](https://developer.android.com/media/optimize/audio-focus).

## M4.5 reviewed share reception (adapter 0.2.2)

The adapter now handles assistant_receive_share with receive-share.mjs; other tools
retain their existing forwarding path. The setup helper copies both modules into a
new private version directory and updates only the previously owned MCP entry through
the existing profile watcher. DSH itself is neither restarted nor upgraded.
SLIVER_WOLF_SHARE_DIR identifies private generated batch storage; no caller-supplied
filesystem path is accepted. Tool timeout is 120 seconds for bounded file reception.
The Android review/confirmation releases a batch to one original session; chunk
reads require the existing pairing credential and matching session. Repeated calls
verify existing file hashes; acknowledged app data is kept until explicit deletion.
Controlled fixture checks: node check-received-share.mjs <shareId> <sessionId>.
Only use the test provider normal fixture with this check; it verifies real file
bytes, repeat reception and a rejected wrong-session request.
