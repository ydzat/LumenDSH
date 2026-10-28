# M10 acceptance record (2026-10-27)

Status: **complete**, 2026-10-28. Foundation verified on device; one completed
autonomous run is recorded, and the earlier interruption caveat stays documented.

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
| M2 no client controls on the console | commit `c427797` then `fix(webui)` removing the floating buttons; screenshot `~/tmp/nobuttons.png`; back gesture verified console→habitat | pass (final design: no overlay; back gesture returns) |
| M3 mic convergence | `dsh-voice-input` removed (local model unavailable on android-arm64); shell mic uses system recognition → composer | partial (voice output unchanged) |
| M5 drawer trimmed | commit `d788268`; screenshot `~/tmp/drawer3.png` | pass (entries); native code deletion still pending |
| M7 single autonomy control | commit `1a21d0f`; settings page labels master switch + explicit state | pass |
| F4 bounded research | `policy.mjs` admits `read_image/web_search/web_fetch`; `tests/companion-policy.test.mjs` 14 pass | pass |
| F6 reboot recovery | `tooling/restore-adb.sh` hooked into `~/.termux/boot/lumendsh-stack.sh` | script present; next reboot will exercise it |
| F7 status source | commit `141308a`; screenshot `~/tmp/status1.png` shows 「自主 运行中 · 谷段 · 最近：自主开发 02:22」 | pass |
| F8 activity line | same line + CompanionSettings 「最近活动」 page | pass (latest item) |
| F10 capsule collision | commit `683db0b`; screenshot `~/tmp/capsule2.png` (header readable) | pass |
| F5 autonomous run | develop run at 23:47 completed with 12 steps / 12 tools (visible in the habitat timeline; the slots-alignment commit `91e245a` came from it) | pass (one complete run) |
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


## Follow-up batch (2026-10-27 late)

| Item | Evidence |
| --- | --- |
| Single period source | commit `f18c97a`; only the tide plugin's own report drives the habitat |
| Continuous conversation (optional) | commit `77384af`; 0.10.39 toggle verified |
| One-tap console on the habitat | commit `4a18f01`; 0.10.40 icon opens the console, back returns |
| Voice button state colours | commit `7eec8f1`; 0.10.41 |
| Transient voice feedback line | commit `413413e`; 0.10.42, screenshot `~/tmp/voicenote.png` |
| Period watcher follows lifecycle | 0.10.43; stops on pause, restarts on resume |
| Stale pause note removed | commits `d7ab164`, `4686456`; 0.10.44–45 |
| Autonomous action audit | host writes `behavior/activity-audit.jsonl` for web_*/phone_* tools during runs |

## Structure cleanup (2026-10-27, after the user's "no redundant parallel implementations" rule)

Old-app leftovers removed from the client:

| Removed | Evidence |
| --- | --- |
| Native conversation page (transcript, draft, composer, status bar, back handling) | commit `88c1f00`; `MainActivity` 1334 → 1228 lines |
| Dead memory/model/session pages and `styledTranscript` | commits `44f65fa`, `92ec988`; `MainActivity` → 1127 lines |
| Native schedule subsystem (`ScheduledTaskStore`, `ScheduleAlarms`, receiver, delivery service, UI, boot permission) | commit `999afe6`; −446 lines |
| Native share inbox (`ShareInbox`, review dialog, `assistant_receive_share` tool); text shares go to the composer, files are copied to Download and announced there | commit `3646f38`; −286 lines |
| Floating client buttons on the official page; back gesture returns to the habitat | commits `c188a2b`, `90e35fa` |

Remaining duplicate candidates are intentional:

- ADB control vs accessibility/MediaProjection: primary vs fallback for the same goal, documented.
- App device bridge vs ADB: the bridge exposes app-only capabilities (speech, releases), ADB covers device-wide actions.
- Native completion notifications vs tone cues: different audiences (Android notification shade vs audible turn cue).

The native conversation, schedules and share intake no longer have any parallel implementation.


## Browse activity (2026-10-28)

`browse` is implemented end to end: schema (up to four activities), read-only
tool guard (web reads, repository reads, `read_image`, self-notes; no commands or
repository writes), a dedicated read-only prompt, a client toggle, and the manual
`/run {activity:"browse"}` route. A verification run reserved its budget and
delivered the prompt (`ffc4c10d…`), but a queued continuation message arrived
immediately, so the run settled as `yielded-to-user` with 0 steps before using a
tool. The research itself then happened in the follow-up turn and produced
self-note `06398a89` (official slot API: `conversation.input.activity` supplies
`inputActions`; prefer list ids over replacing shipped cells).

The action audit (`behavior/activity-audit.jsonl`) only records tool calls while
a run is active; because the run had already settled, this attempt produced no
audit lines. That is expected, not a bug in the browse guard.


## Wake word verified (2026-10-28)

The user confirmed a real on-device wake. The device bridge status afterwards
reports `wake.hits=1`, `recognized=1`, `ownerRejected=0`,
`ownerProfile=long_readings/logistic`, `learnedSamples=1` — the detector fired,
passed the owner check and handed the utterance to recognition. Background
keyword wake remains opt-in; the page pauses it while the UI is visible.

Continuous conversation was then tested successfully on the device: after a
spoken reply the habitat reopened the microphone and the next sentence was
recognized without touching the screen. Together with wake word, spoken reply
and mic-tap barge-in, the voice loop (wake → act → speak → listen) now works on
the habitat; the console stays text-first by design.

## Version bump defect (fixed)

Manifest version edits after 0.10.27 silently did nothing because each bump
replaced a previous string that no longer existed, so every build kept
`versionCode=83 / 0.10.27-dev` while the code changed. Fixed by setting
`0.10.47 / 103` explicitly; future bumps read the current value first.

## Post-M10 infrastructure (2026-10-28 afternoon)

Beyond the M10 foundation, these pieces were built and verified on the device:

- **observe→act→verify** (`integrations/phone/ui-loop.mjs`): UIAutomator parsing,
  stable signatures, stale-target revalidation, post-action stability, stuck
  detection, `safeHome`/`waitFor`/`withRecovery`. Unit tests 9/9; live Bluetooth
  toggle false→true→false.
- **Declarative tasks** (`integrations/phone/task-runner.mjs`,
  `tooling/phone-task.mjs`): start/tap/type/key/waitFor/expect/home, fail-fast and
  safe-home. The `phone_task` host tool runs 1–12 validated steps; a real task
  (open WLAN settings → wait for the page text → verify → home) returned TASK OK,
  evidence `behavior/phone-task-2026-10-28.jsonl`.
- **Run trace**: the host writes `behavior/trace-<date>.jsonl` with
  start/tool/end events; `tooling/trace-report.mjs` summarises status, duration,
  steps and per-tool counts. Verified with a manual run (start + end lines).
- **Supervisor fix**: a surviving relay could keep the 3080 lock, so a restarted
  stack spawned a second relay, failed, and the watchdog re-started it every 30 s.
  `stack.mjs` now adopts a live relay and only exits when it or the port goes away.
- **Embodiment safety**: `docs/design/embodiment-safety.md` defines the body model,
  the CVSS-style five-axis rubric, the hard floors, and the self-review routine for
  connectivity changes; Bluetooth toggling is low risk while nothing is paired.

Commits: `66a06eb`, `a8d2bd2`, `2bef4c8`, `56b62fe`, `c1bbb2a`, `635d75f`,
`2eb9861`, `1b52d27`, `2ba0de2`.
