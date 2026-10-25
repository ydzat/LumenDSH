# Phone-local companion integration

DSH remains an independent service. This integration reuses `dsh-mnemon@0.5.34`
(MIT) and official Mnemon CLI 0.2.12 (Apache-2.0). Source revisions, checksums,
device evidence and open gates are in [M7](../../docs/m7-companion-integration.md).
The unchanged Whale planner and its license are retained under `upstream/`. Keep downloaded packages,
runtime locks and memory databases in private Termux storage, outside Git.

## Preparation and activation

Run in the inspected Termux environment with DSH rc.3 already installed:

1. `bash install-runtime.sh` prepares the pinned CLI and isolated plugin profile.
   `install-semantic.sh` installs the approved local embedding runtime, verifies
   the official model and Mnemon source archives, applies the small semantic
   patch, builds on the phone, and runs real retrieval checks before activation.
2. Run `node prepare-profile.mjs PACKAGE_ROOT OUTPUT ABSOLUTE_CLI_WRAPPER` using
   the isolated profile's installed `dsh-mnemon` directory. For the deployed
   activation script, `OUTPUT` is `~/sliver-wolf-companion/prepared-v2`.
   Existing outputs are refused. Copy the prepared patch into the isolated web
   profile's `cordis.patch.yml`, then run `node probe.mjs`.
3. `activate.mjs` is the explicit one-time backend activation. It requires an idle
   current service and a successful probe receipt, saves profile configuration,
   installs the fixed plugin via DSH's own manager, applies the prepared overrides,
   adds a marked original-persona section to workspace instructions and restarts
   the same DSH version. It does not copy model credentials or replace sessions.
4. Provision a local Mnemon Native space through the authenticated plugin's
   `dshMnemon/write` `body-create` endpoint. Bind `workspaceId` explicitly. The
   deployed owner space is `银狼的共同经历`. An existing space is updated by ID,
   not recreated. Never use controlled test facts in the owner's space.

`remoteAccess: trusted-host` permits management with the existing paired DSH owner
cookie on loopback. No remote listener, Git sync or third-party provider connection
is enabled. The Android UI currently uses read operations only; normal agent tools
perform conversational memory writes, corrections and forgetting.

The official default idle-review agent is disabled. The earlier baseline scheduled reflection used the
existing Android schedule registry and the current agent conversation, with an
enforced shared one-trigger-per-24-hour budget and busy-session skip. A turn may
still use several tools; this is not a token ceiling or a restriction on ordinary
user-requested memory operations. The schedule editor requires explicit save and
does not silently create ongoing background activities.

## Verification

`probe.mjs` uses a fresh profile and no model credentials. `live-check.mjs` uses
the real authenticated model in a controlled workspace; `remember` explicitly
creates the test session, later modes reuse it. It can incur model usage.
`MemoryLiveChecks.java` exercises the production Android transport, durable
readback after restart, deletion and isolation from the original workspace.
Its minimal SharedPreferences provider supplies the phone-private pairing values;
the DSH service, memory store and model are real.

The native app displays actual memory records and committed tool receipts. It does
not display private reasoning or invent a memory timeline. Semantic embeddings use local Qwen3-Embedding-0.6B Q8 through llama.cpp.
See [semantic deployment and exact evidence](../../docs/m7-semantic-memory.md).
Existing installations run `install-semantic.sh` then `activate-semantic.mjs`;
new installations continue through the prepared profile and activation above.


## Current autonomous host and semantic Documents

Host 0.2.1 adds `model-config.mjs`, reusing the installed native exact-model and
selection resolvers. Its authenticated model read powers client 0.7.46 and every
autonomous request resolves its current model default before applying policy caps.
Persisted override receipts prevent those caps from leaking into ordinary requests
through DSH's durable request header. See `docs/model-configuration.md` for limits
and the scoped real-model selection check; that check restores the original
profile override and requires an idle-gated host reload afterward.

`activate-host.mjs` installs this immutable local host plugin via DSH's manager,
with an idle gate, rollback backup, session preservation and exact document-scope
checks. `host.mjs` reuses the Whale planner for versioned local activity policy,
selected-session execution and code-enforced pause/budgets. It supersedes the
baseline reflection timer as the autonomous policy owner; the existing Android
scheduler remains for explicit reminders. Policy activation is explicit in the
client settings page and defaults to disabled.

`documents.mjs` wraps the installed official Source SDK and keeps its original
starter identity and authoritative storage. `document-index-worker.mjs` reuses
native Mnemon/Qwen for bounded paragraph-aware chunks; semantic passages retain
source provenance and pinned grants. Index checkpoints, revisions and chunking
versions prevent stale evidence. Index data stays beside the native documents.

Run `host-check.mjs --documents` for actual isolated DSH semantic/cache/revision/
workspace checks, `document-source-check.mjs` for real controller/model grant and
passage-budget checks, and `context-check.mjs` only against the existing controlled
session for native compaction and durable source preservation. These checks do not
need or expose a second provider credential. `activity-check.mjs` exercises actual
scheduled work and restores the original policy. `host-check.mjs --crash` followed
by `--recovery` injects a process exit in the isolated host, never production.
