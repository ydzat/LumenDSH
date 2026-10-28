# REA on the phone (reverse-engineering tooling)

Status: installed and evaluated, 2026-10-28.

## What it is

[REA](https://github.com/morluto/rea) (`rea-agents` 6.8.0, MIT) is an
agent-facing reverse-engineering CLI/MCP server: APK signatures and resources,
adb device inspection, JavaScript/Electron analysis, native binaries through
Ghidra/Hopper/IDA, and many static format readers.

## Installation here

- Local install: `~/rea-tools` (`npm install rea-agents@6.8.0 --omit=optional`,
  149 packages, no native build failure).
- Wrapper: `~/agent-workspace/rea.sh <command>` (calls the package's
  `scripts/rea.mjs`).
- Node 24.22.1 satisfies the engine range exactly.

## What works on android-arm64 / Termux

| Capability | Result |
| --- | --- |
| `inspect-apk-signature <apk>` | works (signing schemes, signer digests) |
| `inspect-adb-package <serial> <pkg>` | works against `127.0.0.1:5555` (version, install times, flags) |
| `inspect-adb-client` | works (finds Termux adb) |
| `inspect-android-package` (JADX decode) | **unsupported**: "JADX subprocess ownership is unsupported on android/arm64" |
| Native decompilation | unavailable (no Ghidra/Hopper/IDA on the phone) |

`rea doctor` classifies the host as `unsupported_host`; the static readers above
still run. Treat native/JADX paths as unavailable on-device; run those on a Linux
x64 machine if needed.

## Why not register it as a DSH MCP server yet

The MCP server exposes a very large tool catalog (100+ tools). Registering it
would inflate every turn's context and requires a host restart. The CLI wrapper
covers the useful on-device inspections without that cost. Register it later only
if a workflow genuinely needs the MCP ergonomics.

## Verified examples

- `rea.sh inspect-apk-signature ~/Download/lumendsh-web-0.10.48-dev.apk`
  → evidence `ev_0e7a79…`, sha256 `e5eaeca5…`.
- `rea.sh inspect-adb-package 127.0.0.1:5555 local.sliverwolf.web`
  → evidence `ev_6b9911…`, confirms `0.10.48-dev` (104), installed 2026-10-26,
  last update 2026-10-28 01:10.
