# Data boundaries and publication review

| Data | Git treatment |
| --- | --- |
| Source, manifests, schemas, generic defaults | Track after review |
| Dependency pins/hashes/licenses and synthetic fixtures | Track with attribution |
| Reviewed design assets and logo concepts | Trackable project data; drafts are not installed icons |
| Provider keys, cookies, pairing tokens, actual connection configuration | Private and ignored |
| Personal conversations, memories, diary, notifications and shared content | Private and ignored |
| Owner voiceprints, reference speech and microphone recordings | Private and ignored |
| Signing private keys/passwords, app-data backups, device dumps | Private and ignored |
| APK/DEX/build outputs and downloaded tools/models | Ignored generated data, not inherently personal |
| Screenshots/videos/logs, including controlled demonstrations | Ignored by default; review before publication |
| Public certificate fingerprints and dependency hashes | Non-secret reproducibility data |

Use ignored `.local/`, `private/`, `personal/`, `runtime-data/` and `artifacts/`
for local-only material. Existing phone data stays in Android private storage and
Termux; this handoff does not copy, delete or relocate it. An exported live profile
is not a reusable example merely because it is JSON. Examples must use placeholders
or deliberately synthetic data.

Do not blanket-ignore JSON, Markdown or audio extensions: schemas, design documents
and licensed fixtures can be project data. Conversely, file format alone cannot
identify personal content. The code's MIT license does not license downloaded
character art, model weights or voice assets under MIT.

## Review performed on 2026-10-25

Tracked text and historical patches were searched for private-key blocks, long
quoted credential assignments, personal Windows paths, device serials, private LAN
addresses and concrete session identifiers. No private-key block or long quoted
credential assignment matched the limited patterns. This is a bounded review,
not proof that every secret or personal fact has been detected.

Working documentation contained a phone serial and old private LAN addresses;
these were replaced with `<USB_SERIAL>` and `<PHONE_IP>`. Generic loopback endpoints
and Termux package paths describe the architecture and remain. The remote MIT
copyright line is retained verbatim as explicitly supplied by the repository owner.

**Historical commits still contain old development identifiers.** No history was
rewritten and nothing was pushed. `.gitignore` does not untrack existing files or
erase historical objects. Before publishing, review the entire outgoing history
and explicitly choose a clean import or appropriate rewrite. Keep the remote's
existing license/history intact; do not force-push without authorization.

## Current authorization

The user authorized the reviewed clean-snapshot commit and push on 2026-10-25,
preserving the remote MIT-license commit and keeping local development history
private. No force-push or binary release is included. The user separately published the demonstration on
[Bilibili](https://www.bilibili.com/video/BV1AknU6hEyh/); README links to that video,
while raw footage and local edits remain excluded from Git.

`origin`: `git@github.com:ydzat/LumenDSH.git`. LICENSE was
fetched from remote commit `b3c7eed` and is byte-identical to `origin/main:LICENSE`.

Useful read-only checks before staging:

```sh
git status --short
git diff --check
git ls-files
git check-ignore -v private/example.json artifacts/example.mp4 .env
git diff --cached --name-only
```

Review contents and outgoing history as well as filenames. Never paste private
credentials or user content into public issues, screenshots or validation reports.

## Pre-publication file review (2026-10-25)

Reviewed the 250 tracked/unignored candidate files and text blobs reachable from
all 39 local commits. The scan checked private-key headers, common token formats,
long quoted credential assignments, the development handset serial, private LAN
addresses, personal host paths and concrete DSH session UUIDs. It found no credential
pattern matches. This pattern scan complements file review; it does not identify
every possible form of personal information.

- Removed the remaining concrete host workspace path from `environment.md`.
- Extended exclusions to video files outside `artifacts/`, compiled Java/native
  libraries, Python bytecode, SQLite sidecars, screenshots and device-dump directories.
- No tracked file currently matches the ignore rules. The demo and local editing
  script are ignored. No APK, downloaded model, recording or private runtime export
  appears in the candidate file list.
- The only binary candidates are the three requested logo concept PNGs, about
  4.3 MiB together. They are project design material, not screenshots or personal data.
  Keep their design README and preview alongside them.
- Retain source, tests, schemas, dependency download/checksum scripts, attribution
  and licenses. Default pet artwork and wake models are downloaded by the existing
  preparation scripts, not committed as binary payloads.
- English and Chinese README files now acknowledge reused implementations separately
  from design references. Full component licenses remain in the existing notices.

Historical matches remain in six paths: `docs/environment.md`,
`docs/wireless-debugging.md`, `tests/m0-evidence.md`, `tests/m1-evidence.md`,
`tests/m2-evidence.md` and `tooling/android-build/README.md`. They contain old handset,
LAN or host-path identifiers. Current-file edits do not remove those historical blobs.

Recommended first-publication strategy: retain the existing development repository
and its history locally; prepare a clean snapshot on top of the remote's existing
MIT-license commit in a separate checkout. Publish only the reviewed snapshot and
the remote history. This preserves the remote license commit, requires no force-push,
and leaves the private development history intact. Do not merge the old local graph
into the publication branch. The authorized publication checkout is
`.local/publication-lumendsh`; use its clean `main` ancestry for publication.
