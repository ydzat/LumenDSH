# Project Instructions

## Communication

- Discuss the project in Chinese by default.
- Write source code, comments, README files, and technical documentation in English.
- Preserve the project directory spelling `sliver-wolf` unless the user requests a rename.

## Architectural constraints

- DSH runs independently in Termux. Do not bundle, replace, restart, or upgrade that deployment as a side effect of client development.
- Initial scope is phone-local DSH only. Remote DSH is a future milestone, not an initial implementation requirement.
- Daily assistance and self-improvement belong to one agent identity, conversation, and memory. Do not require a separate self-improvement agent or conversation; isolate candidate artifacts from the active release instead.
- Prefer versioned patches and in-place upgrades. Distinguish hot activation, component reload, and Android APK updates; do not promise arbitrary native-code hot replacement.
- Keep build tooling independent of the assistant APK.
- Prefer maintained upstream implementations. Record their source, revision, license, and local changes before importing code or assets.
- Treat character artwork and voice assets separately from source-code licensing.
- Do not equate an OpenAI-compatible model endpoint with a DSH agent/session interface.
- Preserve server-owned conversations and approval semantics. Reconnection must not silently create a new conversation or duplicate a submitted task.
- Background operation, wake-word recognition, and screen presentation require real Android validation; desktop tests are not proof.

## Working practices

- The user authorized publishing a reviewed clean source snapshot to `git@github.com:ydzat/LumenDSH.git` on 2026-10-25, based on the remote's existing MIT-license commit. Keep the original development history private; do not merge or push it into the public branch. This authorization covers the initial source commit and normal push, not APK uploads, force-pushes or future releases. Keep personal data and generated artifacts excluded.
- For phone debugging, read `docs/wireless-debugging.md`. Use explicit network device selection; do not assume USB is connected or that the last recorded IP is current.

- Read `docs/README.md`, `docs/architecture.md`, and `docs/decisions.md` before non-trivial changes. Read the affected contract under `docs/contracts/` before implementation.
- Follow milestone gates in `docs/development-plan.md`; report relevant case IDs from `tests/acceptance.md`. Latest upstream APIs are not proof of support in the installed DSH build.
- MCP device tools and DSH session APIs have distinct directions and responsibilities. Treat Termux/App artifact transfer as an explicit authenticated boundary.
- Keep proposed designs distinguishable from implemented and verified behavior.
- Do not commit API keys, authentication tokens, signing keys, user conversations, recordings, private device logs, or personal reference audio.
- Keep APKs, downloaded SDKs, runtimes, models, and generated build outputs out of Git.
- Use non-destructive device checks and preserve the existing phone setup.
- Choose the smallest relevant verification. Do not add placeholder tests or mocks as evidence of device functionality.
- Milestone completion hook: after each substantial completed stage, record a short (about 5-10 seconds) real-device feature demonstration. Use only a controlled test conversation; inspect the screen first and exclude credentials, personal messages, and unrelated notifications. Save videos under ignored `artifacts/demos/`, link the local artifact in the delivery, and state whether audio is captured. A demo does not replace acceptance evidence. If recording is unavailable or would expose private content, record the reason instead of claiming a video was produced. See `tooling/android-build/record-demo.py`.

- Development cadence: batch implementation across a major stage before running its consolidated tests, phone build/install, acceptance and demo. During implementation, use necessary compilation checks; do not rebuild/reinstall or record a demo for each small edit. Keep unverified changes labeled until the stage gate runs.
