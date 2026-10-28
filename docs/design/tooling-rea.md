# REA on the phone (reverse-engineering tooling)

Status: installed and evaluated, 2026-10-28.

## What it is

[REA](https://github.com/morluto/rea) (`rea-agents` 6.8.0, MIT) is an
agent-facing reverse-engineering CLI/MCP server: APK signatures and resources,
adb device inspection, JavaScript/Electron analysis, native binaries through
Ghidra/Hopper/IDA, and many static format readers.

## Installation

- **Server (full version)**: `npm install -g rea-agents@6.8.0` on the Debian
  server (x86_64, node 22.23.4). JADX headless 0.7.1 from
  [jadx-headless-mcp](https://github.com/1013503897/jadx-headless-mcp) at
  `/opt/rea/jadx-headless-mcp-0.7.1-all.jar` (`REA_JADX_MCP_JAR`); Ghidra
  12.1.4 at `/opt/ghidra_12.1.4_PUBLIC` (`GHIDRA_INSTALL_DIR`); Temurin JDK 21
  at `/opt/java21/jdk-21.0.13+9` (`JAVA_HOME`). All three in `/etc/environment`.
- **Phone (limited)**: `~/rea-tools` (`npm install rea-agents@6.8.0
  --omit=optional`), wrapper `~/agent-workspace/rea.sh <command>`. Node 24.22.1
  satisfies the engine range exactly.
- **DSH skill**: `~/.dsh/skills/rea-reverse-engineering/SKILL.md` (repo copy at
  `integrations/skills/rea-reverse-engineering/SKILL.md`), discovered and loaded
  by the agent's skill tool.

## What works on android-arm64 / Termux

| Capability | Result |
| --- | --- |
| `inspect-apk-signature <apk>` | works (signing schemes, signer digests) |
| `inspect-adb-package <serial> <pkg>` | works against `127.0.0.1:5555` (version, install times, flags) |
| `inspect-adb-client` | works (finds Termux adb) |
| `inspect-android-package` (JADX decode) | **unsupported on the phone**, works on the server (jadx 0.7.1) |
| Native decompilation | unavailable on the phone; Ghidra 12.1.4 works on the server |

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


## Full-version verification (2026-10-28)

- `rea inspect-android-package /tmp/lumendsh.apk` on the server → evidence
  `ev_61eef4…`, provider `jadx` 0.7.1, sha256 `e5eaeca5…` (our 0.10.48 APK).
- `rea analyze /bin/true` on the server → evidence `ev_233442…`, provider
  `ghidra`, ELF x86_64.
- `rea doctor` on the server: host/node/jadx/ghidra checks healthy; Hopper (macOS)
  and IDA (commercial) remain unavailable by design.


## Agent tool (2026-10-28)

`rea` is registered as a companion tool restricted to read-only commands and
allowed roots; it uploads the file with `scp`, runs `ssh server rea …` and returns
the evidence, truncating long output. Develop and browse budgets admit it, and
each call is appended to `behavior/activity-audit.jsonl`.

Verified from the agent: `inspect-android-package` on the 0.10.48 APK returned
`ev_7747c8…` with the full manifest (package, version 104, permissions, exported
components) through JADX 0.7.1 on the server.
