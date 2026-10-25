# M7 companion integration

Baseline evidence (0.7.42). Current status: Android `0.7.45-companion`; see
[mandatory semantic implementation](m7-semantic-memory.md) and the superseding
[M7 closure and C20-C25 evidence](m7-behavior-policy.md). The earlier baseline was installed as `0.7.42-memory`
(versionCode 42), DSH `0.2.1-rc.3`, dsh-mnemon `0.5.34`, Mnemon CLI `0.2.12`.
The user approved this dependency and deployment. M7's companion implementation is
complete within the audited local scope; deferred human voice checks and historical
limits below are not converted into passing evidence by that closure.

## Inspected reuse

- [dsh-mnemon](https://github.com/omdsh-dev/dsh-mnemon), source revision
  `a7e3bc549c777ada56ca703099eb61a7340b1da3`, package 0.5.34, MIT. Its declared
  DSH engine range includes rc.3. The official bundle composes runtime memory,
  documents, memory spaces and strategies. The deployment evidence below establishes the exercised compatibility.
- [Mnemon CLI](https://github.com/mnemon-dev/mnemon), inspected revision
  `fa74871bb2811b24acb6dfd637abdbd36a12149c`, Apache-2.0. Memory Spaces' native
  provider invokes this separate executable. Its release build matrix lists Linux,
  macOS and Windows, not Android. The official 0.2.12 Linux ARM64 executable was verified on this phone through
  the system-shell wrapper described below.
- [Whale persona](https://github.com/linsport/dsh-whale-persona) uses Mnemon spaces
  and explicit grounded auto-capture guidance. Reuse this arrangement, not its
  identity, desktop Ollama installation or requirement to create a new conversation.
- [Magic Context](https://github.com/cortexkit/magic-context) currently documents
  OpenCode, Pi and OMP adapters. Capture, consolidation and selective recall are
  relevant design references. Its cache-aware historian is not a verified DSH
  adapter; keep DSH's native compaction instead of installing a competing manager.

## Implemented ownership and policy

The pinned upstream bundle provides runtime memory, documents and workspace-local
memory spaces. Git sync and the independent idle-review agent are disabled. Daily
assistance, memory tools and scheduled reflection reuse the selected DSH session.
The initial keyword-only baseline is superseded by the local semantic deployment. DSH keeps its native context compaction; Magic Context is a design reference.

Original persona instructions in `integrations/companion/persona.md` are appended
as a marked section to the existing workspace's `AGENTS.local.md`, preserving other
instructions. Standard presets shadow the global persona prefix, so that prefix is
not the integration mechanism. The same existing controlled session loaded these
instructions and identified itself as Silver Wolf without losing its memory tools.

The Android memory page calls the authenticated `dshMnemon/read` API. It binds the
selected session's cwd to a workspace ID before reading status, runtime entries or
long-term records. This matters after restart when an unloaded session cannot resolve
its workspace itself. Reads are selectable and refreshable; late responses from a
previous selection are discarded. Main-workspace isolation passed a real Java check.
The upstream grant is `trusted-host` for paired local management and initial space
provisioning; the Android memory page itself exposes reads, not arbitrary management.

The memory-organization preset uses the existing local scheduler, defaults to once,
and supports pause/cancel/resume. Recurring companion tasks require at least 24 hours.
A persisted global reservation limits companion submissions to one per rolling 24
hours, including uncertain receipts. Busy sessions are skipped. This is an enforced
submission budget; the one-write instruction is prompt policy, not a tool-call or
token quota. No new daemon, separate identity, or automatic external social posting
is introduced. Habitat memory indicators derive from actual Mnemon tool events.

## Reproducibility and provenance

See [installation and activation](../integrations/companion/README.md). The official
CLI archive is verified before extraction. Termux's execution shim initially routed
the static ELF through Android's linker and failed with `unexpected e_type: 2`.
`mnemon-termux` clears `LD_PRELOAD` and executes it through `/system/bin/sh`.
The official binary then performed actual writes and reads; it was not rebuilt.
See the [Termux execution environment](https://github.com/termux/termux-packages/wiki/Termux-execution-environment).

- CLI archive SHA-256: `f0324a637ab47394f8fcd5bdb3a4f36adcaa40db7b89b496877337928ef1db31`.
- CLI binary SHA-256: `1ba0686c627d49fbb2ea5a8dd95b680fe4f6948c44532a70ca243d6fda265962`.
- Active profile pnpm lock SHA-256: `f3c0bc1c5b1750818a8d8fcf6250b1150cabd3d9eb9cd4d610f828d4d079ecb9`.
- APK SHA-256: `3200e0e9a680fb378e0ffc06d2b97baef156ced52caa6f8aa56a8437699e4435`.
- Signing certificate SHA-256: `4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.

Runtime, private receipts and isolated test data stay under the phone's
`~/sliver-wolf-companion/`; production memory stays in `~/agent-workspace/.mnemon`.
Keys, cookies, transcripts, models and APKs are not tracked. No old DSH runtime is
retained and no project source or artifact was published.

## Consolidated gate

| ID | Observed result | Remaining scope |
| --- | --- | --- |
| C14 | Isolated and active plugin report 0.5.34, healthy, serving and CLI found; production restart retains sessions | No claim for other devices |
| C15 | Real model writes runtime and long-term facts; independent Java read finds durable fact after restart; model recalls it with mnemon_recall | Now verified in the semantic follow-up |
| C16 | Same controlled session loads original persona and retains tools | Long-duration personality consistency not measured |
| C17 | Actual UI saves reflection, pauses past due without delivery, resumes; Android background delivery adds exactly one turn at 13:36; model checks memory and records one grounded sandbox fact | Busy skip and daily-budget denial branches inspected, not independently fault-injected |
| C18 | Installed Android status and records show actual API data; Java checks workspace isolation after restart | Unavailable-service UI implemented, no new outage injection |
| C19 | Real runtime-memory correction purple to cyan leaves one entry; forgetting removes the test marker; restart confirms deletion | Broader unrelated-query and long-term forgetting quality not evaluated |

Phone checks passed: ConversationChecks (10), HabitatChecks, NotificationArchiveChecks,
ScheduleStoreChecks (12), voice reply correlation (5), and MemoryLiveChecks
(`M7_JAVA_STATUS_DURABLE_RESTART_FORGET_ISOLATION_OK`). These checks do not establish
acoustic performance. A stale HabitatChecks expectation for two removed placeholder
phrases was corrected to the approved zero default; legacy weighting uses an explicit
legacy fixture. Full phone compilation, signing, APK hash verification and in-place
installation passed. Existing user data and owner voice enrollment were preserved.

The controlled reflection runs in a separate test workspace only for verification;
production reflection uses the user's existing selected session. It is not an
architectural requirement to create a development agent or a new personal session.

## Feature demonstration

[Real memory page and habitat, approximately 9 seconds, no audio](../artifacts/demos/20261025-133321-m7-real-memory-66c66641.mp4).
The screen was inspected and contains only controlled test records. Video is not a
substitute for acceptance. Deferred V03/V04 and human owner/non-owner acoustic checks
retain their previous status; they do not become passed because this stage installed.
