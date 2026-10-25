# Local Content Release Tools

See [scope and manifest contract](../../docs/m5-content-releases.md). Run `release.mjs` in Termux with the existing device bridge environment. No automatic activation, remote publishing or embedded build runtime is provided.

`receipts.mjs` extracts `shaBuffer`, `readJson`, and `writeJson` from `selfbuild/scripts/release-receipts.mjs` in https://github.com/guzhou079-arch/deepseek-harness-android at `a5af7b105b7fdb006e3fc3be2a2179d83907c2cb`. Copyright (c) 2026 zhou-gu24, MIT. See LICENSE.upstream. The extraction retains their behavior and replaces only the standalone assertion helper/imports; unrelated compilation, Android archive, fixed-path and publishing logic is excluded. No additional Node package is introduced.

Content status accepts an optional component: `release.mjs status profile`. Stage accepts `stage ID PAYLOAD_JSON RECEIPT_JSON [presentation|profile]`. Obtain canonical profile payloads with `assistant_prepare_profile`; do not reproduce Android float JSON hashes in another runtime. Uploaded Codex-v2 appearances use `assistant_import_appearance` and remain inert until a profile release activates them.

`native.mjs status`, `native.mjs stage APK RECEIPT`, and `native.mjs install` expose the explicit native pipeline. Android installation permission and confirmation still apply. Build/repair commands and tested boundaries are recorded in [M6](../../docs/m6-native-releases.md).
