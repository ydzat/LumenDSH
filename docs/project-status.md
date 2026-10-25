# Project status and handoff

Date: 2026-10-25. Baseline: Android 0.7.46-model-config, independent DSH
0.2.1-rc.3 and companion integration 0.2.1.

The user considers the foundation usable and has stopped feature development for
now. M0–M7 and model adaptation are delivered within their recorded scopes. This
is the current development checkpoint. No M8 is scheduled.

## Future directions, not scheduled milestones

- UI polish, settings organization, visual consistency and interaction refinement.
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
The source application label and generic notification/settings titles now use
LumenDSH; the installed 0.7.46 APK has not been rebuilt for this documentation handoff.
[Logo concepts](design/logo-concepts/README.md) await selection; installed resources
are unchanged.

The user authorized initial source publication after the documentation and privacy
review. The reviewed snapshot is committed on top of the remote's existing MIT
LICENSE commit in a separate publication checkout. APKs, recordings and private
runtime data are excluded. The original local development history remains private.

Before a first push, follow [data/publication review](data-and-publication.md).
Historical local commits still contain development device identifiers. Choose a
sanitized history strategy with explicit authorization before uploading that graph;
do not silently force-push or join unrelated histories.
