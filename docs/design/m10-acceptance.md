# M10 acceptance record (2026-10-27)

Status: foundation batch verified on device; one caveat (F5 end-to-end autonomous
run is interrupted by queued continuation messages — see §Caveat).

## What "done" means for M10

The agent can wake on its own, operate the phone, write and install its own
console plugin, build/install/restart itself, read documentation, and do all of
it visibly, pausably and reversibly. Voice-first interaction and the autonomous
companion are the post-M10 destination, not this milestone.

## Evidence

| Item | Evidence | Result |
| --- | --- | --- |
| F1 plugin scaffold | `integrations/webui/{composer,shell}`; `tests/webui-plugins.test.mjs` (2 pass) | packages load, bundle patches resolve |
| F2 composer injection | host routes `/sliver-wolf/companion/composer` + `/pending`; device screenshot `~/tmp/compose1.png`; share text visible in the official composer | pass |
| M1 native transcript retired | commit `1cf1922`; home screenshot `~/tmp/m1home2.png` (no message excerpt) | pass |
| M2 console controls as plugin | commit `c427797`; screenshot `~/tmp/shell2.png`; `LumenShell.home()` returned to habitat | pass |
| M3 mic convergence | `dsh-voice-input` removed (local model unavailable on android-arm64); shell mic uses system recognition → composer | partial (voice output unchanged) |
| M5 drawer trimmed | commit `d788268`; screenshot `~/tmp/drawer3.png` | pass (entries); native code deletion still pending |
| M7 single autonomy control | commit `1a21d0f`; settings page labels master switch + explicit state | pass |
| F4 bounded research | `policy.mjs` admits `read_image/web_search/web_fetch`; `tests/companion-policy.test.mjs` 14 pass | pass |
| F6 reboot recovery | `tooling/restore-adb.sh` hooked into `~/.termux/boot/lumendsh-stack.sh` | script present; next reboot will exercise it |
| F7 status source | commit `141308a`; screenshot `~/tmp/status1.png` shows 「自主 运行中 · 谷段 · 最近：自主开发 02:22」 | pass |
| F8 activity line | same line + CompanionSettings 「最近活动」 page | pass (latest item) |
| F10 capsule collision | commit `683db0b`; screenshot `~/tmp/capsule2.png` (header readable) | pass |
| F5 self-upgrade path | `tooling/self-restart.sh` reachable via `bash tooling/`; build + `phone_install` + delayed restart each verified separately | component pass |
| Tests | companion-policy 14, self-iteration 8, situation 3, self-notes 3, develop-situation 2, webui-plugins 2 | 32 pass, 0 fail |
| Autonomy policy | `paused=false`, activities consolidate/reflect/develop, budget 24/48/10/600s | live |

## Caveat

Manual verification runs (`POST /sliver-wolf/companion/run`) reserve a budget and
deliver the activity prompt, but the session queue still contained continuation
messages from earlier restarts. Those arrive seconds later and the policy counts
them as human input, ending the run as `yielded-to-user` with 0 steps. `nudge.mjs`
now skips when a run is active, so once the queue drains a run can finish.
Caveat is in the environment, not in the run path.

## Remaining after M10

- Voice-first loop (wake → act → speak → standby; barge-in).
- Idle autonomy catalogue (`browse`, `explore-phone`, `chat`) and its visibility.
- Delete the remaining dead native code (transcript/schedules/memory/model pages).
- Control console polish and the post-M10 plugin growth.
