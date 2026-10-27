# Development Baseline

Current handoff: **M10 autonomous iterative development (0.10.x) active**. M9 delivered the supervisor, relay + A/B slots, boot persistence and the situation-driven loop; M10 starts with the user's requirements. See [M10 scope](m10-autonomous-development.md), [M9 design and closure](m9-self-iteration.md) and the [self-development loop](self-iteration.md).
Connection diagnostics and App-driven DSH startup: [0.8.1 guide](connection-diagnostics.md).
See [M8 scope](m8-voice-providers.md), [device evidence](../tests/m8-evidence.md)
and [provider setup](../integrations/voice/providers.md).
See [project status](project-status.md), [English introduction](../README.md),
[Chinese introduction](../README.zh-CN.md) and [data boundaries](data-and-publication.md).
The approved [settings drawer and interaction redesign](design/settings-drawer.md) is implemented in 0.7.49. Further visual polish and validation remain future work.

Current backend/client (2026-10-26): DSH `0.2.1-rc.3` and Android `0.8.3-dev` (55).
The user resumed character work after this baseline:
[Silver Wolf persona revision 2 and character voice investigation](silver-wolf-persona.md).
The accepted voice is now integrated through [phone-local character speech](character-voice.md).
Current model-aware adaptation and C26 evidence: [model configuration](model-configuration.md).
The explicitly authorized upgrade is installed; the old runtime and compatibility
branch are removed. Original conversations are retained. See the
[current compatibility and evidence](../integrations/dsh/compatibility-rc3.md).
M7 is complete within the audited phone-local scope. See
[M7 closure and evidence limits](m7-behavior-policy.md) and
[the reconciled R01-R20 requirements](full-requirements-audit.md).

Baseline date: 2026-10-24. Status: M0 build discovery and M1 conversation baseline
verified on the phone. See [M0 evidence](../tests/m0-evidence.md) and
[M1 evidence and limits](../tests/m1-evidence.md). M2 device tools, explicit speech,
recovery, and artifact handoff are [verified within D01-D04 scope](../tests/m2-evidence.md).

## Reading order

1. [Decisions](decisions.md): agreed scope and remaining technical choices.
2. [Architecture](architecture.md): responsibility and state ownership.
   [Web-first consolidation](design/web-first-consolidation.md): how the embedded WebUI, the native habitat and the official pages divide responsibility in 0.10.x.
3. Contracts: [session](contracts/session.md), [device tools](contracts/device-tools.md), [assistant packages](contracts/assistant-packages.md), [updates](contracts/updates.md).
4. [Development plan](development-plan.md): ordered work and milestone gates.
5. [Acceptance scenarios](../tests/acceptance.md): evidence needed before claiming a feature works.
6. [Environment](environment.md) and [build tooling](../tooling/android-build/README.md): starting conditions.
7. [Wireless debugging](wireless-debugging.md): verified Wi-Fi ADB workflow, recovery, and transport selection.
8. [Research](research.md): upstream candidates and evidence limits.

## Meaning of status

- **Accepted direction:** approved architectural choice, not proof of runtime support.
- **Contract v0.1:** proposed internal implementation boundary; does not claim to be an upstream API or external standard.
- **Verified:** accompanied by a real observation, command result, or device test.
- **Open:** a decision assigned to a milestone; resolve before dependent implementation.

## Non-negotiable baseline

One agent handles daily work and self-improvement. DSH stays independently deployed in Termux. First-stage connectivity is local to the phone. The APK does not bundle DSH or the build toolchain. Candidate releases are isolated from the active release, not from the agent's identity or conversation.

Resources/configuration should be reloadable. Native changes use a signed in-place APK upgrade. Neither general native hot-fixing nor remote onboarding is required for the first release.

## Start here

The current M4 direction is the [attention-aware agent habitat](design/m4-attention.md).
See [personality, memory and proactive behavior](companion-behavior.md) for the newly
specified companion integration track and the limits of community-plugin research.

M4.5 is [complete by explicit user acceptance](m4.5-completion.md) on 2026-10-25 at 0.4.27. Current visuals are accepted; the user waived remaining milestone tests, which remain unverified rather than passed. M5/M6 are complete within their recorded gates; M7 is next in the [development plan](development-plan.md). Use the inspected installed
DSH protocol rather than assuming compatibility with latest upstream documentation.
A request to continue implementation authorizes work toward these milestones, not
silent replacement of the existing DSH deployment.

## Reuse audit

Read [the Android reference reuse audit](reuse-audit.md) and [M4.5 gate](m4.5-completion.md) before the next implementation batch.
It identifies overlooked character UI and plugin implementations, distinguishes
necessary local adaptation from avoidable duplication, and orders concrete extractions.

- [M5 content releases](m5-content-releases.md): presentation/profile release lifecycle, reuse and U01-U04 evidence.

- [M6 native releases](m6-native-releases.md): phone builds, signing, system installation, repair and B02/U05 evidence.

- [Full requirements and reuse audit](full-requirements-audit.md): R01-R20 completion checklist and current upstream reuse.
- [Companion behavior integration](m7-behavior-policy.md): configuration, activity budgets and C20-C25 verification.
