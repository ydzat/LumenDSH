# Project status and handoff

Date: 2026-10-26. Current installation: Android 0.8.3-dev (55), independent DSH
0.2.1-rc.3 and the companion host revision `cadb777`.

M0–M7 and model adaptation are delivered within their recorded scopes. The user
subsequently resumed character work: persona revision 2 and the accepted Silver
Wolf voice are integrated. The local engine passed real Android playback, duplicate
suppression, stop and service recovery checks. See [character speech](character-voice.md)
for deployment, cold latency and acceptance limits. M8 delivers explicit local/remote speech providers, encrypted configuration,
model/voice discovery and shared foreground/background playback. See
[M8 scope and limits](m8-voice-providers.md) and [evidence](../tests/m8-evidence.md).
The installed selection is remote Silver Wolf; system and local speech remain available.

The approved settings redesign adds a grouped side drawer, independent home/chat
input and reply policies, foreground keyword mode using the existing owner-gated
service, and native configuration controls. See [design](design/settings-drawer.md)
and [scoped device evidence](../tests/settings-evidence.md).

Foreground keyword listening now yields to automatic reply speech and resumes
after playback; the failure was reproduced on 0.7.49 and the same real DSH/device
check passed on 0.7.50. See [handoff evidence](../tests/voice-reply-handoff-evidence.md).

## M9 self-iteration infrastructure

M9 is delivered: supervised relay + A/B slots on the internal `127.0.0.2`
loopback with a single client entrance at `127.0.0.1:3080`, slot-based host
upgrades with verification/soak/rollback, boot persistence through Termux:Boot,
and the situation-driven self-development loop (experience stream, candidates
with quiet mode, growth stages, self document, `extend_self`). The loop is
currently paused by the user. See [M9 design and status](m9-self-iteration.md)
and [self-development loop](self-iteration.md).

## Future directions, not scheduled milestones

- Further visual consistency and interaction refinement.
- Memory/personality quality under real daily use and larger-library performance.
- Further device compatibility and remaining acoustic/lifecycle validation.
- Separately chosen extensions such as remote access or additional resource formats.

These directions do not authorize further automatic implementation. Waived and
unexecuted tests retain their status. See [the plan](development-plan.md),
[acceptance](../tests/acceptance.md), [M7 evidence](m7-behavior-policy.md) and
[model adaptation](model-configuration.md).

## Repository handoff

Repository and application name: **LumenDSH**. It succeeds the author's earlier
[Lumen](https://github.com/ydzat/lumen) in product direction. Silver Wolf / 银狼 is the replaceable default
character, using fan art based on Silver Wolf from *Honkai: Star Rail*, sourced from [awesome-codex-pet](https://github.com/legeling/awesome-codex-pet/tree/main/pets/silver-wolf--lingxiaotian).
Local directory `sliver-wolf` and Android package `local.sliverwolf.assistant` remain
for compatibility. Existing user-selected Agent names are not overwritten.
The application label and generic notification/settings titles use LumenDSH.
Version 0.7.47-branding is built and installed with the selected geometric L icon.
The selected [brand logo](branding/lumendsh-logo.png) is the geometric L concept.
The original selected PNG is used as the launcher resource; see [branding evidence](branding/README.md).

The user authorized initial source publication after the documentation and privacy
review. The reviewed snapshot is committed on top of the remote's existing MIT
LICENSE commit in a separate publication checkout. APKs, recordings and private
runtime data are excluded. The original local development history remains private.

Before a first push, follow [data/publication review](data-and-publication.md).
Historical local commits still contain development device identifiers. Choose a
sanitized history strategy with explicit authorization before uploading that graph;
do not silently force-push or join unrelated histories.

## Connection recovery follow-up

0.8.1 adds live layered diagnostics and the official Termux command interface
for fixed DSH startup/status actions. See [guide](connection-diagnostics.md) and
[C27-C31 device evidence](../tests/connection-evidence.md). The reported backend
was absent; retained logs do not establish the process termination cause.

Independent default and per-session model selection is available in 0.8.2.
The catalog and capabilities come from DSH; existing conversations retain their
identity when changing routes. See [model controls](model-configuration.md) and
[device evidence](../tests/model-selection-evidence.md).


## M10 autonomous development (0.10.x)

The active line is 0.10.x: `apps/android-web` (package `local.sliverwolf.web`,
label LumenDSH Web) runs the official DSH Web UI inside a WebView and keeps the
native surface for the character, voice and device work. Delivered so far:

- Embedded official UI with cookie handoff, reconnect, crash capture and a
  headless 1dp WebView that keeps the tide plugin alive for the home screen.
- Home shows 「梁文峰 / 梁文谷 · countdown」 from the tide plugin's own client
  state, with a policy-route fallback when the plugin state is unavailable.
- Peak/valley gating: a fresh peak report suppresses `developDue`; valley keeps
  the full develop loop.
- Device operation through adb over the USB tether (`phone_shot/tap/text/key/
  start/install/ui`), plus an accessibility + MediaProjection fallback.
- Community reuse instead of custom screens: official settings/automation pages,
  the official experimental voice bundle (local model unavailable on
  android-arm64; the client falls back to Android's SpeechRecognizer), and an
  evaluated `dsh-schedule-panel` (tool works, panel incompatible on 0.2.1-rc.3).
- Automatic continuation after restarts: `restart-host.sh` holds the watchdog,
  restarts the slot, then queues a `session/prompt` nudge so work resumes
  without a user message.

The old `local.sliverwolf.assistant` client has been removed; the device bridge
MCP now targets the new client on port 3084.
