# DSH Integration

Current deployment: [rc.3 compatibility and real-device evidence](compatibility-rc3.md).
The client has no old-version fallback. rc.7 records and probes below are historical.

This directory owns deployed-version investigation, compatibility notes, sanitized wire fixtures, and adapter work. Runtime placement is determined by verified transport requirements; the preferred client attaches to the independently running local DSH Web Host.

M0 results: [installed rc.7 compatibility matrix](compatibility-rc7.md).
`probe.mjs` is the read-only real-service probe; it needs Node with built-in fetch and
WebSocket and runs inside Termux. It does not contain model credentials or transcript fixtures.

Implement [session contract](../../docs/contracts/session.md). Device tool exposure follows [MCP contract](../../docs/contracts/device-tools.md).

Do not use latest upstream examples as proof that the installed Termux build supports a method. Record exact installed revisions before coding against them. No automatic backend upgrade, second runtime, or shutdown-on-client-exit is permitted as an incidental integration step.
