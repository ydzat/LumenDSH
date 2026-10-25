# Project status and handoff

Date: 2026-10-25. Current installation: Android 0.8.0, independent DSH
0.2.1-rc.3 and companion integration 0.2.1.

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
