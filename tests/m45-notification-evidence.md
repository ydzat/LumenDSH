# M4.5 Completion Notification Increment

Version: 0.4.10-m45-preview (17). F08 remains partial pending all fault/lifecycle cases.

Source reuse: NotificationArchive.java copied/adapted from Android reference revision
 a5af7b105b7fdb006e3fc3be2a2179d83907c2cb (MIT, zhou-gu24). CompletionService adapts
turn-notify policy: 20-second minimum, 60-second cooldown, foreground suppression,
last ordinary reply excerpt. APK includes full attribution/license.

Implementation: optional foreground service subscribes read-only to the selected
existing rc.7 session. It does not install backend hooks, read credentials, send tasks
or create an agent/session. The menu controls enable/disable and private history.
Notification tap/history actions select the recorded existing conversation. Initial
historical completions are suppressed, and seen terminal sequence is committed before
posting. A crash between reservation and posting may lose a notice; no exactly-once
claim. Process termination and device background restrictions can interrupt delivery.

Verified so far:
- Host compilation, phone build and APK signature verification passed.
- In-place install/cold launch passed.
- A controlled real DSH task ran bash sleep 25 while the app stayed in the
  background, then returned M45_NOTICE_CHECK. The listener reported delivery and
  history contained exactly that final excerpt. History opened the original session.
- Listener was disabled after this test and the temporary input test APK removed.
- Two earlier attempts returned to the app during the test; they produced no history.
  Their suppression cause was not instrumented, so they are not policy evidence.
- Android archive test saved 102 controlled temporary records, retained 100 and
  verified original-session references; conversation/state checks also passed.

APK SHA-256: 92e495924502e90c9d8b1cec5f8061e46f1ca69f0e1d460d8657f83855d67007.

Outstanding: denied/revoked notification permission, cooldown/foreground boundary
matrix, process-loss/reconnect fault injection and system notification tap route.
No service permission, archive test or successful build counts as those checks.

The consolidated share-intake build retains this implementation and adds restoration
of an enabled observer when the Activity resumes, plus status-write coalescing.
Its SHA-256 is 8d6210ac7f3d13352257b18686f9adf09133634b681f91626464061a6dac3e83.
Reopening/restoration lifecycle paths have not yet received the full F08 matrix.
