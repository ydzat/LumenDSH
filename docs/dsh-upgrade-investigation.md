# DSH upgrade investigation

Date: 2026-10-25. Status: production upgrade and Android client migration completed.
See [current compatibility and evidence](../integrations/dsh/compatibility-rc3.md).
The investigation stages below are dated findings, not instructions to retain rc.7.

## Direction and scope

The user prefers a current upstream DSH and current plugins, rather than selecting
plugins around the initial community-patched rc.7 deployment. Investigate and solve
phone compatibility first. This is explicit backend-upgrade work, not an incidental
backend change during client development.

Candidate installation, a fresh DSH home, and a controlled workspace are isolated at
`~/sliver-wolf-dsh-upgrade/0.2.1-rc.3/`. The active installation, home, conversation,
credentials and port 3080 are not migration test inputs. No credential material is
printed or copied. A successful fresh startup alone will not establish migration or
Android-client protocol compatibility.

## Direct observations

- npm registry tags queried from the phone: `latest=0.2.1-rc.2`,
  `next=0.2.1-rc.3`, `alpha=0.2.1-alpha.5`.
- First candidate: `0.2.1-rc.3`; use rc.2 for comparison if a regression appears.
- Phone: Android API 29, arm64; Termux Node `v24.22.1`, npm `11.21.1`.
- Node's built-in SQLite opens and closes an in-memory database successfully.
- About 98 GB available on the private-data filesystem during inspection.
- Existing DSH HTTP root returned 200 before candidate installation.
- A controlled hard-link operation inside the candidate workspace fails `EACCES`.
- Directory open/fsync succeeds inside that workspace but fails `EACCES` at
  `/data/data`. These are real Android restrictions, not hypothetical failures.

## Source findings

- The rc.3 npm subprocess package lazily loads node-pty and koffi; the attachment
  package lazily loads sharp. Old eager-import patches must not be carried over
  without checking whether current code still needs them.
- The rc.3 attachment package still contains hard-link publication paths. The
  controlled hard-link failure warrants exercising these paths before migration.
- Community installer `lilyco-42/dsh-termux` targets Android 11+ by building koffi
  for API 30. That recipe is not directly suitable for this API-29 phone.
- `ChongYep/Deepseek-harness-mobile` documents rc.7 Android filesystem/native-addon
  adaptations. Reuse their intent where necessary, not their old package versions.

## Sources

- [Official CLI](https://github.com/deepseek-ai/deepseek-harness/tree/master/apps/cli)
- [Official npm metadata](https://registry.npmjs.org/@deepseek-ai/dsh/latest)
- [Termux discussion](https://github.com/deepseek-ai/deepseek-harness/discussions/136)
- [Termux installer](https://github.com/lilyco-42/dsh-termux)
- [Android distribution](https://github.com/ChongYep/Deepseek-harness-mobile)

## Gates identified during isolated investigation

Assess existing session-format migration, current client RPC/events/approvals, MCP
integration and an independently recoverable cutover. Exercise a real configured
model after migration configuration is prepared; this investigation did not copy
or read the existing model credentials and did not establish model completion.
Do not treat old S01-S05 evidence as proof for the new runtime. No memory plugin
has been installed by this investigation.

## Reproduced failures and verified adaptations

| Component | Actual failure | Candidate adaptation and observation |
| --- | --- | --- |
| Host preparation | Missing `node-addon-require-builtin-android-arm64`; startup exited 1 even with `--expose-internals` | App boot now uses the same exposed-internals approach already present in upstream HMR/loader. Authenticated HTTP root returns 200. |
| Session writer lock | `ERR_FLOCK_UNSUPPORTED_PLATFORM` for android-arm64; accepted in-memory sessions disappeared on restart | Compile shipped `@deepseek-ai/node-addon-system@0.1.2` C implementation; add Android loading branch. Actual acquire, conflicting second descriptor, close/reacquire pass. No lock bypass. |
| Session publication | Source still requires forbidden hard-link publication | Exclusive-copy Android fallback plus target fsync. A controlled offline prompt and title now survive process shutdown/restart and appear as current format. |
| Filesystem tool backend | `LocalFileSystem.writeText(createIfAbsent)` failed `FS_IO_ERROR` caused by hard-link `EACCES` | Reuse existing Android port's exclusive-copy pattern. Actual create/read passes; second create refuses overwrite and original bytes remain. |
| Images | `sharp@0.35.5` cannot load android-arm64 binding | Install exact official `@img/sharp-wasm32@0.35.5` inside candidate. Encode/decode a controlled PNG succeeds. |
| Attachment persistence | `saveFile` fails opening `/data/data`; hard-link publication is also unavailable | Bound directory durability walk to Termux HOME when candidate DSH_HOME is its descendant; use exclusive-copy publication with target fsync. File save/read and image save/read/dedup pass. |
| PTY | No android-arm64 `pty.node` | Rebuild existing `node-pty@1.2.0-beta.15` using phone headers and a per-command GYP definition. Actual PTY Bash prints the expected marker and exits 0. |

The unmodified current `LocalSubprocessRuntime` also executes Bash, captures the
expected output and exits 0. No koffi/API-30 rebuild was necessary for that tested
path. This does not establish every optional native-code path.

The exclusive-copy adaptation preserves no-overwrite behavior but does not retain
hard-link publication's instantaneous visibility. Interrupted copy/crash recovery
is not validated here. Keep that durability difference visible in the migration
review rather than claiming an identical atomicity guarantee.

## Client protocol findings

- Anonymous root access is now 401. A launch-token exchange gives an authenticated
  cookie and the same root returns 200. Test credentials remain in candidate memory;
  they are not a credential migration mechanism.
- Current session RPC uses `/api/session/list`, with the existing outer
  `client-request` envelope but a new `payload.args` object and exact parameter
  names (`_request` for list, `request` for create/rename/prompt).
- Current create, rename, prompt acceptance and list were exercised against the
  isolated runtime. Prompt acceptance is not model execution or durable completion.
- The old Android client's `/api/session.list` returns 404 even when authenticated.
- Published source uses `/api/remote.mux`, Session follow/page and different decision
  delivery. Streams and approvals require a deliberate adapter migration, not a
  URL-only change or assumptions from the rc.7 record.
- Source includes session-format migration packages v0->v1->v2->v3->v4. Their
  presence is not proof that this user's old conversation migrates successfully.

Current `dsh-mnemon@0.5.34` metadata accepts the rc.3 version range. Actual plugin
installation and Android compatibility remain untested. Select current memory
plugins after validating the backend, not by retaining rc.7 as a permanent limit.

## Reproduction and provenance

Scripts are in [tooling/dsh-upgrade](../tooling/dsh-upgrade/README.md). Candidate
packages were installed from npm with scripts initially disabled, then only the
required PTY build was run. No global Node headers, installed DSH files, active
profiles, user sessions or startup helpers were modified.

- DSH rc.3 tarball integrity:
  `sha512-H+E9HLnHf7fJqzen84Z9Bq8ggb5xbrcbyhZjXTJLE9NabHr3zteGvTRvdvGkxPl4ov80is4WLglFmSo+/olJjg==`.
- Upstream flock C SHA-256:
  `c1aa773c7a62b03ce3973156b928f41773fef9407afd79d71b2f625b5616421f`.
- Phone-built flock binary SHA-256:
  `23088d7c148231572547c8ff39494a16b31a258d4d233499443b20c569d2ee86`.
- Each patch checks its exact source anchor, retains the upstream file and prints
  before/after SHA-256 receipts. It refuses independently changed candidate files.
- Upstream DSH JavaScript adaptations: MIT; shipped system/flock C: BSD-3-Clause.
  Original package notices remain with the candidate; no binaries enter Git.

At the end of the isolated investigation, the original service still returned HTTP
200 on port 3080 and candidate servers stopped after each probe. The subsequent
production migration is recorded below. M7 memory integration remains incomplete.

## Production migration result

Both original sessions retained their IDs, migrated to current format, and survived
a production restart. Migration publication exposed another Android hard-link
restriction in `internals.fs.link(staged, currentPath)`; the same exclusive-copy
and fsync adaptation fixed it. The session-persistence patch SHA-256 is now
`ab96e33c05a7972a9ad0f8fec605b5b2d2f749d819db1d7511a28d685f61f9df`.
Real model text, one-time command approval, ordinary questions and MCP status
completed through the new Java adapter. The old runtime/profile dependencies and
client fallback were removed by explicit user direction. Memory plugins remain
uninstalled. The default command-sandbox limitation and all unverified scopes are
listed in the current compatibility record.
