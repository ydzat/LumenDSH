# M4.5 Local Scheduling Increment

Version: 0.4.11-m45-preview (18). F10 remains PARTIAL.

Reuse: ScheduledTaskStore and ScheduleAlarms from reference revision
 a5af7b105b7fdb006e3fc3be2a2179d83907c2cb (MIT, 2026 zhou-gu24).
The store retains atomic replacement, cancellation fences, occurrence claiming,
interval coalescing and calendar arithmetic. Local adaptations add existing-session
metadata, pause/resume, bounded task input and retained consumed one-shot records.

Execution owner: Android AlarmManager -> non-exported receiver -> short foreground
submission service. The service lists existing rc.7 sessions, then sends one prompt
with a stable occurrence request ID. It never creates a session, starts an engine,
reads model keys, invokes root/Shizuku, or retries unknown submission outcomes.
The original upstream executor owns a bundled engine and is therefore not copied.

User controls create a one-shot or interval task in the currently selected existing
session, pause/resume, inspect the last delivery result and cancel. Changing the
interactive session later does not retarget an existing schedule. Cancellation does
not retract an occurrence already claimed for submission. At most 64 active/retained
tasks, 8192 text characters, and 1-10080 minutes are accepted by the editor.

Lifetime policy: alarms may launch the submission service while the Activity is
hidden. Force-stop disables delivery until reopening. Boot/reopen restores future
alarms and consumes missed occurrences without catch-up prompts. A system-delivered
alarm over five minutes late is consumed without submitting. Background restrictions,
reboot behavior and Doze still require target-device validation. New permission:
RECEIVE_BOOT_COMPLETED; it restores only user-created future schedules.

Verification:
- Host compilation passed. The first host registry runtime check failed because
  Windows File.renameTo does not replace an existing destination like Android/POSIX;
  no Windows runtime pass is claimed and production semantics were not weakened.
- Actual Android app_process ScheduleChecks passed 12 assertions: selected-session
  persistence, early/paused suppression, durable one-shot claim, restart replay fence,
  stale alarm identity, explicit reschedule, cancellation, interval coalescing and
  recurring-session preservation. Controlled times are not alarm-delivery evidence.
- Android conversation (10 assertions), synthetic habitat projection and bounded
  notification archive checks passed in the consolidated run.
- Phone compilation, signing verification and in-place install passed.

First installed APK SHA-256:
d25c01cafd01731f2095bca3db7acc652b23015df4d0f44aee7588fe18fb7ce3.

Outstanding: full process-loss, offline/missing-session, boot/Doze, ambiguous
receipt-loss and recurring-delivery matrix. Acceptance is not task completion.

Real device evidence (Huawei Android 10, 2026-10-25): a one-minute one-shot task
was created in the existing controlled session and the Activity sent to HOME.
Android delivered it; the UI recorded accepted and the same transcript contained
the exact requested reply M45_SCHEDULE_DONE. The consumed one-shot became paused.

A subsequent refinement binds each outcome to the claimed trigger timestamp so a
previous recurring outcome cannot masquerade as the new occurrence after a crash.
The final compiled APK SHA-256 is
fb7da800d968617672c1fef6d2b272d063fc5a4a3bc334e3782ea22be95c81f1.

Final-version real checks:
- A second Android-triggered task returned M45_FINAL_SCHEDULE_DONE in the same
  transcript; exactly one prompt and one response marker were present.
- The timestamp-bound delivery record displayed accepted.
- Explicit one-minute re-enable followed by Pause persisted across force-stop and
  relaunch; the registry/UI still reported paused. This is pause persistence evidence,
  not proof that Android can execute a force-stopped application.

- After pause/restart the controlled final marker still appeared exactly twice
  (one prompt and one reply), with no duplicate submission observed.
- Both controlled schedules were explicitly cancelled; the list became empty.
  The temporary input instrumentation package was removed.

Local silent demonstration: artifacts/demos/20261025-002050-m45-schedule-result-0d265d55.mp4.
The 9-second 540x1168 clip was decoded and its controlled result/return-to-habitat
frame inspected. It demonstrates this increment, not full M4.5 acceptance.
