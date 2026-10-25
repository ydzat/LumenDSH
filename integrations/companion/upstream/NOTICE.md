# Reused companion code

`night-plan.mjs` is copied unchanged from Alterssss/dsh-bili-whale revision
`8b0009f8ce2ebdc421afcb2e727b19ac18912ec6`, `tools/lib/night-plan.mjs`.
Copyright (c) 2026 ALTERSSSS, MIT; full license is adjacent.

Only the pure scheduling planner is imported. Host admission, selected-session
binding and local work policy are project integration code. No Bilibili API,
credentials, social posting, dedicated-session creation or rotation is imported.
The original upstream comments and diagnostic text are retained with the copy.
# Additional integration references

The lifecycle admission pattern in `host.mjs` follows the Android reference's
`dsh-plugin-auto-summary-policy/lib/index.js` at revision
`a5af7b105b7fdb006e3fc3be2a2179d83907c2cb`, using the installed rc.3 contracts.
No upstream compaction implementation is replaced.

Memory tool names/operation classifications and Android activity-field handling
follow dsh-mnemon 0.5.34 (`src/host/memory-tools.ts`, `activity.ts` and
`activity-presentation.ts`), MIT, revision
`a7e3bc549c777ada56ca703099eb61a7340b1da3`. The Android implementation retains
call/result correlation and source IDs; it does not expose private reasoning.
